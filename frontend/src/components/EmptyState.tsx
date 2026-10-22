// What a page shows instead of its content: nothing there yet, no project, still loading,
// or an error. One look on every page: a short serif title and a sentence in a soft card;
// errors are tinted red and have a Refresh button (their messages say "then refresh").

import type { ReactNode } from "react";

export function Command({ children }: { children: ReactNode }) {
  return <code className="bg-sunk px-1.5 py-0.5 rounded text-sm font-mono text-ink">{children}</code>;
}

export function Loading({ children }: { children: ReactNode }) {
  return (
    <p role="status" className="py-12 text-center text-sm text-ink-3">
      {children}
    </p>
  );
}

export default function EmptyState({
  title,
  children,
  error = false,
}: {
  title: string;
  children?: ReactNode;
  error?: boolean;
}) {
  return (
    <div
      role={error ? "alert" : undefined}
      className={`mt-6 rounded-card border px-6 py-10 text-center ${
        error ? "border-bad/30 bg-bad-soft" : "border-dashed border-line-strong bg-surface/60"
      }`}
    >
      <h2 className={`font-serif text-lg font-semibold ${error ? "text-bad" : "text-ink"}`}>{title}</h2>
      {children && <p className="mx-auto mt-2 max-w-md text-[15px] leading-relaxed text-ink-2">{children}</p>}
      {error && (
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="mt-4 text-sm font-medium px-3.5 py-1.5 rounded-control border border-line-strong bg-surface text-ink transition-colors duration-150 hover:bg-sunk"
        >
          Refresh
        </button>
      )}
    </div>
  );
}
