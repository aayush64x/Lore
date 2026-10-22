# Lore v0.1.0

First release. Lore is a learning journal for code that AI writes for you: it records what changed while you coded, explains each change for your level, checks it against what you asked for, and makes you explain it back.

## Install

Needs **Java 21+**, **git**, and an **Anthropic API key**.

1. Download **`lore.jar`** and **`install.sh`** below into the same folder.
2. Run `bash install.sh --jar lore.jar`
3. In any git project: `lore start` → code → `lore stop` → `lore ui`

Full instructions: [README](https://github.com/aayush64x/Lore#readme).

## What's in it

- `lore start` / `lore stop` / `lore ui` / `lore key` / `lore config`
- Session summary, plus a **goal check**: which changes match what you asked for, and which parts of your goal weren't done
- Step-by-step **explanations** for your level and goal, with the lines lit up as you go
- **"Lore found an issue"**: real edge cases or bugs in the AI-written code, on the line
- **Teach-back**: explain a change in your own words, graded by Claude Haiku with hints (3 tries, practice retakes after a pass, or skip)
- Progress per file (passed / self-marked / to learn), collapsible file list, keyboard shortcuts
- Local only: data in `~/.lore`, automatic backups, the page listens on 127.0.0.1

## Known limits

- Tested most on macOS. On Windows, use Git Bash or WSL for the installer.
- Teach-back and the goal check need the Anthropic API (the default). On Bedrock, use **Skip test**.
- No way to delete a session from the page yet.
