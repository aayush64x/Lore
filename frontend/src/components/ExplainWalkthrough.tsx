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
                  ? "border-accent bg-accent-soft text-ink font-medium"
                  : "border-transparent text-ink-3 hover:text-ink-2 hover:bg-sunk"
              }`}
            >
              {step.diff_line_start >= 1 && (
                <span className="block font-mono text-xs font-normal text-ink-3 mb-0.5">
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
            className="text-sm px-3 py-1 rounded-control border border-line-strong text-ink-2 hover:bg-sunk disabled:text-line-strong disabled:border-line disabled:hover:bg-transparent"
          >
            ← Back
          </button>
          <span className="text-xs text-ink-3">
            Step {active + 1} of {steps.length}
          </span>
          <button
            onClick={() => onActivate(active + 1)}
            disabled={active === last}
            className="text-sm px-3 py-1 rounded-control border border-line-strong text-ink-2 hover:bg-sunk disabled:text-line-strong disabled:border-line disabled:hover:bg-transparent"
          >
            Next →
          </button>
        </div>
      )}

      {active === last && (
        <div className="mt-4 p-3 rounded-card border border-ok/30 bg-ok-soft text-center">
          {understood ? (
            <p className="text-sm text-ok font-medium">
              {understoodBy === "teach_back" ? "✓ Passed" : "✓ Self-marked as understood"}
            </p>
          ) : (
            <div className="flex flex-wrap items-center justify-center gap-2">
              {onTestMe && (
                <button
                  onClick={onTestMe}
                  className="text-sm font-medium px-4 py-2 rounded-control bg-accent text-on-accent hover:bg-accent-hover"
                >
                  Test me
                </button>
              )}
              <button
                onClick={onMarkUnderstood}
                className={`text-sm font-medium px-4 py-2 rounded-control ${
                  onTestMe
                    ? "border border-ok/30 text-ok hover:bg-ok/15"
                    : "bg-ok text-on-accent hover:bg-ok/90"
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
          <div className="text-[13px] font-medium text-ink-2 mb-1.5">Concepts</div>
          <div className="flex flex-wrap gap-1.5">
            {concepts.map((c, i) => (
              <button
                key={i}
                onClick={() => setOpenConcept(openConcept === i ? null : i)}
                title={c.meaning}
                className={`text-xs px-2 py-0.5 rounded-full border transition-colors ${
                  openConcept === i
                    ? "bg-ink text-paper border-ink"
                    : "bg-sunk text-ink-2 border-line hover:bg-sunk"
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
          {concept && <p className="text-xs text-ink-2 mt-2">{concept.meaning}</p>}
        </div>
      )}
    </div>
  );
}
