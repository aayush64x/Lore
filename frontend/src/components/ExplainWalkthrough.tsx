import { explainChange } from "../api/client";
import type { ExplanationData } from "../api/client";
import { useState } from "react";
interface ExplainWalkthroughProps {
  changeId: string;
  // Tell the parent which diff lines a step is about, so DiffView can highlight them.
  onHighlight?: (start: number, end: number) => void;
  onClearHighlight?: () => void;
}

export default function ExplainWalkthrough({ changeId, onHighlight, onClearHighlight }: ExplainWalkthroughProps) {

  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null> (null);
  const [explanation,setExplanation] = useState<ExplanationData|null>(null);
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

  const handleExplain = async() => {
    setLoading(true);
    setError(null);
    try{
      const data = await explainChange(changeId);
      setExplanation(data);
    }
    catch {
      setError("Couldn't explain this change.");
    }
    finally{
      setLoading(false);
    }
  }



  return (
    <div>
      {!explanation && !loading && !error && (
        <button onClick={handleExplain} className="text-blue-600 hover:underline">Explain this</button>
      )}

      {loading && <p>Generating explanation…</p>}

      {error && (
        <div>
          <p>{error}</p>
          <button onClick={handleExplain} className="text-blue-600 hover:underline">Retry</button>
        </div>
      )}

      {explanation && !loading && !error && (
        <div>
          <div>{explanation.overview}</div>
          {explanation.steps.map((step, i) => (
            <div
              key={i}
              tabIndex={0}
              onMouseEnter={() => showStep(i, step.diff_line_start, step.diff_line_end)}
              onMouseLeave={clearStep}
              onFocus={() => showStep(i, step.diff_line_start, step.diff_line_end)}
              onBlur={clearStep}
              className={`pl-2 py-1 my-1 border-l-2 cursor-default outline-none ${
                activeStep === i ? "border-blue-500 bg-blue-50" : "border-transparent"
              }`}
            >
              <span>Lines {step.diff_line_start}–{step.diff_line_end}: </span>
              <span>{step.text}</span>
            </div>
          ))}

          {explanation.concepts.length > 0 && (
            <div>
              <h4>Concepts in this change</h4>
              <ul>
                {explanation.concepts.map((concept, i) => (
                  <li key={i}>
                    {concept.name} — {concept.meaning}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
