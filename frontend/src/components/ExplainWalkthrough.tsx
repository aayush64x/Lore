import type { ExplanationData } from "../api/client";
import { useState } from "react";

interface ExplainWalkthroughProps {
  explanation: ExplanationData;
  // Tell the parent which diff lines a step is about, so DiffView can highlight them.
  onHighlight?: (start: number, end: number) => void;
  onClearHighlight?: () => void;
  // Turns a step's diff line range into the label shown, like "Lines 12–15",
  // using the same numbers as the gutter next to the code.
  stepLabel: (diffStart: number, diffEnd: number) => string;
}

export default function ExplainWalkthrough({ explanation, onHighlight, onClearHighlight, stepLabel }: ExplainWalkthroughProps) {
  const [activeStep, setActiveStep] = useState<number | null>(null);

  function showStep(i: number, start: number, end: number) {
    setActiveStep(i);
    // The AI can send 0 when it couldn't point at lines; nothing to highlight then.
    if (start >= 1 && end >= start) onHighlight?.(start, end);
  }

  function clearStep() {
    setActiveStep(null);
    onClearHighlight?.();
  }

  return (
    <div>
      <p className="mb-2">{explanation.overview}</p>
      {explanation.steps.map((step, i) => (
        <div
          key={i}
          tabIndex={0}
          onMouseEnter={() => showStep(i, step.diff_line_start, step.diff_line_end)}
          onMouseLeave={clearStep}
          onFocus={() => showStep(i, step.diff_line_start, step.diff_line_end)}
          onBlur={clearStep}
          className={`pl-2 py-1 my-1 border-l-[3px] cursor-default outline-none ${
            activeStep === i ? "border-blue-500 bg-blue-50" : "border-transparent"
          }`}
        >
          {step.diff_line_start >= 1 && (
            <span className="font-mono text-xs text-gray-500">{stepLabel(step.diff_line_start, step.diff_line_end)}: </span>
          )}
          <span>{step.text}</span>
        </div>
      ))}

      {explanation.concepts.length > 0 && (
        <div className="mt-3">
          <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Concepts in this change</h4>
          <ul className="space-y-1">
            {explanation.concepts.map((concept, i) => (
              <li key={i}>
                <span className="font-medium">{concept.name}</span> — {concept.meaning}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
