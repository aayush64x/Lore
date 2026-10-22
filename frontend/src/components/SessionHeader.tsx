import { Fragment, useState } from "react";
import type { ReactNode } from "react";
import { sessionDetails, sessionTitle } from "../api/client";
import type { SessionDetailResponse } from "../api/client";
import CostButton from "./CostButton";

interface SessionHeaderProps {
  session: SessionDetailResponse;
  fileCount: number;
  added: number;
  removed: number;
  understood: number;
  total: number;
  onStartLearning: () => void;
  onRetrySummary: () => void;
  retrying: boolean;
  retryError: string | null;
  onDelete: () => void;
  deleting: boolean;
  deleteError: string | null;
}

// "42 min", "1 h 5 min", "under 1 min". Null while the session is still running.
function duration(startedAt: string, stoppedAt: string | null): string | null {
  if (!stoppedAt) return null;
  const minutes = Math.round((new Date(stoppedAt).getTime() - new Date(startedAt).getTime()) / 60000);
  if (minutes < 1) return "under 1 min";
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

// Top of the session page: what you asked for, the numbers, the AI summary,
// and the button that walks you through what's left.
export default function SessionHeader({
  session,
  fileCount,
  added,
  removed,
  understood,
  total,
  onStartLearning,
  onRetrySummary,
  retrying,
  retryError,
  onDelete,
  deleting,
  deleteError,
}: SessionHeaderProps) {
  const [summaryOpen, setSummaryOpen] = useState(true);
  const details = sessionDetails(session.reason);
  const time = duration(session.startedAt, session.stoppedAt);
  const allDone = total > 0 && understood === total;
  const running = session.status === "ACTIVE";

  const stats: ReactNode[] = [
    time,
    `${fileCount} ${fileCount === 1 ? "file" : "files"}`,
    <span className="font-mono text-xs">
      <span className="text-ok">+{added}</span> <span className="text-bad">−{removed}</span>
    </span>,
    <span>
      <span className="font-semibold text-ink">{understood} of {total}</span> understood
    </span>,
    // Hidden for older sessions (no cost saved) and when the provider isn't the Anthropic API.
    session.cost && <CostButton cost={session.cost} />,
  ].filter(Boolean);

  return (
    <header className="px-6 py-4 border-b border-line bg-surface">
      <div className="flex items-start gap-6 flex-wrap">
        <div className="flex-1 min-w-[280px]">
          <div className="text-[13px] font-medium text-ink-2">Goal</div>
          <h1 className="mt-0.5 font-serif text-[26px] leading-tight font-semibold tracking-tight text-ink break-words">{sessionTitle(session.reason)}</h1>
          <div className="mt-1 text-sm text-ink-3 flex flex-wrap items-center gap-x-2">
            {stats.map((s, i) => (
              <Fragment key={i}>
                {i > 0 && <span className="text-line-strong" aria-hidden="true">·</span>}
                {s}
              </Fragment>
            ))}
          </div>
        </div>
        {/* Side by side on a phone, stacked on the right on wider screens. */}
        <div className="shrink-0 pt-3 flex items-center gap-4 sm:flex-col sm:items-end sm:gap-2">
          {allDone ? (
            <span className="inline-block text-sm font-medium px-4 py-2 rounded-control bg-ok-soft text-ok border border-ok/30">
              All understood 🎉
            </span>
          ) : (
            <button
              onClick={onStartLearning}
              className="text-sm font-medium px-4 py-2 rounded-control bg-accent text-on-accent hover:bg-accent-hover transition-colors"
            >
              Start learning →
            </button>
          )}
          <div className="flex items-center gap-2 text-xs">
            {running && <span className="text-warn">Running</span>}
            <button
              type="button"
              onClick={onDelete}
              disabled={running || deleting}
              title={running ? "Stop this session first with lore stop." : "Delete this session"}
              className="text-bad hover:underline disabled:text-ink-3 disabled:no-underline disabled:cursor-not-allowed"
            >
              {deleting ? "Deleting..." : "Delete session"}
            </button>
          </div>
          {deleteError && <span role="alert" className="text-xs text-bad max-w-xs text-right">{deleteError}</span>}
        </div>
      </div>

      {session.goalGaps && session.goalGaps.length > 0 && (
        <div role="note" className="mt-3 max-w-4xl px-3 py-2 rounded-control border border-warn/30 bg-warn-soft text-sm text-warn">
          <span className="font-medium">Not done yet from your goal:</span>
          <ul className="list-disc pl-5 mt-1">
            {session.goalGaps.map((gap, i) => (
              <li key={i}>{gap}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-3">
        <button
          onClick={() => setSummaryOpen(!summaryOpen)}
          aria-expanded={summaryOpen}
          className="text-[13px] font-medium text-ink-2 hover:text-ink"
        >
          <span className="inline-block w-3">{summaryOpen ? "▾" : "▸"}</span> AI summary
        </button>
        {summaryOpen && (
          <div className="mt-1 pl-3 max-w-4xl text-sm text-ink-3 space-y-2">
            {details && <p className="whitespace-pre-wrap break-words text-ink-2">{details}</p>}
            {session.summary ? (
              <p>{session.summary}</p>
            ) : (
              <p>
                {session.summaryStatus === "FAILED"
                  ? "The AI summary couldn't be created for this session."
                  : "No AI summary for this session yet."}{" "}
                <button
                  onClick={onRetrySummary}
                  disabled={retrying}
                  className="text-accent hover:underline disabled:text-ink-3 disabled:no-underline"
                >
                  {retrying ? "Creating summary..." : "Retry"}
                </button>
                {retryError && <span className="block text-bad mt-1">{retryError}</span>}
              </p>
            )}
          </div>
        )}
      </div>
    </header>
  );
}
