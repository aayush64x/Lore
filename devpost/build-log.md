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
