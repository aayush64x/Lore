import { explainChange } from "../api/client";
import type { ExplanationData } from "../api/client";
import { useState } from "react";
interface ExplainWalkthroughProps {
  changeId: string;
}

export default function ExplainWalkthrough({ changeId }: ExplainWalkthroughProps) {

  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null> (null);
  const [explanation,setExplanation] = useState<ExplanationData|null>(null);

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
        <button onClick={handleExplain}>Explain this</button>
      )}

      {loading && <p>Generating explanation…</p>}

      {error && (
        <div>
          <p>{error}</p>
          <button onClick={handleExplain}>Retry</button>
        </div>
      )}

      {explanation && !loading && !error && (
        <div>
          <div>{explanation.overview}</div>
          {explanation.steps.map((step, i) => (
            <div key={i}>
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
