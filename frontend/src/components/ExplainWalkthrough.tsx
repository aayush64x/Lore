import { useState } from "react";
import { explainChange } from "../api/client";
import type { ExplanationData } from "../api/client";

interface ExplainWalkthroughProps {
  changeId: string;
  onHighlight?: (start: number, end: number) => void;
  onClearHighlight?: () => void;
}

export default function ExplainWalkthrough({ changeId, onHighlight, onClearHighlight }: ExplainWalkthroughProps) {
  const [explanation, setExplanation] = useState<ExplanationData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // TODO: Add state to track which step is currently active (hovered/focused).
  //
  // WHAT: When the user hovers over a step, call onHighlight with that step's
  //   diff_line_start and diff_line_end so DiffView highlights those lines.
  //   When they move away, call onClearHighlight to remove the highlight.
  //
  // INPUTS:
  //   - onHighlight(start, end): callback from the parent — tells DiffView
  //     which lines to highlight
  //   - onClearHighlight(): callback from the parent — clears the highlight
  //   - Each step has: step.diff_line_start, step.diff_line_end
  //
  // STEPS:
  //   1. Add a state variable for the active step index (null when none).
  //   2. On the step's <div>, add onMouseEnter that sets the active step
  //      and calls onHighlight(step.diff_line_start, step.diff_line_end).
  //   3. On the step's <div>, add onMouseLeave that clears the active step
  //      and calls onClearHighlight().
  //   4. Give the active step a visual cue (e.g. a left border or background)
  //      so the user knows which step they're reading.
  //   5. Guard: only call onHighlight/onClearHighlight if they exist
  //      (they're optional props).
  //
  // READ: React docs > "Responding to Events" (event handlers on elements)
  //
  // HOW TO CHECK: In the browser, click "Explain this", hover each step.
  //   The step should visually highlight, and the matching diff lines above
  //   should get the blue outline. Moving the mouse away clears both.

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
