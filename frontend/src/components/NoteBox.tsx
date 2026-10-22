// "My note" at the bottom of a change's panel: the reader's own words on what they learned
// or why they kept this change. Saved per change; also shown under the change on the
// Concepts page. Emptying it and saving clears it.

import { useState } from "react";
import { saveNote } from "../api/client";

interface NoteBoxProps {
  changeId: string;
  note: string | null;
  onSaved: (note: string | null) => void;
}

export default function NoteBox({ changeId, note, onSaved }: NoteBoxProps) {
  const [draft, setDraft] = useState(note ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const changed = draft.trim() !== (note ?? "");

  async function save() {
    if (!changed || busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await saveNote(changeId, draft);
      setDraft(result ?? "");
      setSaved(true);
      onSaved(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't save the note. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="mt-6 border-t border-line pt-4" aria-label="My note">
      <h3 className="text-[13px] font-medium text-ink-2">My note</h3>
      <textarea
        value={draft}
        onChange={(e) => {
          setDraft(e.target.value);
          setSaved(false);
        }}
        disabled={busy}
        rows={3}
        maxLength={5000}
        placeholder="What I learned / why I kept this"
        aria-label="My note on this change"
        className="mt-2 w-full rounded-control border border-line-strong bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-3 focus:border-accent disabled:bg-sunk"
      />
      <div className="mt-1.5 flex items-center gap-3">
        <button
          type="button"
          onClick={save}
          disabled={busy || !changed}
          className="text-sm font-medium px-3 py-1.5 rounded-control border border-line-strong text-ink-2 hover:bg-sunk disabled:opacity-50"
        >
          {busy ? "Saving…" : "Save note"}
        </button>
        {saved && !changed && <span className="text-xs text-ok">Saved</span>}
        {error && <span role="alert" className="text-xs text-bad">{error}</span>}
      </div>
    </section>
  );
}
