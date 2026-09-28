---
doc: prd
status: approved
---

# Lore — Product Requirements

A personal CLI + local web UI that records what changed in your code while building with AI, summarizes it in plain English, and walks you through the parts you didn't understand.
Source: `scope.md > The Unique Kernel`, `scope.md > Who It's For`.

## The Core Journey

1. **Install:** Install the Lore JAR and the `lore` command. First time a command needs AI, Lore prompts for an API key and saves it in a config file in the user's home folder (never in the project).
2. **Start a session:** In a git project folder, run `lore start`. Lore prints: *"Session started in [project] at [time]. Run lore stop when you're done."* The snapshot is saved on disk and survives terminal closes and restarts.
3. **Code normally:** Use Claude Code, paste from ChatGPT, type by hand — doesn't matter. Lore stays quiet.
4. **Stop the session:** Run `lore stop`. Lore asks: *"What were you trying to do?"* Type one line and press Enter. Pressing Enter with no text saves the session but warns the summary will be less useful. Lore shows *"Summarizing [n] changed files…"* then prints: *"Session saved: [reason]. [n] files changed, [n] changes to review. Run lore ui to study them."*
5. **Open the web UI:** Run `lore ui`. Lore opens the browser automatically and prints the URL as a fallback. The terminal stays busy; Ctrl+C shuts it down.
6. **Browse sessions:** See a list of sessions, newest first. Click one to see its summary and changes.
7. **Study a change:** Click a change, see the diff, hit Explain. Read the step-by-step walkthrough with highlighted lines. Review the concepts list.
8. **Mark as understood:** Click "Mark as understood" when you get it. Undo if you realize you didn't.
9. **Come back later:** Progress, explanations, and sessions persist. Pick up where you left off.

## Screens and Layout

Lore has two surfaces: a CLI and a local web page.

**CLI** — four commands: `lore start`, `lore stop`, `lore ui`, and `lore` (no arguments, shows help/status). Output is terse — one or two lines per action. The full depth lives in the web UI.

**Web UI** — two views:

- **Session list (home):** Project name at top. Sessions listed newest first. Each row shows: the one-line reason, date and time, number of files changed, and progress (e.g. "3 of 7 understood").
- **Session detail:** Reached by clicking a session. Shows the reason, date, and a short plain-English summary at the top. Below: changes grouped by file, each with a line range, one-line description, and understood/not-understood status. A toggle filters to unreviewed changes only. A progress bar shows session completion.
- **Change detail (inline or expanded within session detail):** The diff (green for added, red for removed). An "Explain this" button. When clicked, the walkthrough appears alongside the code.

## Look and Feel

Minimal and clean. No specific color palette, typography, or references established — the direction is simplicity and readability over decoration.

Source: `scope.md > Inspiration & Identity` (deferred to PRD).

## Features and Behavior

### Session Recording
Source: `scope.md > The Core Loop`, `scope.md > The POC Boundary`.

- `lore start` takes a snapshot of the project state using git. Prints one confirmation line and goes quiet.
- `lore stop` prompts for a one-line reason, diffs against the snapshot, sends the changes to AI for summarization, and saves the session. Prints a summary line with file count and changes-to-review count.
- The snapshot and session state are saved on disk. Closing the terminal or restarting doesn't lose the session. `lore stop` works later from any terminal in the same project.
- If a session has been open a long time, any `lore` command and the web page remind the user to stop it.
- On a brand-new or empty project, the first session is treated as "project setup" and skips generated files and lockfiles.
- Files git ignores (e.g. `.env`) are never sent to the AI.

### AI Summary
Source: `scope.md > The Core Loop`.

- When a session stops, AI generates a short plain-English summary of the whole session (a few sentences) and a one-line description for each individual change.
- Only the session's changes go to AI, not the entire project.
- The summary appears at the top of the session detail page.
- If summarization fails, the session row shows "Summary failed" with a Retry button.

### Explain Walkthrough
Source: `scope.md > The Unique Kernel`.

- Each change has an "Explain this" button.
- Clicking it generates a walkthrough:
  1. One sentence on what the change does overall.
  2. Step-by-step explanation, where each step points at specific lines. As the user reads a step, those lines are highlighted in the code.
  3. A "Concepts in this change" list naming the ideas involved (e.g. "token expiry," "try-with-resources") with a one-sentence meaning for each.
- Explanations are cached — coming back later shows them instantly without re-calling AI.
- If the change only removed code, the explanation covers what was removed and why it might matter.
- If the explain call fails, the user sees "Couldn't explain this change" with a Retry button.
- Fallback: if line highlighting isn't reliable in time, each step shows its line numbers as text instead.

### Progress Tracking
Source: `scope.md > The Unique Kernel`.

- Every change starts as "Not understood yet."
- Each change has a "Mark as understood" button. It can be undone.
- Each session row shows progress (e.g. "3 of 7 understood").
- The session detail page has a progress bar and an unreviewed-only toggle.

## States and Boundaries

- **No sessions yet:** The web page shows *"No sessions yet. Run lore start in your terminal to record your first one."*
- **Session already running:** `lore start` prints *"A session is already running since [time]. Run lore stop first."* The running session isn't touched.
- **No session running:** `lore stop` prints *"No session is running. Start one with lore start."* Nothing else happens.
- **Not a git repo:** `lore start` stops and tells the user to initialize git — Lore won't run `git init` for them.
- **No changes:** `lore stop` with no changes since `lore start` prints *"No changes since lore start. Nothing to save."* and discards the session.
- **Summary or explain failure:** The relevant item shows an error message with a Retry button. The session and other data are preserved.
- **Stale session:** If a session has been open a long time, any `lore` command and the web page show a reminder to stop it.
- **Persistence:** Sessions, summaries, explanations, and understood/not-understood status persist on disk across terminal closes, restarts, and browser refreshes.

## Product Decisions

- **Session mode over manual saves for the PoC** — When an agent writes code directly into the repo, there's no conscious paste moment. Session mode catches everything regardless of who wrote it.
- **Everything starts as "not understood"** — Simpler and more honest than having AI guess what the user already knows. The user checks things off as they learn.
- **Lore won't run `git init`** — Keeps Lore from making assumptions about the user's project setup.
- **API key in home folder, not project** — Prevents accidental commits of credentials.
- **Explanations are cached** — No re-calling AI for the same change. Instant on return.
- **Empty reason allowed with a warning** — Doesn't block the user, but lets them know the summary will suffer.
- **Session state on disk, not in memory** — Survives terminal closes and restarts. Any terminal in the project can stop it.

## What We're Building

- `lore start` — snapshot via git, session state on disk
- `lore stop` — prompt for reason, diff, AI summary, save
- `lore ui` — local web server, auto-opens browser
- Session list page with progress
- Session detail page with grouped changes, summary, toggle, progress bar
- Diff view with explain walkthrough (highlighted lines, step-by-step, concepts)
- Mark as understood / undo
- Error states with retry for AI failures
- Empty state for no sessions
- Stale session reminders
- One-time API key setup

## Deferred From the POC

- **`lore add`** — Manual snapshots for single files, line ranges, or pasted code. Useful but not needed to prove the session-based core loop.
- **`lore why` and web search** — Searching across sessions. Valuable once there are enough sessions to search through.
- **AI-highlighted concepts** — Surfacing concepts on the unreviewed list. Adds complexity to the first version.
- **Working without git** — `lore add` (later) would cover non-git use. Session mode requires git by design.
- **Accounts and cloud sync** — This is a personal local tool first.

## Non-Goals

- **Capturing terminal output** — Session diffing captures what changed; the transcript isn't needed.
- **Live tracking of every file save** — Start/stop gives a clear, intentional boundary.
- **Sending the whole project to AI** — Only the session's changes, not the entire codebase.
- **Linked entries, agent export, graph view** — Interesting but not needed to prove the core loop.
- **Quizzes and flashcards** — Lore is a study list with walkthroughs, not a testing tool.

## Open Questions

None blocking — all consequential product decisions are resolved.
