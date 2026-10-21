import type { ChangeResponse } from "../api/client";
import DiffView from "./DiffView";
import type { DiffRow } from "./diffLines";

interface ChangeDiffProps {
  change: ChangeResponse;
  rows: DiffRow[];
  // The change shown in the explain panel. Only it gets a highlight.
  active: boolean;
  highlight: { start: number; end: number } | null;
  onSelect: () => void;
}

const TYPE_TAG: Record<string, string> = {
  ADDED: "bg-green-50 text-green-700 border-green-200",
  MODIFIED: "bg-blue-50 text-blue-700 border-blue-200",
  DELETED: "bg-red-50 text-red-700 border-red-200",
  RENAMED: "bg-purple-50 text-purple-700 border-purple-200",
};

// One change in the diff box: a slim header (file, type, lines, status) and its diff.
// Clicking it shows it in the explain panel.
export default function ChangeDiff({ change, rows, active, highlight, onSelect }: ChangeDiffProps) {
  const fileName = change.oldFilePath ? `${change.oldFilePath} → ${change.filePath}` : change.filePath;
  return (
    <section
      data-change={change.id}
      onClick={onSelect}
      className={`bg-white border rounded-lg mb-4 overflow-hidden ${
        active ? "border-blue-300 ring-1 ring-blue-100" : "border-gray-200 cursor-pointer hover:border-gray-300"
      }`}
    >
      <div className="px-4 py-2.5 flex items-center gap-2 flex-wrap border-b border-gray-100">
        <span className="font-mono text-sm font-semibold break-all">{fileName}</span>
        <span
          className={`text-[10px] font-semibold tracking-wide px-1.5 py-0.5 rounded border ${
            TYPE_TAG[change.changeType] ?? "bg-gray-50 text-gray-600 border-gray-200"
          }`}
        >
          {change.changeType}
        </span>
        {change.goalMatch === "no" && (
          <span
            title={change.goalReason ?? undefined}
            className="text-[11px] font-medium px-1.5 py-0.5 rounded border bg-amber-50 text-amber-800 border-amber-300"
          >
            Not part of your goal
          </span>
        )}
        {change.goalMatch === "partly" && (
          <span
            title={change.goalReason ?? undefined}
            className="text-[11px] px-1.5 py-0.5 rounded border bg-gray-50 text-gray-600 border-gray-200"
          >
            Partly your goal
          </span>
        )}
        <span className="font-mono text-xs text-gray-400">
          Lines {change.newStart}–{change.newStart + Math.max(change.newCount - 1, 0)}
        </span>
        <span className="flex-1" />
        <span className={`text-xs font-medium ${change.understood ? "text-green-700" : "text-orange-700"}`}>
          {change.understood ? "✓ Understood" : "Not understood yet"}
        </span>
      </div>
      {change.binary ? (
        <p className="px-4 py-3 text-sm text-gray-500">Binary file, no diff to show.</p>
      ) : (
        <DiffView
          rows={rows}
          highlightStart={highlight?.start}
          highlightEnd={highlight?.end}
          plainCode={change.changeType === "ADDED"}
        />
      )}
    </section>
  );
}
