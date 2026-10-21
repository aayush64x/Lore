import type { ChangeResponse, TeachBackResult } from "../api/client";

interface TeachBackPanelProps {
  change: ChangeResponse;
  answer: string;
  onAnswerChange: (answer: string) => void;
  busy: boolean;
  // The last grade for this change on this visit, if any.
  result: TeachBackResult | null;
  error: string | null;
  onSubmit: () => void;
  // Clears the grade so the answer can be edited and sent again.
  onRetry: () => void;
  onCancel: () => void;
  // Mark understood without passing (understood_by = self).
  onSkip: () => void;
}

const MAX_ANSWER = 2000;

const VERDICT = {
  pass: { title: "Passed ✓", box: "bg-green-50 border-green-200 text-green-800" },
  partly: { title: "Partly there", box: "bg-amber-50 border-amber-200 text-amber-900" },
  fail: { title: "Not yet", box: "bg-red-50 border-red-200 text-red-800" },
};

// Replaces the explanation while testing: the question, the answer box, then the grade.
// The diff stays visible next to it. Every rule (tries, when key points show) comes from
// the server; this only shows what it sent.
export default function TeachBackPanel({
  change,
  answer,
  onAnswerChange,
  busy,
  result,
  error,
  onSubmit,
  onRetry,
  onCancel,
  onSkip,
}: TeachBackPanelProps) {
  const state = result ?? change.teachBack;
  // Already passed: this is practice. No tries used, the pass can't be lost.
  const practice = change.understoodBy === "teach_back";
  const outOfTries = state.attemptsLeft === 0 && change.understoodBy !== "teach_back";
  const tryNumber = Math.min(state.attemptsUsed + (result ? 0 : 1), state.attemptsUsed + state.attemptsLeft);
  const total = state.attemptsUsed + state.attemptsLeft;

  return (
    <div className="p-5">
      <div className="flex items-center justify-between mb-3">
        <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
          {practice ? "Practice" : "Teach it back"}
        </div>
        {practice ? (
          <span className="text-xs text-gray-500">Doesn't use tries. Your pass stays.</span>
        ) : total > 0 && !outOfTries && (
          <span className="text-xs text-gray-500">
            Try {tryNumber} of {total}
          </span>
        )}
      </div>

      <label htmlFor="teach-back-answer" className="block text-sm font-medium text-gray-900 mb-2">
        In your own words, what does this change do and why?
      </label>

      {/* The answer stays on screen after checking (read-only), with the grade under it.
          With no tries left and no answer from this visit, there's nothing to show. */}
      {(!outOfTries || result) && (
        <textarea
          id="teach-back-answer"
          value={answer}
          onChange={(e) => onAnswerChange(e.target.value)}
          onKeyDown={(e) => {
            // Cmd/Ctrl+Enter sends; a plain Enter is a new line.
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey) && !result && !busy && answer.trim() !== "") {
              e.preventDefault();
              onSubmit();
            }
          }}
          maxLength={MAX_ANSWER}
          rows={6}
          autoFocus
          readOnly={!!result || busy}
          placeholder="Plain words are fine. Spelling doesn't count."
          className="w-full text-sm p-3 rounded-md border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none read-only:bg-gray-50 read-only:text-gray-700"
        />
      )}

      {!result && !outOfTries && (
        <>
          <div className="flex items-center justify-between mt-1 mb-3 text-xs text-gray-400">
            <span>Cmd+Enter to check. The code stays on the left.</span>
            <span>
              {answer.length}/{MAX_ANSWER}
            </span>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={onSubmit}
              disabled={busy || answer.trim() === ""}
              className="text-sm font-medium px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:bg-blue-300 transition-colors"
            >
              {busy ? "Checking…" : "Check my answer"}
            </button>
            <button onClick={onCancel} disabled={busy} className="text-sm text-gray-600 hover:text-gray-900">
              Back to the explanation
            </button>
          </div>
        </>
      )}

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

      {result && (
        <div className={`mt-3 p-3 rounded-md border ${VERDICT[result.verdict].box}`}>
          <p className="text-sm font-semibold">{VERDICT[result.verdict].title}</p>
          {result.gotRight.length > 0 && (
            <ul className="mt-2 space-y-1 text-sm">
              {result.gotRight.map((point, i) => (
                <li key={i}>✓ {point}</li>
              ))}
            </ul>
          )}
          {result.missing.length > 0 && (
            <ul className="mt-2 space-y-1 text-sm">
              {result.missing.map((hint, i) => (
                <li key={i}>Hint: {hint}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {result?.verdict === "pass" && (
        <div className="flex items-center gap-3 mt-3">
          {practice && (
            <button
              onClick={onRetry}
              className="text-sm font-medium px-4 py-2 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
            >
              Practice again
            </button>
          )}
          <button onClick={onCancel} className="text-sm text-gray-600 hover:text-gray-900">
            Back to the explanation
          </button>
        </div>
      )}

      {result && result.verdict !== "pass" && !outOfTries && (
        <div className="flex items-center gap-3 mt-3">
          <button
            onClick={onRetry}
            className="text-sm font-medium px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
          >
            Retry (edit your answer)
          </button>
          <button onClick={onCancel} className="text-sm text-gray-600 hover:text-gray-900">
            Back to the explanation
          </button>
        </div>
      )}

      {/* In practice the points are known from the pass; show them only after answering. */}
      {state.keyPoints && (!practice || result) && (
        <div className="mt-4 p-3 rounded-md border border-gray-200 bg-gray-50">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">A full answer covers</p>
          <ul className="list-disc pl-5 space-y-1 text-sm text-gray-700">
            {state.keyPoints.map((point, i) => (
              <li key={i}>{point}</li>
            ))}
          </ul>
        </div>
      )}

      {outOfTries && (
        <div className="mt-4">
          <p className="text-sm text-gray-600 mb-2">No tries left. Read the points above, then mark it yourself.</p>
          {!change.understood && (
            <button
              onClick={onSkip}
              className="text-sm font-medium px-4 py-2 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
            >
              Mark as understood
            </button>
          )}
          <button onClick={onCancel} className="text-sm text-gray-600 hover:text-gray-900 ml-3">
            Back to the explanation
          </button>
        </div>
      )}

      {!result && !outOfTries && !change.understood && (
        <button onClick={onSkip} className="block mt-4 text-xs text-gray-500 hover:text-gray-800 underline">
          Skip test and mark it understood
        </button>
      )}
    </div>
  );
}
