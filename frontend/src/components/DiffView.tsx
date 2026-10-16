interface DiffViewProps {
  diffContent: string | null;
  // 1-based, inclusive. Same numbering the AI uses for explain steps
  // (line 1 is the first line of diffContent, including @@ headers).
  highlightStart?: number;
  highlightEnd?: number;
}

export default function DiffView({ diffContent, highlightStart, highlightEnd }: DiffViewProps) {
  if (!diffContent) return null;

  const lines = diffContent.split("\n");

  function isHighlighted(i: number) {
    if (highlightStart === undefined || highlightEnd === undefined) return false;
    const lineNumber = i + 1;
    return lineNumber >= highlightStart && lineNumber <= highlightEnd;
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
