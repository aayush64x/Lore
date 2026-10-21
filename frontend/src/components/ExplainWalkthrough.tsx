import type { ExplanationData } from "../api/client";
import { useState } from "react";

interface ExplainWalkthroughProps {
  explanation: ExplanationData;
  // The step being studied. Its lines are highlighted in the diff.
  active: number;
  onActivate: (i: number) => void;
  // Hovering a step highlights its lines for a moment, without changing the active one.
  onHoverStep: (i: number | null) => void;
  // Turns a step's diff line range into the label shown, like "Lines 12–15",
  // using the same numbers as the gutter next to the code.
  stepLabel: (diffStart: number, diffEnd: number) => string;
  understood: boolean;
  understoodBy: "teach_back" | "self" | null;
  onMarkUnderstood: () => void;
  // Set on testable changes: "Test me" is offered next to "Got it?" at the last step.
  onTestMe?: () => void;
}

export default function ExplainWalkthrough({
  explanation,
  active,
  onActivate,
  onHoverStep,
  stepLabel,
  understood,
  understoodBy,
  onMarkUnderstood,
  onTestMe,
}: ExplainWalkthroughProps) {
  const [openConcept, setOpenConcept] = useState<number | null>(null);
  const { steps, concepts } = explanation;
  const last = steps.length - 1;
  const concept = openConcept === null ? null : concepts[openConcept];

  return (
    <div>
      {/* No overview paragraph: it repeated the change summary above it. The steps say it all. */}

      <ol className="space-y-1">
        {steps.map((step, i) => (
          <li key={i}>
            <button
              onClick={() => onActivate(i)}
              onMouseEnter={() => onHoverStep(i)}
              onMouseLeave={() => onHoverStep(null)}
              className={`w-full text-left text-sm pl-3 pr-2 py-2 rounded-r border-l-[3px] transition-colors ${
                active === i
                  ? "border-blue-500 bg-blue-50 text-gray-900 font-medium"
                  : "border-transparent text-gray-400 hover:text-gray-600 hover:bg-gray-50"
              }`}
            >
              {step.diff_line_start >= 1 && (
                <span className="block font-mono text-xs font-normal text-gray-500 mb-0.5">
                  {stepLabel(step.diff_line_start, step.diff_line_end)}
                </span>
              )}
              {step.text}
            </button>
          </li>
        ))}
      </ol>

      {steps.length > 0 && (
        <div className="flex items-center justify-between mt-3">
          <button
            onClick={() => onActivate(active - 1)}
            disabled={active === 0}
            className="text-sm px-3 py-1 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:text-gray-300 disabled:border-gray-200 disabled:hover:bg-transparent"
          >
            ← Back
          </button>
          <span className="text-xs text-gray-500">
            Step {active + 1} of {steps.length}
          </span>
          <button
            onClick={() => onActivate(active + 1)}
            disabled={active === last}
            className="text-sm px-3 py-1 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:text-gray-300 disabled:border-gray-200 disabled:hover:bg-transparent"
          >
            Next →
          </button>
        </div>
      )}

      {active === last && (
        <div className="mt-4 p-3 rounded-lg border border-green-200 bg-green-50 text-center">
          {understood ? (
            <p className="text-sm text-green-700 font-medium">
              {understoodBy === "teach_back" ? "✓ Passed" : "✓ Self-marked as understood"}
            </p>
          ) : (
            <div className="flex flex-wrap items-center justify-center gap-2">
              {onTestMe && (
                <button
                  onClick={onTestMe}
                  className="text-sm font-medium px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
                >
                  Test me
                </button>
              )}
              <button
                onClick={onMarkUnderstood}
                className={`text-sm font-medium px-4 py-2 rounded-md ${
                  onTestMe
                    ? "border border-green-300 text-green-800 hover:bg-green-100"
                    : "bg-green-600 text-white hover:bg-green-700"
                }`}
              >
                Got it? Mark as understood
              </button>
            </div>
          )}
        </div>
      )}

      {concepts.length > 0 && (
        <div className="mt-4">
          <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Concepts</div>
          <div className="flex flex-wrap gap-1.5">
            {concepts.map((c, i) => (
              <button
                key={i}
                onClick={() => setOpenConcept(openConcept === i ? null : i)}
                title={c.meaning}
                className={`text-xs px-2 py-0.5 rounded-full border transition-colors ${
                  openConcept === i
                    ? "bg-gray-800 text-white border-gray-800"
                    : "bg-gray-50 text-gray-700 border-gray-200 hover:bg-gray-100"
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
          {concept && <p className="text-xs text-gray-600 mt-2">{concept.meaning}</p>}
        </div>
      )}
    </div>
  );
}
