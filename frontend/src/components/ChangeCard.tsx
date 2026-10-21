import type { ChangeResponse } from "../api/client";
import DiffView from "./DiffView";
import ExplainWalkthrough from "./ExplainWalkthrough";

interface ChangeCardProps {
  change: ChangeResponse;
  onToggleUnderstood: () => void;
  // Lines to highlight in this change's diff, set while hovering an explain step.
  highlight: { start: number; end: number } | null;
  onHighlight: (start: number, end: number) => void;
  onClearHighlight: () => void;
}

// One change: its line range, the understood button, the diff, and Explain this.
export default function ChangeCard({
  change,
  onToggleUnderstood,
  highlight,
  onHighlight,
  onClearHighlight,
}: ChangeCardProps) {
  return (
    <div className="bg-white border border-gray-200 rounded-lg mb-2 overflow-hidden">
      <div className="px-4 py-3 flex gap-3 items-center text-sm">
        <span className="text-gray-500 font-mono text-xs">
          Lines {change.newStart}–{change.newStart + Math.max(change.newCount - 1, 0)}
        </span>
        <span className="flex-1">{change.description ?? change.changeType}</span>
        <button
          onClick={onToggleUnderstood}
          className={`text-xs px-2 py-1 rounded ${
            change.understood
              ? "bg-green-100 text-green-700 hover:bg-green-200"
              : "bg-gray-100 text-gray-500 hover:bg-gray-200"
          } transition-colors`}
        >
          {change.understood ? "Understood" : "Not understood"}
        </button>
      </div>
      <DiffView
        diffContent={change.diffContent}
        highlightStart={highlight?.start}
        highlightEnd={highlight?.end}
      />
      {!change.binary && (
        <div className="px-4 py-3 border-t border-gray-100 text-sm">
          <ExplainWalkthrough
            changeId={change.id}
            onHighlight={onHighlight}
            onClearHighlight={onClearHighlight}
          />
        </div>
      )}
    </div>
  );
}
