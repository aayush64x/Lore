import type { ChangeResponse, ExplanationData } from "../api/client";
import ExplainWalkthrough from "./ExplainWalkthrough";

interface ExplainPanelProps {
  change: ChangeResponse;
  // Which change this is in the file, for "Change 2 of 3".
  index: number;
  count: number;
  onPrevChange: () => void;
  onNextChange: () => void;
  explanation: ExplanationData | null;
  loading: boolean;
  error: string | null;
  onExplain: () => void;
  activeStep: number;
  onActivateStep: (i: number) => void;
  onHoverStep: (i: number | null) => void;
  stepLabel: (diffStart: number, diffEnd: number) => string;
  onToggleUnderstood: () => void;
  // "Got it? Mark as understood" at the last step: marks it and moves on.
  onMarkUnderstood: () => void;
}

// The right side of the file page: summary, understood pill, and the
// step-by-step explanation for the selected change.
export default function ExplainPanel({
  change,
  index,
  count,
  onPrevChange,
  onNextChange,
  explanation,
  loading,
  error,
  onExplain,
  activeStep,
  onActivateStep,
  onHoverStep,
  stepLabel,
  onToggleUnderstood,
  onMarkUnderstood,
}: ExplainPanelProps) {
  return (
    <div className="p-5">
      {count > 1 && (
        <div className="flex items-center justify-between mb-4 text-xs text-gray-500">
          <button onClick={onPrevChange} disabled={index === 0} className="hover:text-gray-900 disabled:text-gray-300">
            ← Prev change
          </button>
          <span>
            Change {index + 1} of {count}
          </span>
          <button onClick={onNextChange} disabled={index === count - 1} className="hover:text-gray-900 disabled:text-gray-300">
            Next change →
          </button>
        </div>
      )}

      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide pt-1">Change summary</div>
        <button
          onClick={onToggleUnderstood}
          title="Click to change"
          className={`text-xs font-medium px-2.5 py-1 rounded-full border whitespace-nowrap transition-colors ${
            change.understood
              ? "bg-green-50 text-green-700 border-green-200 hover:bg-green-100"
              : "bg-orange-50 text-orange-700 border-orange-200 hover:bg-orange-100"
          }`}
        >
          {change.understood ? "✓ Understood" : "Not understood yet"}
        </button>
      </div>
      <p className="text-sm text-gray-500 mb-4">{change.description ?? "No summary for this change."}</p>

      {!change.binary && !explanation && (
        <>
          <button
            onClick={onExplain}
            disabled={loading}
            className="w-full text-sm font-medium py-2.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:bg-blue-300 transition-colors"
          >
            {loading ? "Explaining…" : "Explain this"}
          </button>
          {error && <p className="text-sm text-red-600 mt-2">{error} Click the button to try again.</p>}
        </>
      )}

      {explanation && (
        <div className="border-t border-gray-100 pt-4">
          {explanation.current === false && (
            <div className="flex items-center justify-between gap-3 mb-3 px-3 py-2 rounded-md bg-gray-50 border border-gray-200 text-xs text-gray-600">
              <span>Written for {explanation.writtenFor ?? "another level"}</span>
              <button
                onClick={onExplain}
                disabled={loading}
                className="font-medium text-blue-600 hover:underline disabled:text-gray-400 disabled:no-underline whitespace-nowrap"
              >
                {loading ? "Explaining…" : "Explain again"}
              </button>
            </div>
          )}
          {explanation.current === false && error && <p className="text-xs text-red-600 mb-3">{error}</p>}
          <ExplainWalkthrough
            explanation={explanation}
            active={activeStep}
            onActivate={onActivateStep}
            onHoverStep={onHoverStep}
            stepLabel={stepLabel}
            understood={change.understood}
            onMarkUnderstood={onMarkUnderstood}
          />
          <p className="text-xs text-gray-400 mt-4">Keys: ← → steps · [ ] files</p>
        </div>
      )}
    </div>
  );
}
