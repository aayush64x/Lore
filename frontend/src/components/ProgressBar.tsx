interface ProgressBarProps {
  understood: number;
  total: number;
}

export default function ProgressBar({ understood, total }: ProgressBarProps) {
  const percent = total === 0 ? 0 : Math.round((understood / total) * 100);

  return (
    <div className="flex items-center gap-3">
      <div className="flex-1 h-1.5 bg-line rounded-full overflow-hidden">
        <div
          className="h-full bg-ok rounded-full transition-all duration-300"
          style={{ width: `${percent}%` }}
        ></div>
      </div>
      <span className="text-xs text-ink-3 whitespace-nowrap">
        <span className="font-semibold text-ink">{understood} of {total}</span> understood
      </span>
    </div>
  );
}
