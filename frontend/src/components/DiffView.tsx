interface DiffViewProps {
  diffContent: string | null;
}

export default function DiffView({ diffContent }: DiffViewProps) {
  if (!diffContent) return null;

  const lines = diffContent.split("\n");

  return (
    <pre>
      {lines.map((line, i) => {
        if (line.startsWith("+")) {
          return (
            <div key={i} className="bg-green-50 text-green-800">
              {line}
            </div>
          );
        } else if (line.startsWith("-")) {
          return (
            <div key={i} className="bg-red-50 text-red-800">
              {line}
            </div>
          );
        } else if (line.startsWith("@@")) {
          return (
            <div key={i} className="bg-gray-100 text-gray-500">
              {line}
            </div>
          );
        } else {
          return <div key={i}>{line}</div>;
        }
      })}
    </pre>
  );
}
