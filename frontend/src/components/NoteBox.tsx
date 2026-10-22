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
    <section className="mt-6 border-t border-gray-100 pt-4" aria-label="My note">
      <h3 className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">My note</h3>
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
        className="mt-2 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400 focus:border-blue-500 focus:outline-none disabled:bg-gray-50"
      />
      <div className="mt-1.5 flex items-center gap-3">
        <button
          type="button"
          onClick={save}
          disabled={busy || !changed}
          className="text-sm font-medium px-3 py-1.5 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
        >
          {busy ? "Saving…" : "Save note"}
        </button>
        {saved && !changed && <span className="text-xs text-green-700">Saved</span>}
        {error && <span role="alert" className="text-xs text-red-600">{error}</span>}
      </div>
    </section>
  );
}
