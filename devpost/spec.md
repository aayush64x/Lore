---
doc: spec
status: approved
---

# Lore — Technical Spec

## How This Works, In Plain Language

Lore has three pieces: a **CLI**, a **backend server**, and a **web frontend** — all packaged into a single JAR.

The **CLI** is the entry point. It's built with Picocli — a Java library for building command-line tools. When you run `lore start` or `lore stop`, it boots Spring Boot in headless mode (no web server), talks directly to the database and git, does its job, and exits. When you run `lore ui`, it boots Spring Boot with the web server and opens your browser. The CLI and web server can run at the same time because the database supports concurrent access.

The **backend** is a Java application built with Spring Boot. It manages sessions, stores data in a local database, and calls AI. It exposes a REST API under `/api` that the frontend talks to. It also runs git plumbing commands to snapshot your project and compute diffs. The web server binds to `127.0.0.1` only — it's not exposed to the network.

The **frontend** is a React app built with TypeScript and Vite. It shows your sessions, changes, and walkthroughs. In development it runs separately with Vite proxying `/api` to the Spring backend. For release, the Maven build bundles the compiled frontend into the JAR so everything ships as one file plus a launcher script.

**Storage** is an H2 database file at `~/.lore/lore.mv.db`. H2 is an embedded database — no separate server process, just a file. It uses `AUTO_SERVER=TRUE` mode so the CLI and web server can access it concurrently from separate processes. Sessions, changes, explanations, and progress all persist there across restarts. The database is global — all projects share one file, and every query filters by the current project's absolute path.

**AI** calls go to AWS Bedrock (Claude) for two jobs: summarizing a session's changes when you run `lore stop`, and generating step-by-step explain walkthroughs when you click "Explain this" in the web UI. The AI only sees the diff for that session or change, never the whole project. Files git ignores (like `.env`) are never sent.

This shape keeps things simple — one repo, one JAR, one local database, no accounts, no cloud infrastructure — while keeping the API boundary clean enough that the frontend and backend could be separated later.

## The Core Journey Through the System

PRD ref: `prd.md > The Core Journey`.

1. **Install:** User downloads the fat JAR and puts the `lore` launcher script on their PATH. The JAR contains the CLI, backend, and bundled frontend. First time a command needs AI, Lore checks for AWS credentials via the default credential provider chain (`~/.aws/credentials` or environment variables).

2. **`lore start`:** Picocli parses the command. `GitService` checks that the current directory is a git repo (if not, prints an error and exits). `SessionService` checks H2 for an active session for the current project path (if one exists, prints "A session is already running since [time]" and exits). `GitService.snapshot()` creates a temporary git index file via `GIT_INDEX_FILE`, adds all non-ignored files with `git add`, runs `git write-tree` to create a tree object, wraps it in a commit via `git commit-tree` (with `GIT_AUTHOR_NAME`/`GIT_COMMITTER_NAME` set to `"Lore"` / `"lore@localhost"` so it works without a user git identity), and stores it under `refs/lore/sessions/<id>` with `git update-ref`. The temporary index file is deleted. `SessionService` saves a `Session` row to H2 with `status=ACTIVE`, the start ref, project path, and timestamp. CLI prints: *"Session started in [project] at [time]. Run lore stop when you're done."* No web server started.

3. **Code normally:** Lore is not running. The user codes with whatever tools they want — Claude Code writing directly into the repo, pasting from ChatGPT, typing by hand.

4. **`lore stop`:** Picocli parses the command. `SessionService` checks for an active session for the current project path (if none, prints "No session is running" and exits). CLI prompts: *"What were you trying to do?"* and reads one line from stdin. Empty input is allowed with a warning that the summary will be less useful. `GitService.snapshot()` repeats the same snapshot process to create a stop tree. `GitService.diff()` runs `git diff-tree -r -p -M <start-tree> <stop-tree>` to produce a unified diff patch with rename detection. `DiffParser` splits the raw patch into one `Change` record per hunk — extracting file paths, change types (including `RENAMED` from `-M` output), hunk header values (`old_start`, `old_count`, `new_start`, `new_count` from the `@@` line), and the raw diff text per hunk. Lockfiles, generated files, and build output are skipped. Binary files are recorded as changes but not sent to AI. If no changes remain, prints *"No changes since lore start. Nothing to save."* and discards the session (cleans up git refs). Otherwise, `AiService.summarize()` sends the diffs to AWS Bedrock — if the total diff exceeds a configurable size limit, it summarizes file by file, and skips very large individual files with a note ("File too large to summarize"). Returns structured JSON: a session summary (few sentences) and a one-line description per change. `SessionService` saves changes, descriptions, and summary to H2. Prints: *"Session saved: [reason]. [n] files changed, [n] changes to review. Run lore ui to study them."*

5. **`lore ui`:** Boots Spring Boot with the embedded web server bound to `127.0.0.1` only (`WebApplicationType.SERVLET`). Serves the bundled React frontend at `/` and the REST API at `/api`. The project path is passed to the frontend so all session queries filter by it. Opens the browser via `Desktop.browse()` and prints the URL as fallback. Terminal blocks until Ctrl+C.

6. **Browse sessions:** Frontend fetches `GET /api/sessions?project={path}` → renders the session list page. Project name at top, sessions newest first. Each row shows: reason, date/time, files changed, and progress ("3 of 7 understood"). Failed summary shows "Summary failed" with Retry. Empty state: "No sessions yet. Run `lore start` in your terminal to record your first one." Stale session banner if applicable.

7. **Study a change:** User clicks a session → frontend fetches `GET /api/sessions/{id}` → shows the session detail page: reason, date, plain-English summary at top, changes grouped by file below. Each change shows its line range, one-line AI description, and understood/not-understood status. Toggle filters to unreviewed only. Progress bar for the session. User clicks a change → sees the diff (green for added, red for removed). Clicks "Explain this" → frontend calls `POST /api/changes/{id}/explain` → backend checks for a cached explanation in H2. If none, `AiService.explain()` sends the single change's diff to Bedrock with each line numbered sequentially (1, 2, 3...) so the model can reference exact positions. Returns structured JSON: overview sentence, steps array (each with explanation text and `diffLineStart`/`diffLineEnd` referencing the numbered diff lines), and concepts array (each with name and one-sentence meaning). Explanation is saved to H2. Frontend renders the walkthrough alongside the diff: overview, then step-by-step with the referenced lines highlighted in the diff view. Fallback: if line highlighting isn't reliable in time, show line numbers as text ("Lines 3–8:"). Concepts list at the bottom. Cached explanations load instantly on return.

8. **Mark as understood:** Frontend calls `PUT /api/changes/{id}/understood` → toggles the boolean in H2 → UI updates the badge and progress. Undo supported (same endpoint toggles back).

9. **Come back later:** Sessions, summaries, explanations, and understood status persist in H2. `lore ui` from the same project shows everything. Pick up where you left off.

## Stack

| Component | Choice | Docs |
|---|---|---|
| Language | Java 21 | [OpenJDK 21](https://openjdk.org/projects/jdk/21/) |
| Backend framework | Spring Boot 3.x | [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/) |
| Build tool | Maven | [Maven Getting Started](https://maven.apache.org/guides/getting-started/) |
| CLI framework | Picocli | [Picocli User Guide](https://picocli.info/) |
| Frontend | React 18 + TypeScript | [React Docs](https://react.dev/) |
| Frontend build | Vite | [Vite Guide](https://vite.dev/guide/) |
| Database | H2 (file mode, `AUTO_SERVER=TRUE`) | [H2 Database](https://h2database.com/), [Auto Server Mode](https://h2database.com/html/features.html#auto_mixed_mode) |
| ORM | Spring Data JPA + Hibernate | [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/) |
| Migrations | Flyway | [Flyway Documentation](https://documentation.red-gate.com/flyway/) |
| AI provider | AWS Bedrock (Claude) | [Bedrock User Guide](https://docs.aws.amazon.com/bedrock/latest/userguide/) |
| AWS SDK | AWS SDK for Java v2 | [AWS SDK for Java v2 Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/) |
| Git integration | Git CLI via ProcessBuilder | [Git Internals - Plumbing](https://git-scm.com/book/en/v2/Git-Internals-Plumbing-and-Porcelain) |

Rationale: Learner-selected stack matching their experience and learning goals. Java + Spring Boot for backend depth; React + TypeScript for frontend practice; H2 for zero-infrastructure local storage with concurrent access; Flyway for schema discipline from day one; AWS Bedrock because the learner wants to set up AWS themselves.

## Where It Runs and How Someone Tries It

**Runtime:** Local machine. Requires Java 21, Node.js (development only), git, and an AWS account with Bedrock access to Claude.

**API key setup:** The learner sets up IAM, Bedrock model access, and `aws configure` themselves. Credentials live in `~/.aws/`, never in the project. Spec provides the steps; does not run commands for them.

**To run (development):**
- Backend: `cd backend && mvn spring-boot:run`
- Frontend: `cd frontend && npm run dev` (Vite proxies `/api` to Spring)

**To run (release):**
- `java -jar lore.jar start` / `java -jar lore.jar stop` / `java -jar lore.jar ui`
- Or via the `lore` launcher script on PATH.

**Demo recording:** Run `lore start`, make changes with Claude Code, run `lore stop`, type the reason. Run `lore ui`, open the browser, show the session, explain a change, mark it understood. Record the terminal and browser. Deployment is not required.

## Look and Feel

PRD ref: `prd.md > Look and Feel`.

**Direction:** Minimal and clean. Simplicity and readability over decoration.

**Implementation:**
- System font stack (`-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`). Monospace for code (`"JetBrains Mono", "Fira Code", "SF Mono", monospace`).
- Neutral palette: white/near-white backgrounds, dark text, one accent color for interactive elements. Diff colors: green for added, red for removed (standard conventions).
- Generous whitespace. Clear visual hierarchy through font weight and size, not heavy borders or color fills.
- No dark mode in the PoC — keep styling simple. Can be added later with CSS custom properties.
- CLI output: plain text, no color codes in the PoC. Terse — one or two lines per command.

## Components

### CLI Layer
Implements `prd.md > Session Recording`, `prd.md > States and Boundaries`.

Picocli is the application entry point. Four commands: `StartCommand`, `StopCommand`, `UiCommand`, and the root `LoreCli` (help/status).

- **StartCommand:** Boots Spring headless (`WebApplicationType.NONE`). Calls `SessionService.start()` which validates the git repo, checks for an active session on this project path, calls `GitService.snapshot()`, and persists the session to H2. Prints confirmation and exits.
- **StopCommand:** Boots Spring headless. Calls `SessionService.stop()` which finds the active session for this project path, prompts for a reason, calls `GitService.snapshot()` + `GitService.diff()`, runs `DiffParser`, calls `AiService.summarize()`, and persists everything. Prints the summary line and exits.
- **UiCommand:** Boots Spring with the web server bound to `127.0.0.1` (`WebApplicationType.SERVLET`). Serves the frontend and API. Opens the browser. Blocks until Ctrl+C.
- **LoreCli (root):** Shows help. If a session is active for the current project, shows its start time. If a session is stale, warns.

### Git Service
Implements `prd.md > Session Recording`.

Wraps git plumbing commands via `ProcessBuilder`. Core operations:

- **Snapshot:** Creates a temporary git index file by setting `GIT_INDEX_FILE` to a temp path. Runs `git add` against this index to stage all non-ignored files. Runs `git write-tree` to create a tree object from the temporary index. Runs `git commit-tree <tree>` to wrap it in a commit object (no parent needed) — sets `GIT_AUTHOR_NAME`/`GIT_AUTHOR_EMAIL` and `GIT_COMMITTER_NAME`/`GIT_COMMITTER_EMAIL` to `"Lore"` / `"lore@localhost"` in the ProcessBuilder environment so the command succeeds even if the user has no git identity configured. Runs `git update-ref refs/lore/sessions/<id> <commit>` to store it so it isn't garbage-collected. Deletes the temporary index file. Does not touch the working tree, the real staging area, or branch history. Does not trigger hooks. Works in repos with zero commits.
- **Diff:** Runs `git diff-tree -r -p -M <start-commit> <stop-commit>` to produce a unified diff patch with rename detection. Returns the raw patch output for `DiffParser` to process.
- **Cleanup:** Runs `git update-ref -d refs/lore/sessions/<id>` to remove refs for discarded sessions.
- **Ignored files:** Respects `.gitignore`. Files git ignores (e.g. `.env`) are never captured or sent to AI.

References:
- [git write-tree](https://git-scm.com/docs/git-write-tree) — creates a tree object from the index
- [git commit-tree](https://git-scm.com/docs/git-commit-tree) — creates a commit object from a tree
- [git update-ref](https://git-scm.com/docs/git-update-ref) — stores/deletes refs
- [git diff-tree](https://git-scm.com/docs/git-diff-tree) — diffs two tree objects; `-p` for patch, `-M` for rename detection
- [GIT_INDEX_FILE](https://git-scm.com/docs/git#Documentation/git.txt-codeGITINDEXFILEcode) — overrides the index file path
- How to check: run `lore start`, change a file, run `lore stop`, verify the diff output matches `git diff` for those changes. Check that `refs/lore/sessions/<id>` exists after start and persists after stop (completed sessions keep their refs). Refs are only deleted for discarded sessions (e.g. no changes).

### Diff Parser
Implements `prd.md > Session Recording`.

Parses the unified diff patch output from `git diff-tree -r -p -M` and splits it into one `Change` record per hunk. Responsibilities:

- Reads `diff --git a/... b/...` headers to extract file paths.
- Detects change type: `ADDED` (new file), `MODIFIED`, `DELETED`, `RENAMED` (from `-M` rename headers with `rename from` / `rename to`).
- For renames, captures both the old and new file paths.
- Parses each `@@ -old_start,old_count +new_start,new_count @@` hunk header to extract the four values.
- Extracts the raw diff text for each hunk (the `+`, `-`, and context lines between `@@` headers).
- Multiple hunks in the same file produce multiple `Change` records.
- Skips lockfiles and generated files (e.g. `package-lock.json`, `yarn.lock`, `pom.xml.lock`, files in `target/`, `build/`, `dist/`, `node_modules/`).
- Flags binary files (detected by `Binary files ... differ` in the diff output) — they're recorded as changes but their content is not stored and they're not sent to AI.
- Assigns `display_order` for stable ordering in the UI.

References:
- The unified diff format: `@@` hunk header syntax, `a/` and `b/` path prefixes, `+`/`-`/space line prefixes
- How `git diff-tree -M` outputs rename headers (`rename from`, `rename to`, `similarity index`)
- How to detect binary files in diff output
- How to check: create a test repo, make various changes (add, modify, delete, rename a file, change a binary), run the parser on the raw `git diff-tree` output, verify each hunk becomes a separate `Change` with correct fields.

### Session Service
Implements `prd.md > Session Recording`, `prd.md > AI Summary`, `prd.md > States and Boundaries`.

Orchestrates the session lifecycle:
- `start()`: Validates git repo, checks no active session exists for the current project path, calls `GitService.snapshot()`, persists session with `status=ACTIVE`.
- `stop()`: Finds the active session for the current project path, reads reason from stdin, calls `GitService.snapshot()` for stop tree, calls `GitService.diff()`, runs `DiffParser` to split into hunks, calls `AiService.summarize()`, persists changes and summary, marks session `COMPLETE`.
- `getStatus()`: Returns active session info for the current project path, including stale warning if the session has been open a long time.
- Handles edge cases: already-running session, no active session, no changes (discards session + cleans up refs), not a git repo.
- Stale session detection: checks session age on any command and warns if old.

### AI Service
Implements `prd.md > AI Summary`, `prd.md > Explain Walkthrough`.

Interface `AiService` with `BedrockAiService` implementation. Two operations:

- **Summarize session (`summarize()`):** Takes the list of changes (diff text per hunk). Returns structured JSON: a session summary (few sentences) and a one-line description per change. For large sessions (total diff exceeds a configurable size limit), summarizes file by file instead of all at once. Very large individual files are skipped with a note ("File too large to summarize"). Binary files are listed in the summary but their content is not sent. Prompt instructs the model to return structured JSON.
- **Explain change (`explain()`):** Takes a single change's diff text. Before sending to AI, numbers each line of the hunk sequentially (1, 2, 3...) so the model can reference exact positions. Prompt instructs the model to return structured JSON: overview sentence, steps array (each with explanation text, `diffLineStart`, and `diffLineEnd` referencing the numbered diff lines), and concepts array (each with name and one-sentence meaning). The frontend uses `diffLineStart`/`diffLineEnd` to highlight the corresponding lines in the diff view.

Uses AWS SDK for Java v2 `BedrockRuntimeClient` to invoke Claude via the Converse API. Credentials from `~/.aws/credentials` via the default credential provider chain.

The interface allows swapping to direct Anthropic API calls later.

References:
- [Bedrock Converse API](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-call.html) — how to invoke Claude via the SDK
- [AWS SDK BedrockRuntimeClient](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeClient.html) — the Java client
- Prompt design: how to instruct Claude to return structured JSON with line number references
- Tool use for structured output: using the Converse API's tool-use feature to get reliable JSON schemas
- How to check: run `lore stop` on a session with known changes, verify the summary describes what actually changed. Click "Explain this" in the UI, verify the steps reference the correct lines and the concepts make sense.

### REST API
Implements all web UI behaviors in `prd.md > Screens and Layout`.

Spring `@RestController` classes under `/api`. The project path is passed as a query parameter; all session queries filter by it.

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/sessions?project={path}` | GET | List sessions for this project (newest first) with progress |
| `/api/sessions/{id}` | GET | Session detail with summary and changes |
| `/api/changes/{id}/explain` | POST | Generate or retrieve cached explanation |
| `/api/changes/{id}/understood` | PUT | Toggle understood status |
| `/api/sessions/{id}/retry-summary` | POST | Retry failed summary |
| `/api/changes/{id}/retry-explain` | POST | Retry failed explanation |
| `/api/status?project={path}` | GET | Active session info, stale session warnings |

### Frontend — Session List Page
Implements `prd.md > Screens and Layout` (session list), `prd.md > Progress Tracking`.

Project name at top. Sessions newest first. Each row: reason, date/time, files changed, progress ("3 of 7 understood"). Failed summary shows "Summary failed" with Retry button. Empty state: "No sessions yet. Run `lore start` in your terminal to record your first one." Active/stale session banner if applicable.

### Frontend — Session Detail Page
Implements `prd.md > Screens and Layout` (session detail), `prd.md > Progress Tracking`.

Header: reason, date, plain-English summary. Changes grouped by file, each showing hunk line range, one-line description, and status badge (not understood / understood). Toggle to filter unreviewed only. Progress bar for the session.

### Frontend — Diff View and Explain Walkthrough
Implements `prd.md > Explain Walkthrough`, `prd.md > Progress Tracking`.

Diff rendered with green/red line coloring for added/removed lines. "Explain this" button triggers `POST /api/changes/{id}/explain`. Walkthrough renders alongside the diff:
1. One-sentence overview.
2. Step-by-step, where each step references `diffLineStart`/`diffLineEnd` — the frontend highlights those lines in the diff view. Fallback: if line highlighting isn't reliable in time, show the line numbers as text (e.g. "Lines 3–8:").
3. "Concepts in this change" list with one-sentence meanings.

"Mark as understood" button at the bottom. Undo supported. Cached explanations load instantly.

### Configuration
Implements `prd.md > The Core Journey` (install, API key setup).

- Lore data directory: `~/.lore/` — contains the H2 database file and config.
- Config file: `~/.lore/config.properties` — stores AWS region, Bedrock model ID, and any Lore-specific settings (e.g. large-session size limit). API credentials use the standard AWS credential chain (`~/.aws/`).
- H2 database: `~/.lore/lore.mv.db` — single file, `AUTO_SERVER=TRUE` for concurrent CLI + web server access.
- Per-project filtering: the database is global, so all queries (active session check, session list, web UI) filter by the current project's absolute path.

## Data Model

### Sessions Table

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| project_path | VARCHAR | Absolute path to the project root |
| reason | VARCHAR (nullable) | One-line reason from `lore stop` |
| summary | TEXT (nullable) | AI-generated session summary |
| summary_status | ENUM | `PENDING`, `COMPLETE`, `FAILED` |
| start_ref | VARCHAR | Git ref for the start snapshot |
| stop_ref | VARCHAR (nullable) | Git ref for the stop snapshot |
| status | ENUM | `ACTIVE`, `COMPLETE`, `DISCARDED` |
| started_at | TIMESTAMP | |
| stopped_at | TIMESTAMP (nullable) | |

### Changes Table

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| session_id | UUID (FK → sessions) | |
| file_path | VARCHAR | Relative path within the project |
| change_type | ENUM | `ADDED`, `MODIFIED`, `DELETED`, `RENAMED` |
| old_file_path | VARCHAR (nullable) | Original path for renames |
| old_start | INT | From `@@` header: start line in the old file |
| old_count | INT | From `@@` header: number of lines in old file |
| new_start | INT | From `@@` header: start line in the new file |
| new_count | INT | From `@@` header: number of lines in new file |
| diff_content | TEXT | Raw diff text for this hunk |
| description | VARCHAR (nullable) | AI-generated one-line description |
| is_binary | BOOLEAN | Default `false`; true for binary files |
| understood | BOOLEAN | Default `false` |
| display_order | INT | Stable order within the session |

### Explanations Table

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| change_id | UUID (FK → changes, unique) | One explanation per change |
| overview | TEXT | One-sentence overview |
| steps | JSON | Array of `{text, diffLineStart, diffLineEnd}` — references numbered diff lines |
| concepts | JSON | Array of `{name, meaning}` |
| status | ENUM | `PENDING`, `COMPLETE`, `FAILED` |
| created_at | TIMESTAMP | |

**How diff line numbering works for explanations:** When sending a hunk to AI for explanation, each line of the diff text is numbered sequentially starting at 1. The AI returns steps that reference these numbered positions via `diffLineStart` and `diffLineEnd`. The frontend uses these numbers to highlight the corresponding lines in the diff view, which means highlighting works by position in the displayed diff, not by source file line numbers. This is more reliable because the diff view and the AI see the same numbering.

Flyway migrations manage all schema changes from the start. All data persists in `~/.lore/lore.mv.db`.

## File Structure

```
lore/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/lore/
│       │   ├── LoreApplication.java              # Spring Boot + Picocli entry
│       │   ├── cli/
│       │   │   ├── LoreCli.java                  # Root command (help/status)
│       │   │   ├── StartCommand.java             # lore start
│       │   │   ├── StopCommand.java              # lore stop
│       │   │   └── UiCommand.java                # lore ui (starts web server)
│       │   ├── git/
│       │   │   ├── GitService.java               # Git plumbing via ProcessBuilder
│       │   │   └── DiffParser.java               # Splits unified diff into per-hunk changes
│       │   ├── session/
│       │   │   ├── Session.java                  # JPA entity
│       │   │   ├── SessionRepository.java        # Spring Data repository
│       │   │   └── SessionService.java           # Session lifecycle orchestration
│       │   ├── change/
│       │   │   ├── Change.java                   # JPA entity
│       │   │   ├── ChangeRepository.java         # Spring Data repository
│       │   │   ├── Explanation.java              # JPA entity
│       │   │   └── ExplanationRepository.java    # Spring Data repository
│       │   ├── ai/
│       │   │   ├── AiService.java                # Interface
│       │   │   ├── BedrockAiService.java         # AWS Bedrock implementation
│       │   │   └── dto/                          # AI request/response shapes
│       │   │       ├── SummaryResponse.java
│       │   │       └── ExplanationResponse.java
│       │   └── api/
│       │       ├── SessionController.java        # /api/sessions, /api/status
│       │       └── ChangeController.java         # /api/changes
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/
│       │       └── V1__initial_schema.sql
│       └── test/java/com/lore/
│           └── git/
│               ├── GitServiceTest.java           # Snapshot logic tests
│               └── DiffParserTest.java           # Diff parsing tests
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── api/
│       │   └── client.ts                         # REST API client
│       ├── pages/
│       │   ├── SessionList.tsx
│       │   └── SessionDetail.tsx
│       └── components/
│           ├── DiffView.tsx
│           ├── ExplainWalkthrough.tsx
│           ├── ProgressBar.tsx
│           └── StatusBadge.tsx
├── scripts/
│   └── lore                                      # Launcher shell script
├── devpost/                                      # Learning workspace
└── README.md
```

## External Services and Dependencies

### AWS Bedrock (Claude)

- **API:** Bedrock Converse API via AWS SDK for Java v2.
- **Model:** Claude (specific model ID configured in `~/.lore/config.properties`).
- **Auth:** Default AWS credential provider chain (`~/.aws/credentials` or environment variables).
- **Calls:**
  - **Summarize:** Send session diffs as user message. System prompt instructs structured JSON response with a session summary (few sentences) and a one-line description per change. For large sessions, sends diffs file by file. Very large files skipped with a note. Binary files listed but content not sent.
  - **Explain:** Send a single change's diff with each line numbered sequentially. System prompt instructs structured JSON response with overview, steps (each with `diffLineStart`/`diffLineEnd`), and concepts.
- **Rate limits:** Bedrock has per-model request limits; Lore makes at most one summary call per session stop (or one per file for large sessions) and one explain call per change click.
- **Cost:** Pay-per-use via AWS account. Claude on Bedrock charges per input/output token.
- **Docs:** [Bedrock Converse API](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-call.html), [AWS SDK BedrockRuntimeClient](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeClient.html).

**Learner sets up AWS themselves:** IAM user/role, Bedrock model access request, region selection, `aws configure`. Spec provides the steps; does not run commands for them.

### H2 Database

- **Mode:** Embedded file mode with `AUTO_SERVER=TRUE` for concurrent access from CLI and web server processes.
- **File:** `~/.lore/lore.mv.db`.
- **Connection:** JDBC via Spring Data JPA. URL: `jdbc:h2:file:~/.lore/lore;AUTO_SERVER=TRUE`.
- **Docs:** [H2 Features](https://h2database.com/html/features.html), [Auto Server Mode](https://h2database.com/html/features.html#auto_mixed_mode).

### Git

- **Invoked via:** `ProcessBuilder` calling the system `git` binary.
- **Requires:** Git installed and on PATH.
- **Plumbing commands used:** `git rev-parse` (check if repo), `git add` (stage to temp index), `git write-tree` (create tree from index), `git commit-tree` (wrap tree in commit), `git update-ref` (store/delete refs), `git diff-tree` (diff two trees; `-r` recursive, `-p` patch, `-M` rename detection).
- **Docs:** [Git Internals - Plumbing and Porcelain](https://git-scm.com/book/en/v2/Git-Internals-Plumbing-and-Porcelain).

## Tests

### Git Service Tests
Test the snapshot logic:
- Snapshot creates a tree object that includes all non-ignored files.
- Snapshot does not modify the working tree, staging area, or HEAD.
- Snapshot creates a ref under `refs/lore/sessions/`.
- Snapshot works in a repo with zero commits.
- Snapshot works in a repo with no git identity configured (no `user.name` / `user.email`) — `commit-tree` succeeds because GitService sets its own author/committer env vars.
- Cleanup deletes the ref.

Hints: create a temporary git repo in a temp directory for each test. For the no-identity test, create the repo without running `git config user.name` or `git config user.email`. Run git plumbing commands to inspect the created tree. Use JUnit 5 + `@TempDir`.

### Diff Parser Tests
Test the parser against real unified diff output:
- Single file modified with one hunk → one Change.
- Single file modified with multiple hunks → multiple Changes.
- Added, deleted, and renamed files → correct `change_type` and paths.
- Renamed file → `old_file_path` populated.
- `@@` header values parsed correctly into `old_start`, `old_count`, `new_start`, `new_count`.
- Binary file → flagged as `is_binary`, no diff content stored.
- Lockfile/generated file → skipped entirely.
- Display order assigned sequentially.

Hints: capture real `git diff-tree` output from test repos as string fixtures. Parse them and assert on the resulting Change records. No git repo needed for parser-only tests — feed it string input.

## Important Failure Modes

- **AI call fails (summary or explain)** → Session and changes are saved without the summary/explanation. The UI shows "Summary failed" or "Couldn't explain this change" with a Retry button. The rest of the app works normally. Implements `prd.md > States and Boundaries`.

- **Git not installed or not a repo** → `lore start` checks for git and a valid repo before doing anything. Prints a clear message and exits. Implements `prd.md > States and Boundaries`.

- **No changes during session** → `lore stop` detects an empty diff, prints "No changes since lore start. Nothing to save.", discards the session, and cleans up the git refs. Implements `prd.md > States and Boundaries`.

## What Was Simplified and Why

- **No dark mode** — Adding theme support doubles the CSS work. The PoC ships light-only; the styling uses custom properties so dark mode can be layered on later.
- **No color in CLI output** — Plain text for PoC. ANSI colors can be added later without architectural changes.
- **Single shared database** — All projects share one database file in `~/.lore/`, filtered by project path. A per-project database would add configuration complexity for no PoC benefit.
- **Line highlighting fallback** — If syncing walkthrough steps to diff lines proves unreliable, each step shows line numbers as text ("Lines 3–8:") instead of interactive highlighting. The learning value is the same.
- **No deployment** — Runs locally, recorded for demo video. No hosting setup needed.

## Decisions and Open Issues

### Learner Decisions

- **Java 21 + Spring Boot 3 + Maven** — Learner's established stack; matches their experience and goals.
- **React + TypeScript + Vite** — Learner's choice for the frontend.
- **H2 file mode with Flyway and `AUTO_SERVER=TRUE`** — Learner chose local file storage with migrations from day one for schema discipline. Auto server mode for concurrent CLI + web server access.
- **AWS Bedrock (Claude)** — Learner wants to set up AWS themselves; interface abstraction allows switching to direct Anthropic API later.
- **Git snapshot via temporary index + write-tree + commit-tree + update-ref** — Learner-designed approach: doesn't touch staging area, branch history, or hooks; captures untracked files; works with zero commits; stores under `refs/lore/sessions/<id>`.
- **Agent-driven core logic with learn-mode review** — Agent writes all implementations (git operations, diff parsing, AI calls, session orchestration, tests). After each slice, agent walks the learner through the code, maps it to the spec, and asks 2–3 comprehension questions before the slice is approved. Previously: learner was going to write core logic from method signatures + hints; changed to agent-writes on 2026-09-30 to hit the Oct 10 deadline.
- **Small slices on separate branches with PRs** — Practicing for a 24-hour hackathon workflow.
- **Future-proofing without building** — Data flows through `/api`, schema changes through migrations, nothing assumes frontend and backend share a process. No auth, accounts, or cloud now.

### Derived Implementation Details

- Picocli as entry point; Spring Boot runs with `WebApplicationType.NONE` for start/stop, `WebApplicationType.SERVLET` for ui.
- Web server binds to `127.0.0.1` only — not exposed to the network.
- AI responses use structured JSON (prompted via system message or tool-use); parsed into DTOs.
- Diff lines numbered sequentially when sent to AI for explain; steps reference `diffLineStart`/`diffLineEnd` for reliable highlighting.
- Vite proxies `/api` to Spring in development; Maven build bundles frontend into JAR for release.
- Launcher script wraps `java -jar lore.jar` with argument forwarding.
- All database queries filter by project path (global database, per-project views).

### Useful Unknown

**Structured AI responses:** Getting Claude to reliably return step-by-step walkthroughs as structured JSON (with accurate diff line number references) is a prompt engineering challenge the learner hasn't done before. This connects to their learning goal of understanding how to work with AI effectively. To investigate during the build: experiment with the Bedrock Converse API's tool-use feature for structured output, and test with real diffs to see how reliably the model maps steps to numbered diff lines.

### Later

- **Blank out likely secrets in changed lines before sending to AI** — Detect and redact patterns like API keys, tokens, and passwords in diff content before sending to Bedrock for summary or explanation. Not in the PoC, but important before broader use.

### Open Issues

None blocking. All consequential technical decisions are resolved.
