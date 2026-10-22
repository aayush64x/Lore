// "Ask a follow-up" under a change's explanation: earlier questions and answers (saved, so
// they're here when you come back), and a box to ask another. Enter sends; Shift+Enter is
// a new line.

import { useState } from "react";
import { askQuestion } from "../api/client";
import type { QuestionResponse } from "../api/client";

interface FollowUpProps {
  changeId: string;
  questions: QuestionResponse[];
  // After each try: the new question and answer, or null if it failed (it may still have cost).
  onAsked: (asked: QuestionResponse | null) => void;
}

export default function FollowUp({ changeId, questions, onAsked }: FollowUpProps) {
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function ask() {
    const question = draft.trim();
    if (!question || busy) return;
    setBusy(true);
    setError(null);
    try {
      const asked = await askQuestion(changeId, question);
      setDraft("");
      onAsked(asked);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't answer that. Try again.");
      onAsked(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="mt-6 border-t border-line pt-4" aria-label="Follow-up questions">
      <h3 className="text-[13px] font-medium text-ink-2">Ask a follow-up</h3>

      {questions.length > 0 && (
        <ul className="mt-3 space-y-3">
          {questions.map((q, i) => (
            <li key={i} className="text-sm">
              <p className="font-medium text-ink">{q.question}</p>
              <p className="mt-1 whitespace-pre-wrap text-ink-2">{q.answer}</p>
            </li>
          ))}
        </ul>
      )}

      <textarea
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            ask();
          }
        }}
        disabled={busy}
        rows={2}
        maxLength={1000}
        placeholder="Ask a question about this change"
        aria-label="Ask a question about this change"
        className="mt-3 w-full rounded-control border border-line-strong bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-3 focus:border-accent disabled:bg-sunk"
      />
      <div className="mt-1.5 flex items-center gap-3">
        <button
          type="button"
          onClick={ask}
          disabled={busy || draft.trim() === ""}
          className="text-sm font-medium px-3 py-1.5 rounded-control bg-accent text-on-accent hover:bg-accent-hover disabled:opacity-50"
        >
          {busy ? "Thinking…" : "Ask"}
        </button>
        {error && <span role="alert" className="text-xs text-bad">{error}</span>}
      </div>
    </section>
  );
}
