# Remaining slices

Feature freeze: Oct 23, 2026. Deadline: Oct 26, 2026.

Rules (see `CLAUDE.md`): one small task per slice. Build, test, commit, push. Report with /simple. Wait for "next".

## Real-key test

- [x] **0. Real-key test: no later than Oct 20** (Aayush): run `lore key`, enter a real Anthropic API key, then start → edit → stop → ui → Explain in a test repo. Check the summary and explanation are real.

## Make what's there work smoothly

- [x] **1. Tidy up `lore stop` output.** Hide the raw `WARN ...` log line. Say "1 file", not "1 files".
- [x] **2. Retry button for a failed summary** on the session page. The endpoint `POST /api/sessions/{id}/retry-summary` already exists.
- [x] **3. "Unreviewed only" filter** on the session page. Hides changes marked as understood.
- [x] **4. Highlight a range of lines in the diff.** `DiffView` can color given lines blue.
- [x] **5. Hover a step to highlight its lines.** Connects explain steps to the diff from slice 4.
- [x] **6. Fix the lint error in `SessionList.tsx`** and show an error message if the session list fails to load.
- [x] **6b. API controller tests + fixes.** Tests for `SessionController` and `ChangeController` endpoints. Fix whatever they find.
- [x] **6c. File list + file page.** The session page lists the changed files; click one to see only its changes.

## Make it usable by other people

- [x] **7. Open the browser on Linux and Windows too.** The fallback only works on macOS today.
- [x] **8. Installer.** Install the `lore` command so it works from any folder, not just inside the Lore repo.
- [ ] **9. Rewrite the README for strangers.** Install, API key, start, stop, ui. Remove the AWS setup and "planned" labels.
- [ ] **10. Downloadable release on GitHub.** Build the JAR and publish a GitHub Release. Needs `gh` (`brew install gh`) or a manual upload.
- [ ] **11. Fresh-install test.** Follow the README as a brand-new user with an empty setup. Fix anything that breaks.
- [ ] **11b. UI polish pass** (before Oct 23). Spacing, buttons, empty and error states, readable on a small screen.

## Must fix before rollout (do not cut)

- [ ] **13. Don't lose data when the JAR is replaced while Lore runs.** On Oct 21 the JAR was rebuilt while `lore ui` ran from it. On Ctrl+C, H2 crashed while saving (`NoClassDefFoundError: Chunk$PositionComparator` in `~/.lore/lore.trace.db`), and two sessions were lost. Real users hit the same thing if they re-run `install.sh` (or update) while `lore ui` is open. Fix:
  - `scripts/install.sh`: copy the new JAR to a temp file, then `mv` it over `lore.jar`. The running program keeps the old file until it exits.
  - Test: start `lore ui`, re-run the installer, press Ctrl+C, then check that every session is still there.
  - (The database backup on every start was moved out of this item and done on Oct 21.)

## Review page rework (Oct 21 plan)

Order: 15 → 16 → 17 → 18. Then 19 only as a stretch, with a hard cutoff.

- [x] **15. Fix stray `}]}` at the source.** The model sometimes ends a text field with JSON characters (seen Oct 21: "...matching the code.}]}"). Tell it plain text only. Check every text field before saving. Ask once more if it's bad, then fail instead of saving bad text. Old damaged explanations get an "Explain again" note.
- [ ] **16. One summary, one status.** *(On hold Oct 21: Aayush is undecided. Middle option: drop only the overview paragraph and the card-header status.)* The session summary becomes one sentence under the goal. Remove the change summary and overview from the panel. Status only in the top bar and the sidebar dots. Keep one Mark as understood button.
- [x] **17. Concepts only for what the diff changes.** Prompt fix (no tags for code the diff didn't touch, like "Regex trimming").
- [x] **18. Collapsible files sidebar.** Button on the sidebar plus the `b` key. Thin rail with status dots, file-count badge and filename tooltips. Saved in localStorage (try/catch). Animated width, no jump.
- [ ] **19. Stretch: questions.** 1–2 multiple-choice questions (3–4 options) per substantive change, plus one edge-case question only if a real one exists. The server checks answers; retry allowed. The explanation shows after answering. A change is understood when its questions are answered correctly. **Hard cutoff: Oct 23 noon.** If it isn't working by then, keep the current Mark as understood.

## Cut first if time runs short

- [ ] **12. Warning for sessions left open too long**, in the terminal and on the web page.

## After the hackathon

- **Trivial vs substantive changes.** Classify each change (comments, docs, formatting, renames = trivial) in the `lore stop` summary call, with a comment/whitespace-only fallback. New `changes.kind` column (old rows = substantive). Fold trivial ones into "N trivial changes – show". Leave them out of "N of M understood".
- **Inline notes.** Show each explain step under the line it explains, like GitHub review comments, collapsible.
- **Lookalike characters.** Scan the diff for `…`, smart quotes, non-breaking spaces and dashes, and have the explanation name them ("… (1 char, U+2026)" vs "... (3 chars)"). Show code in explanations as inline code.

## Not code slices

- [x] **Delete the 2 fake demo sessions** from `~/.lore` ("Add user authentication", "Fix pagination bug in project list").
- [ ] **Close the old Oct 3 session** (Aayush, before the demo): run `lore stop` in the Lore folder.
- [ ] **Demo video** (Aayush, after the freeze).
- [ ] **Devpost write-up** (Aayush, after the freeze).
