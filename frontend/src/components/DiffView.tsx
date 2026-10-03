interface DiffViewProps {
  diffContent: string | null;
}

export default function DiffView({ diffContent }: DiffViewProps) {
  if (!diffContent) return null;

  const lines = diffContent.split("\n");

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
