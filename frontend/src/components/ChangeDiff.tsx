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
  ADDED: "bg-ok-soft text-ok border-ok/30",
  MODIFIED: "bg-accent-soft text-accent border-accent/30",
  DELETED: "bg-bad-soft text-bad border-bad/30",
  RENAMED: "bg-sunk text-ink-2 border-line",
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
      className={`bg-surface border rounded-card mb-4 overflow-hidden ${
        active ? "border-accent/50 ring-1 ring-accent/20" : "border-line cursor-pointer hover:border-line-strong"
      }`}
    >
      <div className="px-4 py-2.5 flex items-center gap-2 flex-wrap border-b border-line">
        <span className="font-mono text-sm font-semibold break-all">{fileName}</span>
        <span
          className={`text-[10px] font-semibold tracking-wide px-1.5 py-0.5 rounded border ${
            TYPE_TAG[change.changeType] ?? "bg-sunk text-ink-2 border-line"
          }`}
        >
          {change.changeType}
        </span>
        {change.goalMatch === "no" && (
          <span
            title={change.goalReason ?? undefined}
            className="text-[11px] font-medium px-1.5 py-0.5 rounded border bg-warn-soft text-warn border-warn/30"
          >
            Not part of your goal
          </span>
        )}
        {change.goalMatch === "partly" && (
          <span
            title={change.goalReason ?? undefined}
            className="text-[11px] px-1.5 py-0.5 rounded border bg-sunk text-ink-2 border-line"
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
            className="text-[11px] font-medium px-1.5 py-0.5 rounded border bg-bad-soft text-bad border-bad/30 hover:bg-bad/15"
          >
            ⚠ Lore found an issue
          </button>
        )}
        <span className="font-mono text-xs text-ink-3">
          Lines {change.newStart}–{change.newStart + Math.max(change.newCount - 1, 0)}
        </span>
      </div>
      {issue && !onLine && issueOpen && (
        <p className="px-4 py-2 text-xs text-bad bg-bad-soft border-b border-bad/30">{issue.text}</p>
      )}
      {change.binary ? (
        <p className="px-4 py-3 text-sm text-ink-3">Binary file, no diff to show.</p>
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
