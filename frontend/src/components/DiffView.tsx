import type { DiffRow } from "./diffLines";

interface DiffViewProps {
  rows: DiffRow[];
  // 1-based, inclusive. Same numbering the AI uses for explain steps
  // (line 1 is the first line of diffContent, including @@ headers).
  highlightStart?: number;
  highlightEnd?: number;
  // New files: show plain code with one line number column instead of old | new.
  plainCode?: boolean;
}

const ROW_CLASS: Record<DiffRow["kind"], string> = {
  added: "diff-row-added bg-green-50 text-green-900",
  removed: "diff-row-removed bg-red-50 text-red-900",
  header: "bg-gray-100 text-gray-500",
  context: "",
  note: "text-gray-400",
};

function Gutter({ value }: { value: number | null }) {
  return (
    <span className="diff-gutter select-none text-gray-400 bg-gray-50 text-right w-12 px-2 shrink-0 border-r border-gray-200">
      {value ?? ""}
    </span>
  );
}

export default function DiffView({ rows, highlightStart, highlightEnd, plainCode }: DiffViewProps) {
  function isHighlighted(i: number) {
    if (highlightStart === undefined || highlightEnd === undefined) return false;
    const lineNumber = i + 1;
    return lineNumber >= highlightStart && lineNumber <= highlightEnd;
  }

  // The inner div is as wide as the longest line (or the box, if wider), so row
  // colors and the highlight fill the whole row even when it scrolls sideways.
  return (
    <pre className="overflow-x-auto text-xs leading-5 border-t border-gray-100">
      <div className="min-w-full w-max">
        {rows.map((row, i) => {
          // Plain code keeps each row's index i (the @@ header is still "line 1"
          // for explain steps), but hides the header and the "\ No newline" marker.
          if (plainCode && (row.kind === "header" || row.kind === "note")) return null;
          const highlighted = isHighlighted(i) ? " diff-line-highlighted" : "";
          const kind = plainCode ? "" : ROW_CLASS[row.kind];
          return (
            <div key={i} className={`flex ${kind}${highlighted}`}>
              {!plainCode && <Gutter value={row.oldLine} />}
              <Gutter value={row.newLine} />
              <span className="pl-3 pr-4">{(row.kind === "header" ? row.text : row.text.slice(1)) || " "}</span>
            </div>
          );
        })}
      </div>
    </pre>
  );
}
