import { useMemo, useState } from "react";
import { explainChange } from "../api/client";
import type { ChangeResponse, ExplanationData } from "../api/client";
import DiffView from "./DiffView";
import ExplainWalkthrough from "./ExplainWalkthrough";
import { diffRows, stepLabel } from "./diffLines";

interface ChangeCardProps {
  change: ChangeResponse;
  onToggleUnderstood: () => void;
  // Lines to highlight in this change's diff, set while hovering an explain step.
  highlight: { start: number; end: number } | null;
  onHighlight: (start: number, end: number) => void;
  onClearHighlight: () => void;
}

const TYPE_TAG: Record<string, string> = {
  ADDED: "bg-green-50 text-green-700 border-green-200",
  MODIFIED: "bg-blue-50 text-blue-700 border-blue-200",
  DELETED: "bg-red-50 text-red-700 border-red-200",
  RENAMED: "bg-purple-50 text-purple-700 border-purple-200",
};

// One change: file name and type, the understood pill, Explain this, the diff,
// and the explanation once there is one.
export default function ChangeCard({
  change,
  onToggleUnderstood,
  highlight,
  onHighlight,
  onClearHighlight,
}: ChangeCardProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [explanation, setExplanation] = useState<ExplanationData | null>(null);

  const rows = useMemo(
    () => (change.diffContent ? diffRows(change.diffContent, change.oldStart, change.newStart) : []),
    [change.diffContent, change.oldStart, change.newStart]
  );

  async function handleExplain() {
    setLoading(true);
    setError(null);
    try {
      setExplanation(await explainChange(change.id));
    } catch {
      setError("Couldn't explain this change.");
    } finally {
      setLoading(false);
    }
  }

  const plain = change.changeType === "ADDED";
  const fileName = change.oldFilePath ? `${change.oldFilePath} → ${change.filePath}` : change.filePath;

  return (
    <div className="bg-white border border-gray-200 rounded-lg mb-3 overflow-hidden">
      <div className="px-4 py-3 flex gap-3 items-start">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-mono text-sm font-semibold break-all">{fileName}</span>
            <span
              className={`text-[10px] font-semibold tracking-wide px-1.5 py-0.5 rounded border ${
                TYPE_TAG[change.changeType] ?? "bg-gray-50 text-gray-600 border-gray-200"
              }`}
            >
              {change.changeType}
            </span>
          </div>
          <div className="text-sm text-gray-600 mt-1">
            <span className="font-mono text-xs text-gray-400 mr-2">
              Lines {change.newStart}–{change.newStart + Math.max(change.newCount - 1, 0)}
            </span>
            {change.description}
          </div>
        </div>
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
        {!change.binary && !explanation && (
          <button
            onClick={handleExplain}
            disabled={loading}
            className="text-xs font-medium px-3 py-1 rounded-md border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 disabled:text-gray-400 whitespace-nowrap transition-colors"
          >
            {loading ? "Explaining…" : "Explain this"}
          </button>
        )}
      </div>

      {change.binary ? (
        <p className="px-4 pb-3 text-sm text-gray-500">Binary file, no diff to show.</p>
      ) : (
        <DiffView
          rows={rows}
          highlightStart={highlight?.start}
          highlightEnd={highlight?.end}
          plainCode={plain}
        />
      )}

      {(error || explanation) && (
        <div className="px-4 py-3 border-t border-gray-100 text-sm">
          {error && (
            <p className="text-red-600">
              {error}{" "}
              <button onClick={handleExplain} className="text-blue-600 hover:underline">Retry</button>
            </p>
          )}
          {explanation && (
            <ExplainWalkthrough
              explanation={explanation}
              onHighlight={onHighlight}
              onClearHighlight={onClearHighlight}
              stepLabel={(start, end) => stepLabel(rows, start, end)}
            />
          )}
        </div>
      )}
    </div>
  );
}
