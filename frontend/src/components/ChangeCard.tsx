import { useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { explainChange } from "../api/client";
import type { ChangeResponse, ExplanationData } from "../api/client";
import DiffView from "./DiffView";
import ExplainWalkthrough from "./ExplainWalkthrough";
import { diffRows, stepLabel } from "./diffLines";

interface ChangeCardProps {
  change: ChangeResponse;
  onToggleUnderstood: () => void;
}

const TYPE_TAG: Record<string, string> = {
  ADDED: "bg-green-50 text-green-700 border-green-200",
  MODIFIED: "bg-blue-50 text-blue-700 border-blue-200",
  DELETED: "bg-red-50 text-red-700 border-red-200",
  RENAMED: "bg-purple-50 text-purple-700 border-purple-200",
};

// One change: the diff on the left and a sticky panel on the right with the
// summary, the understood pill, and the step-by-step explanation.
// Below 1000px wide the panel goes under the diff. top-12 keeps the panel
// under the sticky navbar (h-12).
export default function ChangeCard({ change, onToggleUnderstood }: ChangeCardProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [explanation, setExplanation] = useState<ExplanationData | null>(null);
  const [activeStep, setActiveStep] = useState(0);
  const [hoverStep, setHoverStep] = useState<number | null>(null);
  const diffRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const rows = useMemo(
    () => (change.diffContent ? diffRows(change.diffContent, change.oldStart, change.newStart) : []),
    [change.diffContent, change.oldStart, change.newStart]
  );

  // Arrow keys work right away once the explanation shows up.
  useEffect(() => {
    if (explanation) panelRef.current?.focus({ preventScroll: true });
  }, [explanation]);

  async function handleExplain() {
    setLoading(true);
    setError(null);
    try {
      setExplanation(await explainChange(change.id));
      setActiveStep(0);
    } catch {
      setError("Couldn't explain this change.");
    } finally {
      setLoading(false);
    }
  }

  // Hovering a step shows its lines; otherwise the active step's lines stay lit.
  // The AI can send 0 when it couldn't point at lines; nothing to highlight then.
  const shownStep = explanation?.steps[hoverStep ?? activeStep];
  const highlight = shownStep && shownStep.diff_line_start >= 1 && shownStep.diff_line_end >= shownStep.diff_line_start
    ? { start: shownStep.diff_line_start, end: shownStep.diff_line_end }
    : null;

  function activate(i: number) {
    if (!explanation || i < 0 || i >= explanation.steps.length) return;
    setActiveStep(i);
    const step = explanation.steps[i];
    if (step.diff_line_start < 1) return;
    // Rows are tagged with their diff line number. Plain code hides the @@ header,
    // so take the first row that exists at or after the step's start.
    const target = [...(diffRef.current?.querySelectorAll<HTMLElement>("[data-diff-line]") ?? [])].find(
      (el) => Number(el.dataset.diffLine) >= step.diff_line_start
    );
    target?.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  function handleKeyDown(e: KeyboardEvent) {
    if (!explanation) return;
    if (e.key === "ArrowRight") activate(activeStep + 1);
    else if (e.key === "ArrowLeft") activate(activeStep - 1);
    else return;
    e.preventDefault();
  }

  const plain = change.changeType === "ADDED";
  const fileName = change.oldFilePath ? `${change.oldFilePath} → ${change.filePath}` : change.filePath;

  const pill = (
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
  );

  return (
    <div className="bg-white border border-gray-200 rounded-lg mb-4">
      <div className="px-4 py-3 flex items-center gap-2 flex-wrap border-b border-gray-100">
        <span className="font-mono text-sm font-semibold break-all">{fileName}</span>
        <span
          className={`text-[10px] font-semibold tracking-wide px-1.5 py-0.5 rounded border ${
            TYPE_TAG[change.changeType] ?? "bg-gray-50 text-gray-600 border-gray-200"
          }`}
        >
          {change.changeType}
        </span>
        <span className="font-mono text-xs text-gray-400">
          Lines {change.newStart}–{change.newStart + Math.max(change.newCount - 1, 0)}
        </span>
      </div>

      <div className="grid min-[1000px]:grid-cols-[3fr_2fr]">
        <div ref={diffRef} className="min-w-0">
          {change.binary ? (
            <p className="px-4 py-3 text-sm text-gray-500">Binary file, no diff to show.</p>
          ) : (
            <DiffView rows={rows} highlightStart={highlight?.start} highlightEnd={highlight?.end} plainCode={plain} />
          )}
        </div>

        <div className="border-t min-[1000px]:border-t-0 min-[1000px]:border-l border-gray-100">
          <div
            ref={panelRef}
            tabIndex={-1}
            onKeyDown={handleKeyDown}
            className="p-4 outline-none min-[1000px]:sticky min-[1000px]:top-12 min-[1000px]:max-h-[calc(100vh-3rem)] min-[1000px]:overflow-y-auto"
          >
            <div className="flex items-start justify-between gap-3 mb-3">
              <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide pt-1">AI summary</div>
              {pill}
            </div>
            <p className="text-sm text-gray-500 mb-4">{change.description ?? "No summary for this change."}</p>

            {!change.binary && !explanation && (
              <>
                <button
                  onClick={handleExplain}
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
                <ExplainWalkthrough
                  explanation={explanation}
                  active={activeStep}
                  onActivate={activate}
                  onHoverStep={setHoverStep}
                  stepLabel={(start, end) => stepLabel(rows, start, end)}
                  understood={change.understood}
                  onMarkUnderstood={onToggleUnderstood}
                />
                <p className="text-xs text-gray-400 mt-4 hidden min-[1000px]:block">Tip: use ← and → to move between steps.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
