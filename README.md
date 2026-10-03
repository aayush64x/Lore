# Lore

A learning journal for code you build with AI.

Lore records what changed in your project during a coding session, explains each change in plain English, and keeps a list of the changes you have not understood yet, so you can come back and learn them.

## Why

When you build with an AI coding agent, code lands in your project faster than you can read it. It works, so you move on. A few weeks later you cannot explain your own project.

Lore is for that gap. You keep moving fast while you build, and Lore keeps a record you can study afterwards.

## How it works

1. Run `lore start` before you begin coding. Lore takes a snapshot of your project.
2. Code as usual. An agent can write the code, you can paste it, or you can type it. Lore does nothing while you work.
3. Run `lore stop` when you are done. Lore asks what you were trying to do, compares the project to the snapshot, and saves every change.
4. Run `lore ui` to open a local web page. Each change starts as "not understood yet". Open one, read the explanation, and mark it as understood.

## Status

Lore is a proof of concept, built for the Devpost "Build With AI: Basics" hackathon. It is not finished.

| Feature | Status |
|---|---|
| `lore start` (git snapshot, session saved) | Working |
| Diff parser (one change per hunk) | Working |
| `lore stop` (diff, save changes) | In progress |
| AI summary of a session | Planned |
| `lore ui` (session list, session detail, diffs) | In progress |
| Step-by-step explanation of a change | Planned |
| Mark as understood, progress tracking | Planned |
| Single JAR with bundled web app | Planned |

## Requirements

- Java 21
- Git, installed and on your PATH
- Maven, to build
- Node.js 20 or newer, to build the web app
- An AWS account with access to a Claude model on Amazon Bedrock, for the AI features

## Build and run

Backend:

```bash
cd backend
mvn package
```

Run a command from inside any git project:

```bash
java -jar /path/to/lore/backend/target/<lore-jar-name>.jar start
java -jar /path/to/lore/backend/target/<lore-jar-name>.jar stop
java -jar /path/to/lore/backend/target/<lore-jar-name>.jar ui
```

Web app, during development (the backend must be running with `lore ui`):

```bash
cd frontend
npm install
npm run dev
```

Vite forwards requests under `/api` to the backend.

Tests:

```bash
cd backend
mvn test
```

## Commands

| Command | What it does |
|---|---|
| `lore start` | Snapshots the project and starts a session. |
| `lore stop` | Asks what you were trying to do, records what changed, and ends the session. |
| `lore ui` | Starts the local web page and opens it in your browser. Press Ctrl+C to stop it. |
| `lore` | Shows help and whether a session is running. |

Lore must be run inside a git repository. If the folder is not one, Lore tells you to run `git init` and does nothing else.

## AI setup

Lore uses Claude through Amazon Bedrock. Credentials are read from the standard AWS location (`~/.aws/`), so they never live in this project.

1. Create an IAM user that is allowed to call `bedrock:InvokeModel` and `bedrock:InvokeModelWithResponseStream`.
2. Run `aws configure` with that user's access key.
3. In the Bedrock console, submit Anthropic's one-time use case form and note the model ID.
4. Put the region and model ID in `~/.lore/config.properties`.

If the AI call fails, the session and its changes are still saved. Only the summary is marked as failed, and it can be retried.

## How the snapshot works

Lore uses git plumbing commands so that it never touches your branches, your staging area, or your commit hooks:

1. `git add --all` into a temporary index file (`GIT_INDEX_FILE`), not your real one.
2. `git write-tree` turns that index into a tree object.
3. `git commit-tree` wraps the tree in a commit that is not on any branch.
4. `git update-ref refs/lore/sessions/<id>` keeps the commit from being garbage-collected.

`lore stop` takes a second snapshot the same way and runs `git diff-tree -r -p -M` between the two. This captures uncommitted and untracked files, works in a repository with no commits, and is unaffected by commits made during the session.

## What Lore stores and sends

- Session data is stored in a local H2 database at `~/.lore/`. Nothing is uploaded anywhere by Lore itself.
- Only the changed lines of a session are sent to the AI, never the whole project.
- Files that git ignores, such as `.env`, are never captured or sent.
- Binary files are listed but their content is not sent. Lockfiles and build output are skipped.
- The web page listens on `127.0.0.1` only.

A secret that is hard-coded in a tracked file you changed would be sent along with the rest of that change. Redacting likely secrets is planned.

## Project layout

```
backend/     Java 21, Spring Boot, Picocli, H2, Flyway
  cli/       start, stop, and ui commands
  git/       GitService (snapshots, diffs) and DiffParser
  session/   Session entity and session lifecycle
  change/    Change and Explanation entities
  ai/        AiService interface and Bedrock implementation
  api/       REST controllers under /api
frontend/    React, TypeScript, Vite
devpost/     Planning documents: scope.md, prd.md, spec.md
```

## Roadmap

- `lore add` for saving a single file, line range, or pasted snippet
- Plain-English search ("why did I change the login code?")
- Redacting likely secrets before anything is sent to the AI
- Working without git
- Accounts and sync across devices

## Built with

Java 21, Spring Boot, Picocli, Spring Data JPA, H2, Flyway, React, TypeScript, Vite, and Claude on Amazon Bedrock.

Planned and built with an AI coding agent using the Devpost Learn skill pack. The planning documents are in `devpost/`.
