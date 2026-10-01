---
doc: checklist
status: approved
---

# Build Checklist

Build mode: learn (slices 1, 2a, 2b); may switch to fast after that

## Slices

- [ ] **1. Scaffold + `lore start`**
  Becomes usable: You can run `lore start` in a git project and it creates a snapshot ref under `refs/lore/sessions/<id>` and saves an ACTIVE session row to H2.
  Why now: Git plumbing (temporary index, write-tree, commit-tree, update-ref) is the riskiest technical piece and must be proven first. Scaffolding lives inside this slice so it's not a standalone step.
  PRD ref: `prd.md > The Core Journey` (steps 1–2), `prd.md > Session Recording`, `prd.md > States and Boundaries`
  Spec ref: `spec.md > CLI Layer` (StartCommand, LoreCli), `spec.md > Git Service` (snapshot, repo check), `spec.md > Session Service` (start), `spec.md > Data Model`, `spec.md > File Structure`, `spec.md > Stack`
  Build: Agent scaffolds the Maven project with Spring Boot 3, Java 21, Picocli, H2, Flyway, Spring Data JPA. Creates V1 Flyway migration (sessions, changes, explanations tables), JPA entities, repositories, application.yml with H2 AUTO_SERVER=TRUE, and the StartCommand + LoreCli shell. Learner writes GitService.snapshot() (temp index → write-tree → commit-tree → update-ref with Lore git identity env vars) and GitServiceTest.
  Verify (mechanical): In a test git repo, run `lore start`. Confirm `refs/lore/sessions/<id>` exists via `git show-ref`. Query H2 to confirm session row with status=ACTIVE. Run GitServiceTest — all tests pass.
  Learner check: Run `lore start` in a git project and confirm the "Session started" message prints. Run `git show-ref` and find the lore ref.
  Commit: `Add project scaffold and lore start command`

- [ ] **2a. DiffParser on its own**
  Becomes usable: DiffParser is a tested pure function — unified diff text in, Change records out — with no dependencies on git, CLI, or database.
  Why now: Isolating the parser makes it easy to test with string fixtures and catches format bugs before they're buried under the stop-session flow.
  PRD ref: `prd.md > Session Recording`
  Spec ref: `spec.md > Diff Parser`
  Build: Learner writes DiffParser (splits unified diff into per-hunk Change records: file paths, change types including RENAMED, @@ header values, raw diff text, binary detection, lockfile/generated file skipping, display_order) and DiffParserTest using captured `git diff-tree` output as string fixtures.
  Verify (mechanical): All DiffParserTest cases pass — single hunk, multiple hunks, add/delete/rename with old_file_path, @@ values parsed correctly, binary flagged, lockfiles skipped, display_order sequential.
  Learner check: Run the tests and confirm they all pass. Look at a test case for a rename and confirm old_file_path is populated.
  Commit: `Add diff parser with tests`

- [ ] **2b. `lore stop` without AI**
  Becomes usable: The full CLI recording pipeline works — start a session, change files, stop it, and the session with per-hunk changes is saved to H2. Empty sessions are discarded. Edge cases handled.
  Why now: Wires snapshot, diff, and parser into a real session lifecycle. Proves the full recording path before AI is added.
  PRD ref: `prd.md > The Core Journey` (steps 3–4), `prd.md > Session Recording`, `prd.md > States and Boundaries`
  Spec ref: `spec.md > CLI Layer` (StopCommand), `spec.md > Git Service` (diff, cleanup), `spec.md > Session Service` (stop), `spec.md > Diff Parser`
  Build: Implement StopCommand and SessionService.stop() — prompt for reason, call GitService.snapshot() for stop tree, call GitService.diff(), run DiffParser, save changes to H2, mark session COMPLETE. Handle empty sessions (discard + delete refs). Handle CLI edge cases: no session running, session already running on start, not a git repo.
  Verify (mechanical): Run `lore start`, create/modify/delete files, run `lore stop`, type a reason. Query H2 — session is COMPLETE with correct changes (file paths, change types, diff content). Run `lore stop` with no changes — session discarded, refs cleaned up. Run `lore stop` with no active session — prints error. Run `lore start` twice — prints "already running."
  Learner check: Run the full start → edit → stop flow and confirm the session-saved message shows the right file and change counts.
  Commit: `Add lore stop with session recording`

- [ ] **3. AI summary in `lore stop`**
  Becomes usable: When you stop a session, Bedrock generates a plain-English summary and per-change descriptions. If the AI call fails, the session is still saved with summary_status=FAILED.
  Why now: First AI integration — validates Bedrock connectivity, prompt design, and structured JSON parsing. Prerequisite: AWS credentials configured with Bedrock access.
  PRD ref: `prd.md > AI Summary`, `prd.md > States and Boundaries` (summary failure)
  Spec ref: `spec.md > AI Service` (summarize), `spec.md > External Services and Dependencies > AWS Bedrock`
  Build: Learner writes AiService interface, BedrockAiService.summarize() using the Converse API, and SummaryResponse DTO. Wire into SessionService.stop() — on success, save summary and per-change descriptions; on failure, save session with summary_status=FAILED. Add config for AWS region and model ID in application.yml / config.properties.
  Verify (mechanical): Run start → edit → stop with AWS credentials configured. Confirm the summary is stored in H2 and the per-change descriptions make sense. Disconnect AWS (e.g. bad region) and confirm the session saves with summary_status=FAILED.
  Learner check: Run `lore stop` and read the summary it prints. Does it accurately describe what you changed?
  Commit: `Add AI session summary via Bedrock`

- [ ] **4. `lore ui` read-only**
  Becomes usable: Run `lore ui`, browser opens, you see your sessions listed newest-first, click one to see its summary and changes with diffs. No AI in the UI yet.
  Why now: First time the learner sees their data visually. Built hackathon-style (agent writes most, learner steers and reviews, time-boxed to 3 hours) as a rehearsal for fast-paced building.
  PRD ref: `prd.md > The Core Journey` (steps 5–6), `prd.md > Screens and Layout`
  Spec ref: `spec.md > REST API`, `spec.md > CLI Layer` (UiCommand), `spec.md > Frontend — Session List Page`, `spec.md > Frontend — Session Detail Page`, `spec.md > Frontend — Diff View and Explain Walkthrough` (diff view only, no explain yet), `spec.md > Look and Feel`
  Build: Scaffold React + TypeScript + Vite frontend with proxy config. Implement REST API endpoints: GET /api/sessions, GET /api/sessions/{id}, GET /api/status. Implement UiCommand (starts web server, opens browser). Build session list page (project name, sessions newest-first, reason, date, file count) and session detail page (summary, changes grouped by file, diff view with green/red line coloring). Style per Look and Feel spec. Agent writes most of the code; learner steers and reviews.
  Verify (mechanical): Run `lore ui`, open the browser, see sessions listed. Click a session — summary and changes render. Diff view shows correct green/red coloring. Vite proxy works for /api calls.
  Learner check: Run `lore ui`, browse your sessions, click into one, and look at the diffs. Does it show what you expected?
  Commit: `Add web UI with session list and diff view`

- [ ] **5. Packaging**
  Becomes usable: The Maven build bundles the compiled React frontend into the fat JAR. A `lore` launcher script wraps `java -jar`. All three commands (start, stop, ui) work from the JAR.
  Why now: Proves the single-artifact delivery before adding more UI features. Catches build/classpath issues early.
  PRD ref: `prd.md > The Core Journey` (step 1 — install)
  Spec ref: `spec.md > How This Works, In Plain Language` (single JAR), `spec.md > Where It Runs and How Someone Tries It` (release), `spec.md > File Structure` (scripts/lore)
  Build: Configure the Maven build to run `npm run build` in the frontend directory and copy the output into `src/main/resources/static/`. Create the `scripts/lore` launcher script that forwards arguments to `java -jar`. Verify Spring Boot serves the bundled frontend at `/`.
  Verify (mechanical): Run `mvn package`. Run `java -jar target/lore-*.jar start` in a test repo, then `stop`, then `ui`. Confirm the browser loads the bundled frontend (not Vite dev server).
  Learner check: Run `./scripts/lore ui` from outside the project and confirm the web UI loads from the JAR.
  Commit: `Bundle frontend into fat JAR with launcher script`

- [ ] **6a. Explain walkthrough — text version**
  Becomes usable: Click "Explain this" on a change in the web UI and get a walkthrough: overview sentence, step-by-step with line numbers shown as text ("Lines 3–8:"), and a concepts list. Explanations are cached.
  Why now: The unique kernel in its simplest reliable form. Text line references work regardless of frontend highlighting complexity.
  PRD ref: `prd.md > Explain Walkthrough`
  Spec ref: `spec.md > AI Service` (explain), `spec.md > Frontend — Diff View and Explain Walkthrough`, `spec.md > REST API` (POST /api/changes/{id}/explain)
  Build: Implement AiService.explain() — number diff lines sequentially, send to Bedrock with structured output prompt, parse into ExplanationResponse (overview, steps with diffLineStart/diffLineEnd, concepts). Add POST /api/changes/{id}/explain endpoint. Build the ExplainWalkthrough frontend component — overview, steps showing line numbers as text, concepts list. Cache explanations in H2.
  Verify (mechanical): Click Explain on a change with a known diff. Verify the walkthrough has an overview, steps referencing correct line numbers, and relevant concepts. Reload — explanation loads instantly from cache.
  Learner check: Click Explain on a change you made, read the walkthrough, and say whether the steps and line references make sense.
  Commit: `Add explain walkthrough with text line references`

- [ ] **6b. Live line highlighting**
  Becomes usable: When reading an explain step, the referenced lines are highlighted in the diff view instead of just showing line numbers as text.
  Why now: Better study experience — your eyes go straight to the relevant code. If highlighting isn't reliable, 6a stays as the fallback and this slice is skipped.
  PRD ref: `prd.md > Explain Walkthrough` (highlighted lines)
  Spec ref: `spec.md > Frontend — Diff View and Explain Walkthrough` (line highlighting, fallback)
  Build: Update DiffView to accept highlighted line ranges. Update ExplainWalkthrough so hovering or focusing a step highlights its diffLineStart–diffLineEnd range in the diff. Fallback: if sync is unreliable, keep text line numbers from 6a.
  Verify (mechanical): Click Explain, hover/focus each step, confirm the correct diff lines highlight. Check edge cases: first line, last line, single-line step.
  Learner check: Click Explain on a change, read through the steps, and confirm the highlighting matches what each step is describing.
  Commit: `Add live line highlighting for explain steps`

- [ ] **7a. Mark understood, progress, and filter**
  Becomes usable: You can mark changes as understood (with undo), see progress on the session list and detail pages, and filter to unreviewed changes only.
  Why now: Completes the "I don't understand this yet" loop — the other half of the kernel.
  PRD ref: `prd.md > The Core Journey` (step 8), `prd.md > Progress Tracking`
  Spec ref: `spec.md > REST API` (PUT /api/changes/{id}/understood), `spec.md > Frontend — Session List Page` (progress), `spec.md > Frontend — Session Detail Page` (progress bar, filter)
  Build: Add PUT /api/changes/{id}/understood endpoint (toggles boolean). Add "Mark as understood" button with undo to the change detail view. Add progress display to session list rows ("3 of 7 understood"). Add progress bar to session detail page. Add unreviewed-only filter toggle.
  Verify (mechanical): Mark a change understood — badge updates, progress increments on both pages. Undo — reverts. Toggle unreviewed filter — understood changes hidden. Reload — state persists.
  Learner check: Mark a few changes understood, check the progress numbers, try the filter, and confirm everything updates correctly.
  Commit: `Add understood toggle, progress bars, and unreviewed filter`

- [ ] **7b. Retry states, stale warnings, and empty states**
  Becomes usable: Failed AI calls show Retry buttons that work. Long-running sessions show stale warnings. No sessions shows the empty state message.
  Why now: Polishes the edge cases that make the app feel complete rather than fragile.
  PRD ref: `prd.md > States and Boundaries`
  Spec ref: `spec.md > Important Failure Modes`, `spec.md > REST API` (retry endpoints), `spec.md > CLI Layer` (LoreCli root status)
  Build: Add Retry buttons for failed summaries (POST /api/sessions/{id}/retry-summary) and failed explanations (POST /api/changes/{id}/retry-explain). Add stale session detection — warn in CLI and web UI banner if a session has been open a long time. Add empty state: "No sessions yet. Run lore start in your terminal to record your first one." Update LoreCli root command to show active session info and stale warning.
  Verify (mechanical): Force-fail an AI call (bad credentials), confirm Retry button appears, fix credentials, click Retry, confirm it succeeds. Start a session and check for stale warning after threshold. Open UI with no sessions — empty state renders.
  Learner check: Open the web UI and check that empty state, stale warnings, and retry buttons all look right.
  Commit: `Add retry states, stale warnings, and empty states`

## Hands-on Checkpoints

- [ ] Early feedback after slice 2b — CLI recording pipeline complete; try the full start → edit → stop flow and give feedback before moving to AI and the web UI
- [ ] Rough demo video after slice 6a — first time the full study loop works (record, browse, explain); record a draft to test the demo flow before polish slices
- [ ] Final kick-the-tires after slice 7b — full app review, core journey end-to-end

## Final Review

- [ ] Final review complete — feedback resolved and learner confirms ready to ship

## Code Tour and App Map

- [ ] Learning activity complete — guided route, focused alternative, prior practice connected, or brief recap
- [ ] Optional edit and transfer reflection addressed — offered/declined/already covered/not applicable as appropriate
- [ ] `devpost/app-map.html` generated from finished code, checked, and shown, including a project-grounded practice to reuse

Activity and evidence: [what actually happened; real document/test/code references; unfinished work if interrupted]
Route and stops: [actual paths and symbols; guided stops completed, or reference-only route]
Edit outcome: [tried/kept/reverted/declined/not applicable; verification if changed]
Reflection: [offered/answered/declined/already covered — personal answer belongs only in the ignored profile]
Activity mode: [live app and editor, explicit static fallback, focused alternative, prior practice, or recap]

## Revisions
