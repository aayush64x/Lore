import { useState } from "react";
import { explainChange } from "../api/client";
import type { ExplanationData } from "../api/client";

interface ExplainWalkthroughProps {
  changeId: string;
}

export default function ExplainWalkthrough({ changeId }: ExplainWalkthroughProps) {
  const [explanation, setExplanation] = useState<ExplanationData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleExplain() {
    setLoading(true);
    setError(null);
    explainChange(changeId)
      .then((data) => setExplanation(data))
      .catch(() => setError("Couldn't explain this change. Try again."))
      .finally(() => setLoading(false));
  }

  if (!explanation && !loading && !error) {
    return (
      <div className="px-4 py-3 border-t border-gray-100">
        <button
          onClick={handleExplain}
          className="text-sm text-blue-600 hover:text-blue-800 hover:underline"
        >
          Explain this
        </button>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="px-4 py-3 border-t border-gray-100 text-sm text-gray-500">
        Generating explanation...
      </div>
    );
  }

  if (error) {
    return (
      <div className="px-4 py-3 border-t border-gray-100">
        <p className="text-sm text-red-600">{error}</p>
        <button
          onClick={handleExplain}
          className="text-sm text-blue-600 hover:underline mt-1"
        >
          Retry
        </button>
      </div>
    );
  }

  if (!explanation) return null;

  return (
    <div className="px-4 py-3 border-t border-gray-100 space-y-4">
      <p className="text-sm font-medium text-gray-800">{explanation.overview}</p>

      <div className="space-y-3">
        {explanation.steps.map((step, i) => (
          <div key={i} className="text-sm">
            <span className="text-gray-400 font-mono text-xs mr-2">
              Lines {step.diff_line_start}–{step.diff_line_end}
            </span>
            <span className="text-gray-700">{step.text}</span>
          </div>
        ))}
      </div>

      {explanation.concepts.length > 0 && (
        <div>
          <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
            Concepts in this change
          </h4>
          <ul className="space-y-1">
            {explanation.concepts.map((concept, i) => (
              <li key={i} className="text-sm">
                <span className="font-medium text-gray-800">{concept.name}</span>
                <span className="text-gray-500"> — {concept.meaning}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
