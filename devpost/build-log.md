# Build log

One entry per slice. Newest at the bottom.

---

## Slice 2: Retry button for a failed summary

**Commit:** `109fd79`

- If the AI summary failed, the session page now shows a **Retry** button.
- Clicking it asks the AI again. While it works, the button says "Creating summary...".
- If it works, the new summary appears right away, with no page refresh.
- If it fails again, the reason shows in red (for example, "No Anthropic API key...").
- Why: before this, a failed summary stayed failed forever.

**Files:** `frontend/src/api/client.ts`, `frontend/src/pages/SessionDetail.tsx`

**How to check:**
1. Run `./scripts/lore ui` and open a session with no summary.
2. Click **Retry**.
3. No API key: you see a red "No Anthropic API key" message. With a real key: the summary appears.

---

## Slice 3: "Unreviewed only" filter

**Commit:** `bb20882`

- The session page has a new **Unreviewed only** checkbox next to "Changes".
- When it's ticked, changes you marked as understood are hidden.
- If every change is understood, it says "Nothing left to review".
- If you mark a change as understood while the box is ticked, that change disappears right away. This is on purpose.
- Why: so you only see what you still need to study.

**Files:** `frontend/src/pages/SessionDetail.tsx`

**How to check:**
1. Run `./scripts/lore ui` and open a session with a few changes.
2. Mark one change as understood.
3. Tick **Unreviewed only**. That change should disappear. Untick it and it comes back.

---

## Slices 4 + 5: Highlight lines when you hover a step

**Commit:** `3163b68`

- The diff can now light up a range of lines in blue.
- When you move your mouse over an explain step (like "Lines 3–4: ..."), those lines light up in the diff above it.
- Move the mouse away and the highlight goes away.
- The step you're on also gets a blue bar on its left. You can use the Tab key instead of the mouse.
- Only that change's diff lights up. Other changes on the page stay the same.
- Why: your eyes go straight to the code the step is talking about.

**Files:** `frontend/src/components/DiffView.tsx`, `frontend/src/components/ExplainWalkthrough.tsx`, `frontend/src/pages/SessionDetail.tsx`

**How to check (needs a real API key):**
1. Run `./scripts/lore ui`, open a session, and click **Explain this** on a change.
2. Move your mouse over a step that says "Lines 3–4".
3. Count down the diff: lines 3 and 4 should be blue with an outline. (The `@@` line at the top counts as line 1.)
4. Move the mouse away. The blue goes away.

---

## Slice 6b: API controller tests + fixes

**Commit:** `fc5145d`

- Added 15 tests for the web server's endpoints (the URLs the page talks to): the session list, one session, "mark as understood", and "explain".
- **Bug found and fixed:** the server saved explain steps with the wrong names for the line numbers. The page couldn't read them, so with a real API key you'd see "Lines –" with no numbers, and hover highlighting wouldn't work. Now the names match.
- Old explanations saved with the wrong names are fixed when they're read. (Your database had none.)
- Asking to explain a picture or other binary file now gives a clear "can't be explained" error instead of sending it to the AI.
- If the AI sends back no steps or no concepts, the page gets empty lists instead of a server crash.

**Files:** `backend/src/main/java/com/lore/api/controller/ChangeController.java`, `backend/src/test/java/com/lore/api/ApiControllerTest.java` (new)

**How to check:**
1. In the `backend` folder, run `mvn test`. You should see `Tests run: 79, Failures: 0`.
2. With a real API key: run `./scripts/lore ui`, click **Explain this** on a change. Each step should start with real numbers, like "Lines 3–4:".

---

## Slice 7: Open the browser on Linux and Windows

**Commit:** `ebc365b`

- `lore ui` tries to open your browser by itself.
- Before, the backup way only worked on a Mac. Now it knows the right command for each system: `open` on Mac, `xdg-open` on Linux, and `rundll32` on Windows.
- If nothing works (for example, a computer with no screen), it prints "Open http://... in your browser" so you can copy the link.
- It only prints that line when the browser really didn't open.
- Why: people on Linux and Windows will use Lore too.

**Files:** `backend/src/main/java/com/lore/cli/UICommand.java`, `backend/src/test/java/com/lore/cli/UICommandTest.java` (new)

**How to check:**
1. Run `./scripts/lore ui` on your Mac. Your browser should open by itself.
2. In the `backend` folder, run `mvn test`. The 3 `UICommandTest` tests check the command for Mac, Windows and Linux.
3. If you have a Linux or Windows computer, try `lore ui` there too.

---

## Slice 6c: File list and a page for each file

- The session page no longer shows every diff at once. It shows the summary and a list of **Files changed**.
- Each file row shows its path, whether it's new, edited, deleted or renamed, how many changes it has, and how many you've understood.
- Click a file to open its own page with only that file's changes. Each change still has its diff, **Explain this**, hover highlighting and the understood button.
- At the bottom of a file page, **Next file** takes you to the next file. **← All files** takes you back to the list.
- **Unreviewed only** now hides files where every change is understood.
- Refreshing a file page works too. The server sends back the app for the new address.
- Why: one long page with every diff was hard to read. Now you study one file at a time.

**Files:** `frontend/src/pages/SessionDetail.tsx`, `frontend/src/pages/FileDetail.tsx` (new), `frontend/src/components/ChangeCard.tsx` (new), `frontend/src/App.tsx`, `backend/src/main/java/com/lore/api/controller/SpaController.java`, `backend/src/test/java/com/lore/api/SpaControllerTest.java` (new)

**How to check:**
1. In the `backend` folder, run `mvn package`. You should see `Tests run: 84, Failures: 0`.
2. In your test repo, run `lore ui` and open a session. You should see a list of files, not diffs.
3. Click a file. Only that file's changes show. Click **Next file**, then **← All files**.
4. Mark every change in a file as understood, go back, and tick **Unreviewed only**. That file should disappear.
5. On a file page, press refresh in the browser. The page should load again.

---

## First-run setup + `lore config`

- The first time you run `lore start` or `lore ui`, Lore asks 3 things:
  1. Your API key (hidden while you type). You can press Enter to skip it.
  2. How much coding you know: new to coding, know some, or developer.
  3. Your goal: understand what my app does, or learn to code.
- Press Enter on a question to take the suggested answer.
- Typing something wrong (like `9`) asks again.
- Answers are saved in `~/.lore/config.properties`, the same private file as the key.
- It asks only once. If you already have a key, it asks only the 2 new questions.
- It never asks during `lore stop`, `--help`, or when input comes from a pipe instead of a person.
- New command `lore config`: shows your settings and lets you change any of them. Enter keeps what you have.
- Why: the next slice uses your level and goal to write explanations that fit you.

**Files:** `backend/src/main/java/com/lore/ai/UserProfile.java` (new), `backend/src/main/java/com/lore/ai/ApiKeyStore.java`, `backend/src/main/java/com/lore/ai/ApiKeyPrompt.java`, `backend/src/main/java/com/lore/cli/ConfigCommand.java` (new), `backend/src/main/java/com/lore/cli/LoreCli.java`, `backend/src/test/java/com/lore/ai/SetupPromptTest.java` (new), `backend/src/test/java/com/lore/ai/ApiKeyNeverLeaksTest.java`

**How to check:**
1. In the `backend` folder, run `mvn package`. You should see `Tests run: 95, Failures: 0`.
2. Run `lore ui` in your test repo. Your key is already saved, so it asks only the level and goal questions.
3. Run `lore ui` again. It asks nothing.
4. Run `lore config`. It shows your settings. Change one, then check `~/.lore/config.properties`.

---

## Write as much as you want at `lore stop`

- "What were you trying to do?" used to take one line. Now it can be as long as you like.
- **Way 1, type it:** write as many lines as you want. One empty line is kept as a paragraph break. Press Enter 2 more times to finish.
- **Way 2, editor:** type `e` and press Enter. A text editor opens. Write, save, close, and Lore continues.
  - It uses your own editor setting (`$VISUAL` or `$EDITOR`) if you have one.
  - Otherwise it uses Notepad on Windows and nano on Mac or Linux. It never falls back to vim.
  - The editor file has help lines that start with `#`. Lore ignores them.
  - If the editor can't open, you type the text in the terminal instead.
- Press Enter right away to skip, same as before.
- The first line becomes the session's title in the list. The rest shows under the title on the session page.
- The AI summary gets the whole text.
- The database can now hold up to 20,000 characters (it was 1,024). Old sessions are kept.
- Why: people want to explain what they were trying to do, or paste the prompt they gave their AI tool.

**Files:** `backend/src/main/java/com/lore/cli/ReasonInput.java` (new), `backend/src/main/java/com/lore/cli/StopCommand.java`, `backend/src/main/java/com/lore/session/Session.java`, `backend/src/main/resources/db/migration/V3__longer_session_reason.sql` (new), `backend/src/main/java/com/lore/ai/AiPrompts.java`, `frontend/src/api/client.ts`, `frontend/src/pages/SessionList.tsx`, `frontend/src/pages/SessionDetail.tsx`, `frontend/src/pages/FileDetail.tsx`, `backend/src/test/java/com/lore/cli/ReasonInputTest.java` (new), `backend/src/test/java/com/lore/session/SessionServiceTest.java`

**How to check:**
1. In the `backend` folder, run `mvn package`. You should see `Tests run: 107, Failures: 0`.
2. In your test repo: `lore start`, change a file, `lore stop`. Type 2 paragraphs, then press Enter 2 more times.
3. Do it again, but type `e`. nano opens. Write something, press Ctrl+O, Enter, then Ctrl+X.
4. Run `lore ui`. The list shows only the first line. Open the session to see the rest under the title.

---

## Navbar

- Every page now has a white bar across the top. It stays at the top when you scroll.
- On the left: **Lore**. Click it to go to the session list.
- Next to it, a breadcrumb trail (a "you are here" path): `project › session title › file`.
  - Click the project name to go to the session list.
  - Click the session title to go to that session's file list.
  - The last item is the page you're on, so it isn't a link.
- Long titles and file paths are cut off with "…". Hover to see the full text.
- Removed the old "← Back to sessions" and "← All files" buttons. The bar replaces them.
- The session page used to lose your project when you went back. Now the server sends the project folder with each session, so links always find the right list.
- Why: you always know where you are and can jump back with one click.

**Files:** `frontend/src/components/Navbar.tsx` (new), `frontend/src/pages/SessionList.tsx`, `frontend/src/pages/SessionDetail.tsx`, `frontend/src/pages/FileDetail.tsx`, `frontend/src/api/client.ts`, `backend/src/main/java/com/lore/api/dto/SessionDetailResponse.java`, `backend/src/main/java/com/lore/api/controller/SessionController.java`, `backend/src/test/java/com/lore/api/ApiControllerTest.java`

**How to check:**
1. In the `backend` folder, run `mvn package`. You should see `Tests run: 107, Failures: 0`.
2. Run `lore ui`. The bar shows `Lore | your-project`.
3. Open a session, then a file. The bar shows `Lore | your-project › session title › file`.
4. Click the session title, then the project name, then **Lore**. Each one takes you to the right page.
5. Refresh on a file page. The bar still shows the full path.

---

## Explanations that fit you

- **Explain this** now writes for the person reading it, using the level and goal from setup (`lore config`).
- **New to coding + understand my app:** first says what the code does for the app, in plain words, like "The checkout page now adds tax before showing the total." Then lists at most 3 coding ideas, briefly.
- **New to coding + learn to code:** says what the code does, then how. Names and explains each coding idea the first time it shows up.
- **Know some:** normal code words, with the less common ones explained. The "learn" goal also points out patterns worth learning.
- **Developer:** short and technical. Points out edge cases, possible bugs and security issues. The "learn" goal also suggests better ways to write it.
- The AI also gets what you wrote at `lore stop`, so it can say "You asked for X. This part does that by...".
- Each explanation remembers which profile it was written for. If you change your profile, **Explain this** makes a new one in the new style. If nothing changed, it reuses the saved one, so no extra AI call.
- Explanations made before this update are kept as they are.

**Files:** `backend/src/main/java/com/lore/ai/AiPrompts.java`, `backend/src/main/java/com/lore/ai/AiService.java`, `backend/src/main/java/com/lore/ai/AnthropicAiService.java`, `backend/src/main/java/com/lore/ai/BedrockAiService.java`, `backend/src/main/java/com/lore/ai/NoOpAiService.java`, `backend/src/main/java/com/lore/ai/UserProfile.java`, `backend/src/main/java/com/lore/api/controller/ChangeController.java`, `backend/src/main/java/com/lore/change/Explanation.java`, `backend/src/main/resources/db/migration/V4__explanation_profile.sql` (new), tests in `AiPromptsTest`, `ApiControllerTest` and `FakeAi`

**How to check (needs your real key):**
1. In the `backend` folder, run `mvn package`. You should see `Tests run: 115, Failures: 0`.
2. Run `lore config` and pick **New to coding** and **Understand what my app does**.
3. Do a new `lore start`, change some code, `lore stop`, then `lore ui`. Click **Explain this**. It should talk about what the app does, in plain words.
4. Run `lore config` again and pick **I'm a developer**. Reload the page (no need to restart `lore ui`) and click **Explain this** on the same change. It should be shorter and more technical.

---

## Long new files are split into chunks

- A new file used to be one giant change. If it had 300 lines, you got one 300-line diff with one **Explain this**.
- Now, a new file longer than 80 lines is split into chunks of about 30–80 lines.
- Each chunk has its own **Explain this** and its own **Understood** button.
- Where it cuts (works for any language, because it looks at the layout, not the code itself):
  - Always right after an empty line, at the least indented spot. That's usually where a function, class or heading starts, including the comment above it.
  - Never at a line that closes something, like `}` or `</div>`.
  - If there are no empty lines, it cuts every 80 lines.
  - It never leaves a tiny last chunk.
- New files now show as plain code with line numbers, without a `+` on every line.
- Explain steps for new files use those same line numbers, so "Lines 12–15" matches the numbers next to the code.
- Files you change (not new) look the same as before.
- Sessions saved before this update keep their one big change.
- Tested on two real files: a Java file was cut at its methods, and a React page was cut at its main sections.

**Files:** `backend/src/main/java/com/lore/git/NewFileChunker.java` (new), `backend/src/main/java/com/lore/git/DiffParser.java`, `frontend/src/components/DiffView.tsx`, `frontend/src/components/ChangeCard.tsx`, `frontend/src/components/ExplainWalkthrough.tsx`, `backend/src/test/java/com/lore/git/NewFileChunkerTest.java` (new), `backend/src/test/java/com/lore/git/DiffParserTest.java`

**How to check:**
1. In the `backend` folder, run `mvn package`. You should see `Tests run: 124, Failures: 0`.
2. In your test repo: `lore start`, then copy in a long file (for example `cp ~/Desktop/Projects/Lore/frontend/src/pages/SessionDetail.tsx .`), then `lore stop`.
3. Run `lore ui` and open that file. You should see 3 chunks (lines 1–79, 80–123, 124–178), each with its own buttons.
4. The code has line numbers and no `+` signs.
5. Click **Explain this** on the second chunk. Hover a step. The line numbers in the step match the numbers next to the highlighted code.

---

## Slice 8: installer

- New script `scripts/install.sh` installs the `lore` command so it works from any folder, not just inside the Lore repo.
- It copies the JAR to `~/.lore/lib/lore.jar` and writes a small `lore` command to `~/.local/bin/lore`.
- If there's no built JAR yet, it builds one with Maven first.
- `--jar FILE` installs a JAR you downloaded instead. The GitHub release (slice 10) will use this.
- It checks for Java 21+ and warns if git is missing.
- If `~/.local/bin` isn't on your PATH, it prints the exact line to add to `~/.zshrc`.
- It prints how to uninstall (delete two files).
- Your sessions and settings in `~/.lore` are not touched.

**Files:** `scripts/install.sh` (new), `devpost/slices.md`

**How to check:**
1. In the Lore folder, run `./scripts/install.sh`. It should say `Installed: /Users/you/.local/bin/lore` and `Done.`
2. Open a new terminal, `cd` into any other git project, and run `lore`. It should show the session status.
3. Run `which lore`. It should print `~/.local/bin/lore`.
