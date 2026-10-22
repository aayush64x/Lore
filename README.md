# Lore

**Build with AI. Learn what you built.**

Lore is a learning journal for code that AI writes for you. It records what changed while you were coding, explains each change in plain English, checks the changes against what you asked for, and makes you explain them back before it counts them as understood.

It runs on your own computer and uses your own Anthropic API key.

## Why

AI coding tools write code faster than you can read it. The feature works, you move on, and a few weeks later you can't explain your own project.

Lore lets you keep building fast and study afterwards. It isn't a changelog. It's a study list built from your own code.

## How it works

```
lore start   →   code as usual (AI agent, paste, or type)   →   lore stop   →   lore ui
```

1. **`lore start`** takes a hidden snapshot of your git project. Your branches, commits and staging area are not touched.
2. **Code as usual.** Lore does nothing while you work.
3. **`lore stop`** asks what you were trying to do, saves every change since the snapshot, and has Claude write a summary.
4. **`lore ui`** opens a local web page where you study the changes, one by one.

## What you get

- **A summary of the session**, plus a one-line summary of every change.
- **A goal check.** Each change is marked as part of your goal, partly, or not part of it, and Lore lists the parts of your goal that no change did ("Not done yet: write a test for countWords").
- **Step-by-step explanations** written for your level (new to coding, know some, or developer) and your goal (understand my app, or learn to code). Each step lights up the lines it talks about.
- **"Lore found an issue"**: a red badge on a line where the AI-written code has a real edge case or bug, with the reason.
- **Teach-back.** Click **Test me** and explain the change in your own words. A small, cheap model grades it against the key points, ignores spelling, and gives hints that point you in the right direction without giving the answer. You get 3 tries; after 2 misses the key points are shown. Passing marks the change understood. You can retake a passed change as practice at any time, and you can always skip the test and mark a change yourself.
- **Progress** per session: which files are passed, self-marked, or still to learn.
- A GitHub-style diff with line numbers, a collapsible file list, and keyboard shortcuts (`[` `]` files, `←` `→` steps, `b` sidebar).

## Requirements

- **Java 21 or newer**: `java -version` should print 21 or higher.
- **git**
- **An Anthropic API key**: get one at [console.anthropic.com](https://console.anthropic.com/settings/keys). You pay Anthropic directly for what Lore uses (see [Costs](#costs)).
- macOS, Linux, or Windows with Git Bash or WSL (the installer is a bash script). Lore has been tested most on macOS.

## Install

### From a release (recommended)

1. Go to the [latest release](https://github.com/aayush64x/Lore/releases/latest) and download **`lore.jar`** and **`install.sh`** into the same folder.
2. In that folder, run:
   ```bash
   bash install.sh --jar lore.jar
   ```
   This copies Lore to `~/.lore/lib/lore.jar` and adds a `lore` command to `~/.local/bin`.
3. If it says `~/.local/bin` is not on your PATH, add the line it prints to `~/.zshrc` or `~/.bashrc` and open a new terminal.
4. Check it works:
   ```bash
   lore --help
   ```

### From source

You also need Maven and Node.js 20 or newer.

```bash
git clone https://github.com/aayush64x/Lore.git
cd Lore
./scripts/install.sh
```

The installer builds Lore if needed (this takes a minute or two the first time), then installs it the same way.

## First run

The first time you run `lore start` or `lore ui` in a terminal, Lore asks for:

1. **Your Anthropic API key.** It is saved in `~/.lore/config.properties`, readable only by you. You can also set the `ANTHROPIC_API_KEY` environment variable instead.
2. **Your coding level and goal**, so explanations fit you.

Change these any time with `lore config`, or just the key with `lore key`.

## Everyday use

In any git project:

```bash
lore start
# ...build something, with an AI agent or by hand...
lore stop        # type what you were trying to do, e.g. "Add a countWords helper and a test for it"
lore ui          # opens http://127.0.0.1:8080 in your browser
```

On the page:

1. Open a session. You land on the first file with something left to learn.
2. Read the **summary** and any **"Not done yet from your goal"** box.
3. Click **Explain this** on a change and step through it with `←` `→`.
4. Click **Test me** and explain the change in your own words, or **Skip test** to mark it yourself.
5. **Start learning →** takes you to the next change you haven't understood.

Press `Ctrl+C` in the terminal to stop `lore ui`.

A tip: the more specific you are at `lore stop` ("add login with password reset", not "stuff"), the better the goal check and explanations.

## Commands

| Command | What it does |
|---|---|
| `lore start` | Snapshots the project and starts a session. |
| `lore stop` | Asks what you were trying to do, saves the changes, and gets the AI summary and goal check. |
| `lore ui` | Starts the local web page and opens your browser. `lore ui --port 9000` uses another port. |
| `lore` | Shows whether a session is running. |
| `lore key` | Sets or changes your Anthropic API key. |
| `lore config` | Changes your API key, coding level, or goal. |

Lore must run inside a git repository. If the folder isn't one, it tells you to run `git init` and does nothing else.

## Costs

Lore calls Claude with your key only when:

- you run `lore stop` (one summary call per session; sessions over 60 changes are summarized in groups of up to 60, plus one call to join them),
- you click **Explain this** (one call per change; saved, so it's never paid for twice),
- you click **Check my answer** (one small grading call).

Only **Explain** uses `claude-sonnet-5-5`, because it has to teach. Summaries and teach-back grading use the much cheaper `claude-haiku-5-5`. A small session usually costs a cent or two; sessions with many explained changes cost more.

To use other models, add any of these lines to `~/.lore/config.properties`:

```properties
anthropic.explain-model=claude-opus-5-5
anthropic.summary-model=claude-sonnet-5-5
anthropic.grading-model=claude-sonnet-5-5
```

(The older `anthropic.model` line still works and sets the Explain model.)

## Your data

- Everything is stored locally in `~/.lore/` (an H2 database). Lore uploads nothing by itself.
- Only the **changed lines** of a session go to the AI, never the whole project. Files git ignores (like `.env`) are never captured. Binary files, lockfiles and build output are skipped.
- The web page only listens on `127.0.0.1`.
- Lore never changes your code. It only reads it and keeps hidden snapshot refs (`refs/lore/sessions/...`).
- Every time Lore starts, it backs up its database to `~/.lore/backups/` and keeps the newest 3. To restore, stop Lore and copy a backup over `~/.lore/lore.mv.db`.
- To keep data somewhere else, set `LORE_HOME` to a folder.

A secret hard-coded in a file you changed would be sent along with that change, so keep secrets in ignored files like `.env`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `lore: command not found` | Add `~/.local/bin` to your PATH (the installer prints the line) and open a new terminal. |
| `Java 17 found, but Lore needs Java 21 or newer` | Install Java 21+ (for example from [Adoptium](https://adoptium.net)). |
| `This folder isn't a git repository` | Run `git init` in your project first. |
| `No Anthropic API key` | Run `lore key`, or set `ANTHROPIC_API_KEY`. |
| The summary says it couldn't be created | Your session is still saved. Fix the key or network, then click **Retry** on the session page. |
| Port 8080 is already in use | `lore ui --port 9000` |
| `Teach-back only works with the Anthropic provider` | Teach-back needs the default Anthropic setup; use **Skip test** meanwhile. |

## Update or uninstall

- **Update:** install the new `lore.jar` the same way. It's safe while `lore ui` is open; restart `lore ui` to use the new version. Your sessions are kept.
- **Uninstall:**
  ```bash
  rm ~/.local/bin/lore ~/.lore/lib/lore.jar
  ```
  Add `rm -rf ~/.lore` to also delete your sessions, settings and key.

## How the snapshot works

Lore uses git plumbing so it never touches your branches, staging area or commit hooks:

1. `git add --all` into a temporary index file (`GIT_INDEX_FILE`), not your real one.
2. `git write-tree` turns that index into a tree object.
3. `git commit-tree` wraps the tree in a commit that isn't on any branch.
4. `git update-ref refs/lore/sessions/<id>` keeps it from being garbage-collected.

`lore stop` takes a second snapshot the same way and diffs the two. This captures uncommitted and untracked files, works in a repository with no commits, and isn't affected by commits you make during the session.

## Project layout

```
backend/       Java 21, Spring Boot 3, Picocli, H2, Flyway (one JAR with the web page inside)
  cli/         start, stop, ui, key, config
  git/         snapshots, diffs, diff parser
  session/     sessions and the summary at lore stop
  change/      changes, explanations, teach-back attempts
  ai/          Anthropic API calls, prompts, output checks
  api/         REST endpoints under /api
frontend/      React, TypeScript, Vite, Tailwind
scripts/       lore launcher and install.sh
devpost/       planning documents and the build log
```

To work on Lore: `cd backend && mvn package` builds everything and runs the tests. For the web page with live reload, run `lore ui` and then `cd frontend && npm install && npm run dev`.

## Built with

Java 21, Spring Boot, Picocli, Spring Data JPA, H2, Flyway, the Anthropic Java SDK (Claude Sonnet and Haiku), React, TypeScript, Vite and Tailwind.

Built for the Devpost hackathon "Build With AI: Basics", with an AI coding agent. The planning documents and a step-by-step build log are in `devpost/`.
