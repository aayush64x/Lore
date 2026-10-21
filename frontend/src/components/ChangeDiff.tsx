import { useState } from "react";
import type { ChangeResponse, PossibleIssue } from "../api/client";
import DiffView from "./DiffView";
import type { DiffRow } from "./diffLines";

interface ChangeDiffProps {
  change: ChangeResponse;
  rows: DiffRow[];
  // The change shown in the explain panel. Only it gets a highlight.
  active: boolean;
  highlight: { start: number; end: number } | null;
  onSelect: () => void;
  // From this change's explanation: a real edge case or bug, or null.
  issue: PossibleIssue | null;
}

const TYPE_TAG: Record<string, string> = {
  ADDED: "bg-green-50 text-green-700 border-green-200",
  MODIFIED: "bg-blue-50 text-blue-700 border-blue-200",
  DELETED: "bg-red-50 text-red-700 border-red-200",
  RENAMED: "bg-purple-50 text-purple-700 border-purple-200",
};

// One change in the diff box: a slim header (file, type, badges, lines) and its diff.
// Status isn't repeated here: it's in the panel, the sidebar dot and the top bar.
// Clicking it shows it in the explain panel.
export default function ChangeDiff({ change, rows, active, highlight, onSelect, issue }: ChangeDiffProps) {
  const [issueOpen, setIssueOpen] = useState(false);
  // On a drawn line the badge sits on that line; otherwise (no line, or a hidden
  // header row in plain code) it sits in this header.
  const plain = change.changeType === "ADDED";
  const row = issue && issue.diff_line >= 1 ? rows.at(issue.diff_line - 1) : undefined;
  const onLine = !!row && !(plain && (row.kind === "header" || row.kind === "note"));
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
        {issue && !onLine && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              setIssueOpen(!issueOpen);
            }}
            aria-expanded={issueOpen}
            className="text-[11px] font-medium px-1.5 py-0.5 rounded border bg-red-50 text-red-700 border-red-300 hover:bg-red-100"
          >
            ⚠ Lore found an issue
          </button>
        )}
        <span className="font-mono text-xs text-gray-400">
          Lines {change.newStart}–{change.newStart + Math.max(change.newCount - 1, 0)}
        </span>
      </div>
      {issue && !onLine && issueOpen && (
        <p className="px-4 py-2 text-xs text-red-900 bg-red-50 border-b border-red-200">{issue.text}</p>
      )}
      {change.binary ? (
        <p className="px-4 py-3 text-sm text-gray-500">Binary file, no diff to show.</p>
      ) : (
        <DiffView
          rows={rows}
          highlightStart={highlight?.start}
          highlightEnd={highlight?.end}
          plainCode={plain}
          issue={issue && onLine ? { line: issue.diff_line, text: issue.text } : null}
        />
      )}
    </section>
  );
}
