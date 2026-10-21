interface DiffViewProps {
  diffContent: string | null;
  // 1-based, inclusive. Same numbering the AI uses for explain steps
  // (line 1 is the first line of diffContent, including @@ headers).
  highlightStart?: number;
  highlightEnd?: number;
  // New files: show plain code with line numbers instead of "+" on every line.
  // newStart is the file line number of the first code line.
  plainCode?: { newStart: number };
}

export default function DiffView({ diffContent, highlightStart, highlightEnd, plainCode }: DiffViewProps) {
  if (!diffContent) return null;

  // Drop the empty string after the final newline so it isn't drawn as a line.
  const lines = diffContent.replace(/\n$/, "").split("\n");

  function isHighlighted(i: number) {
    if (highlightStart === undefined || highlightEnd === undefined) return false;
    const lineNumber = i + 1;
    return lineNumber >= highlightStart && lineNumber <= highlightEnd;
  }

  if (plainCode) {
    // Keep each line's index i (the @@ header is index 0, so it's still "line 1"
    // for explain steps), but hide the header and the "\ No newline" marker.
    return (
      <pre className="overflow-x-auto text-xs leading-5 py-2">
        {lines.map((line, i) => {
          if (line.startsWith("@@") || line.startsWith("\\")) return null;
          return (
            <div key={i} className={`flex ${isHighlighted(i) ? "diff-line-highlighted" : ""}`}>
              <span className="select-none text-gray-400 text-right w-12 pr-3 shrink-0">
                {plainCode.newStart + i - 1}
              </span>
              <span>{line.slice(1) || " "}</span>
            </div>
          );
        })}
      </pre>
    );
  }

  return (
    <pre className="overflow-x-auto text-xs leading-5 py-2">
      {lines.map((line, i) => {
        let className = "";
        if (line.startsWith("+")) {
          className = "bg-green-50 text-green-800";
        } else if (line.startsWith("-")) {
          className = "bg-red-50 text-red-800";
        } else if (line.startsWith("@@")) {
          className = "bg-gray-100 text-gray-500";
        }
        if (isHighlighted(i)) {
          className += " diff-line-highlighted";
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
