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
    <section className="mt-6 border-t border-gray-100 pt-4" aria-label="Follow-up questions">
      <h3 className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">Ask a follow-up</h3>

      {questions.length > 0 && (
        <ul className="mt-3 space-y-3">
          {questions.map((q, i) => (
            <li key={i} className="text-sm">
              <p className="font-medium text-gray-900">{q.question}</p>
              <p className="mt-1 whitespace-pre-wrap text-gray-700">{q.answer}</p>
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
        className="mt-3 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400 focus:border-blue-500 focus:outline-none disabled:bg-gray-50"
      />
      <div className="mt-1.5 flex items-center gap-3">
        <button
          type="button"
          onClick={ask}
          disabled={busy || draft.trim() === ""}
          className="text-sm font-medium px-3 py-1.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {busy ? "Thinking…" : "Ask"}
        </button>
        {error && <span role="alert" className="text-xs text-red-600">{error}</span>}
      </div>
    </section>
  );
}
