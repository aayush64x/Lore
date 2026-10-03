interface ProgressBarProps {
  understood: number;
  total: number;
}

export default function ProgressBar({ understood, total }: ProgressBarProps) {
  const percent = total === 0 ? 0 : Math.round((understood / total) * 100);

  return (
    <div className="flex items-center gap-3">
      <div className="flex-1 h-1.5 bg-gray-200 rounded-full overflow-hidden">
        <div
          className="h-full bg-blue-600 rounded-full transition-all duration-300"
          style={{ width: `${percent}%` }}
        ></div>
      </div>
      <span className="text-xs text-gray-500 whitespace-nowrap">
        {understood} of {total} understood
      </span>
    </div>
  );
}
