---
doc: scope
status: approved
---

# Lore

A personal CLI + local web UI that records what changed in your code, why you changed it, and walks you through the parts you didn't understand.

## The Unique Kernel
The "I don't understand this yet" loop. Every code change starts as unreviewed. You come back, hit explain, get a plain-English walkthrough, and mark it learned when you actually get it. Lore isn't a changelog — it's a study list built from your real coding sessions.

## Who It's For
Students and self-taught developers who build with AI — pasting code from ChatGPT, working with Claude Code, moving fast. They ship things that work but don't always understand what they shipped. They want to learn from their own projects, on their own time, without slowing down while they're building.

## The Core Loop
`lore start` → code normally (agent writes, you paste, you type — doesn't matter) → `lore stop` → answer "what were you trying to do?" in one line → Lore diffs the snapshot, AI summarizes what changed in plain English → open the web UI, see flagged changes, hit explain on one you don't understand, read the walkthrough, mark it learned. Come back tomorrow and do another.

## Inspiration & Identity
Not discussed — to be explored in PRD.

## Why This Matters to the Learner
"I know nothing can replace doing everything by yourself from scratch. Getting in depth with problem. But I guess this hackathon and tool aim is to bridge that gap. Use AI while still learning." Lore is the tool version of that philosophy — move fast with AI, then actually learn what happened.

## What "Working" Looks Like
The one-minute demo: `lore start`, ask Claude Code to add or fix something, `lore stop`, type what you were trying to do. Open the web page, see the session summary and list of changes, open one you don't understand, hit explain, read the step-by-step walkthrough, check it off as understood.

## The POC Boundary
**In:**
- `lore start` — snapshots the project state (requires git)
- `lore stop` — asks "what were you trying to do?", diffs against the snapshot, sends changes to AI for a plain-English summary, saves the session
- `lore ui` — local web page showing session list, per-session change list, explain walkthrough for any change, mark-as-learned
- Works on new and existing git repos; first session treated as project setup, skips generated files and lockfiles
- If the folder isn't a git repo, Lore stops and tells you to initialize one — it won't run `git init` for you
- Everything flagged as "not understood" by default

## Later
- `lore add` — save a single file, line range, or pasted code with a note
- "Why?" search in the web UI and `lore why` in the terminal
- AI-highlighted concepts on top of the unreviewed list
- Working without git
- Accounts and cloud sync

## Explicitly Cut
- **Capturing terminal output** — session diffing catches the result of what changed; the terminal transcript isn't needed
- **Live tracking of every file save** — session start/stop gives a clear, intentional boundary
- **Sending the whole project to the AI** — only the session's changes go to AI, not the entire codebase
- **Linked entries, AI agent export, and graph view** — interesting but not needed to prove the core loop
- **Quizzes and flashcards** — Lore is a study list with explain walkthroughs, not a testing tool
