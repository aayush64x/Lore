# Lore

**Build with AI. Learn what you built.**

Lore is a learning journal for code you build with AI. It records what changed in your project during a coding session, explains each change in plain English, and keeps a list of the changes you haven't understood yet, so you can come back and learn them.

---

## The problem

AI coding tools write code faster than you can read it. You ask Claude Code or ChatGPT for a feature, it works, and you move on. A few weeks later you open your own project and can't explain how half of it works.

Slowing down to read every line while you build kills your momentum. Never reading it means you stop learning.

## The idea

Keep moving fast while you build. Study afterwards.

Lore sits quietly beside your project. You tell it when a coding session starts and stops. It captures every change made in between, whether an AI agent wrote it, you pasted it, or you typed it yourself. Later, when you have time, you open Lore and work through those changes one by one until you understand them.

Lore isn't a changelog. It's a **study list built from your own code**.

---

## How it works

### 1. Start a session

In any git project, before you start coding:

```
$ lore start
Session started in my-app at 2:14 PM. Run lore stop when you're done.
```

Lore takes a snapshot of your project. It doesn't touch your branches, commits, or staging area.

### 2. Code as usual

Use an AI agent, paste code, or type it by hand. Lore stays quiet.

### 3. Stop the session

```
$ lore stop
What were you trying to do? Add login with email and password
Summarizing 4 changed files...
Session saved: Add login with email and password. 4 files changed, 9 changes to review. Run lore ui to study them.
```

Lore compares your project to the snapshot, splits the difference into small changes, and asks AI to write a plain-English summary.

### 4. Study your changes

```
$ lore ui
```

Your browser opens to your study list.

---

## What you see

### Session list

Every session you've recorded, newest first, with how much of it you've understood.

```
┌──────────────────────────────────────────────────────────┐
│  my-app                                                  │
├──────────────────────────────────────────────────────────┤
│  Add login with email and password                       │
│  Oct 20, 2:14 PM · 4 files          3 of 9 understood    │
├──────────────────────────────────────────────────────────┤
│  Fix crash when cart is empty                            │
│  Oct 19, 6:40 PM · 1 file           2 of 2 understood ✓  │
└──────────────────────────────────────────────────────────┘
```

### Session detail

Click a session to see what happened in it:

- **A short AI summary** of the whole session, in plain English.
- **A progress bar** showing how much you've understood.
- **Every change, grouped by file**, with the diff in green (added) and red (removed).
- **A filter** that hides what you've already understood, so you only see what's left.

### Explain this

Every change has an **Explain this** button. Click it and Lore gives you a walkthrough:

1. **Overview:** one sentence on what the change does.
2. **Step by step:** each step explains a few lines. When you hover a step, those lines **light up in the diff**, so your eyes go straight to the code being explained.
3. **Concepts in this change:** the ideas behind the code, each with a one-line meaning.

```
┌─ diff ─────────────────────────────┐   ┌─ Explain ──────────────────────────────┐
│ + import bcrypt from 'bcrypt';     │   │ Checks a user's password at login.     │
│ +                                  │   │                                        │
│█+ const match = await bcrypt      █│◄──│ ▶ Lines 3–4: Compares the typed        │
│█+   .compare(password, user.hash);█│   │   password with the stored hash...     │
│ + if (!match) return res.status(401│   │                                        │
│                                    │   │ Concepts                               │
│                                    │   │ • Hashing: a one-way scramble of ...   │
│                                    │   │ • 401: "you're not logged in" ...      │
└────────────────────────────────────┘   └────────────────────────────────────────┘
```

Explanations are saved, so coming back later shows them instantly.

### Mark as understood

When you get it, click **Mark as understood**. If you realize you didn't, undo it. Every change starts as "not understood yet". Lore doesn't guess what you know. You check things off yourself.

Come back tomorrow, pick up where you left off, and work through a few more.

---

## Who it's for

Students and self-taught developers who build with AI. They ship things that work but don't always understand what they shipped. They want to learn from their own projects, on their own time, without slowing down while they build.

---

## Privacy

- **Everything stays on your computer.** Sessions are stored in a local database in your home folder. There are no accounts and no cloud.
- **Only the changed lines go to the AI**, never your whole project.
- **Files git ignores, like `.env`, are never captured or sent.**
- **Your API key lives in your home folder**, not your project, so you can't commit it by accident.
- **The web page only runs locally** (`127.0.0.1`). Nobody else on your network can see it.

---

## What you need

- Java 21
- Git
- A Claude API key for the AI summaries and explanations

Lore works on new and existing git projects. If a folder isn't a git repo, Lore tells you to run `git init`. It won't do it for you.

---

## Commands

| Command | What it does |
|---|---|
| `lore start` | Snapshots the project and starts a session |
| `lore stop` | Asks what you were doing, saves every change, and writes an AI summary |
| `lore ui` | Opens your study list in the browser |
| `lore` | Shows help and whether a session is running |

---

## Not in this version

- Saving a single file or pasted snippet without a session (`lore add`)
- Searching your history ("why did I change the login code?")
- Working without git
- Accounts and sync across devices
- Quizzes or flashcards. Lore is a study list, not a test.

---

*Built for the Devpost "Build With AI: Basics" hackathon with Java, Spring Boot, React, and Claude.*
