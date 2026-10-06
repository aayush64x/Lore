interface DiffViewProps {
  diffContent: string | null;
  highlightStart?: number;
  highlightEnd?: number;
}

export default function DiffView({ diffContent, highlightStart, highlightEnd }: DiffViewProps) {
  if (!diffContent) return null;

  const lines = diffContent.split("\n");

  // TODO: Add the "diff-line-highlighted" CSS class to lines whose 1-based
  //   index falls within highlightStart–highlightEnd (inclusive).
  //
  // WHAT: When the user hovers an explain step, the matching diff lines
  //   should get a blue highlight so their eyes go straight to the code.
  //   Lines are numbered 1-based (the first line of diffContent is line 1).
  //   If highlightStart or highlightEnd is undefined, no lines highlight.
  //
  // INPUTS:
  //   - highlightStart: number | undefined — first line to highlight (1-based)
  //   - highlightEnd: number | undefined — last line to highlight (1-based, inclusive)
  //   - i: the loop index (0-based) from lines.map
  //
  // STEPS:
  //   1. Figure out whether the current line (at loop index i) falls in
  //      the highlight range. Remember: i is 0-based, the range is 1-based.
  //   2. If it does, append "diff-line-highlighted" to the className string.
  //
  // READ: MDN > CSS > outline (to understand the highlight style in index.css)
  //
  // HOW TO CHECK: In the browser, click "Explain this" on a change, hover
  //   a step, and confirm the matching diff lines get a blue outline.
  //   Check edge cases: first line, last line, single-line step.

  return (
    <pre className="font-mono text-xs leading-relaxed overflow-x-auto">
      {lines.map((line, i) => {
        let className = "px-4 py-px whitespace-pre ";
        if (line.startsWith("@@")) {
          className += "bg-gray-100 text-gray-500";
        } else if (line.startsWith("+")) {
          className += "bg-green-50 text-green-800";
        } else if (line.startsWith("-")) {
          className += "bg-red-50 text-red-800";
        }
        return (
          <div key={i} className={className}>
            {line}
          </div>
        );
      })}
    </pre>
  );
}
