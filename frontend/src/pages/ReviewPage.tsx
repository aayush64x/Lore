// Review page — spaced review of changes you already understood.
//
// A change marked understood (or passed in teach-back) comes back after 1 day, then 3,
// 7 and 14. Each review asks one question about it, graded like a teach-back answer:
// pass moves it to the next gap, partly repeats the gap, fail starts again at 1 day.
// The change's one-line summary only shows after answering (it would give the answer
// away); the diff is behind "Show the change" for when you're stuck.
// With demo.time-travel=true in ~/.lore/config.properties, a box at the top can pretend
// days passed, to show reviews coming due.
//
// URL: /review?project=/path/to/project

import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { REVIEW_CHANGED, answerReview, conceptsUrl, fetchReview, moveDemoClock, sessionTitle } from "../api/client";
import type { ReviewItem, ReviewResponse, ReviewResult } from "../api/client";
import DiffView from "../components/DiffView";
import { diffRows } from "../components/diffLines";
import EmptyState, { Command, Loading } from "../components/EmptyState";
import Navbar from "../components/Navbar";

type LoadResult =
  | { project: string; data: ReviewResponse; error: null }
  | { project: string; data: null; error: string };

const VERDICT = {
  pass: { label: "Passed", className: "bg-ok-soft text-ok border-ok/30" },
  partly: { label: "Partly", className: "bg-warn-soft text-warn border-warn/30" },
  fail: { label: "Not yet", className: "bg-bad-soft text-bad border-bad/30" },
};

function comesBack(result: ReviewResult): string {
  if (result.done) return "That was the last review for this change. It won't come back.";
  return `It comes back in ${result.gapDays} ${result.gapDays === 1 ? "day" : "days"}.`;
}

function ReviewCard({ item, question, project }: { item: ReviewItem; question: string; project: string }) {
  const [answer, setAnswer] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ReviewResult | null>(null);

  async function check() {
    setBusy(true);
    setError(null);
    try {
      setResult(await answerReview(item.changeId, answer));
      window.dispatchEvent(new Event(REVIEW_CHANGED));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't check your answer. Try again.");
    } finally {
      setBusy(false);
    }
  }

  const verdict = result ? VERDICT[result.verdict] : null;
  return (
    <li className="rounded-card border border-line bg-surface p-5">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="font-mono text-sm text-ink break-all">{item.filePath}</div>
          <Link to={`/sessions/${item.sessionId}`} className="text-xs text-accent hover:underline">
            {sessionTitle(item.sessionReason)}
          </Link>
        </div>
        <span className="shrink-0 text-xs text-ink-3">{item.gapDays}-day review</span>
      </div>

      {item.concepts.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Concepts">
          {item.concepts.map((c) => (
            <Link
              key={c.name}
              to={conceptsUrl(project, c.other ? "suggested" : c.name)}
              className={`text-xs px-2 py-0.5 rounded-full border ${
                c.other
                  ? "border-dashed border-line-strong text-ink-3 hover:text-ink"
                  : "border-accent/30 bg-accent-soft text-accent hover:bg-accent/15"
              }`}
            >
              {c.other ? `Other: ${c.name}` : c.name}
            </Link>
          ))}
        </div>
      )}

      {item.diffContent && (
        <details className="mt-3 rounded-control border border-line">
          <summary className="cursor-pointer select-none px-3 py-2 text-xs text-ink-2 hover:text-ink">
            Show the change
          </summary>
          <DiffView
            rows={diffRows(item.diffContent, item.oldStart, item.newStart)}
            plainCode={item.changeType === "ADDED"}
          />
        </details>
      )}

      <label className="mt-4 block text-sm font-medium text-ink" htmlFor={`answer-${item.changeId}`}>
        {question}
      </label>
      <textarea
        id={`answer-${item.changeId}`}
        value={answer}
        onChange={(e) => setAnswer(e.target.value)}
        disabled={busy || result !== null}
        rows={3}
        placeholder="Explain it in a sentence or two, without looking first."
        className="mt-1.5 w-full rounded-control border border-line-strong bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-3 focus:border-accent focus:outline-none disabled:bg-sunk"
      />

      {!result && (
        <div className="mt-2 flex items-center gap-3">
          <button
            type="button"
            onClick={check}
            disabled={busy || answer.trim() === ""}
            className="text-sm font-medium px-4 py-2 rounded-control bg-accent text-on-accent hover:bg-accent-hover disabled:opacity-50"
          >
            {busy ? "Checking..." : "Check"}
          </button>
          {error && <span role="alert" className="text-sm text-bad">{error}</span>}
        </div>
      )}

      {result && verdict && (
        <div className="mt-3 space-y-2 text-sm" role="status">
          <span className={`inline-block text-xs font-medium px-2.5 py-1 rounded-full border ${verdict.className}`}>
            {verdict.label}
          </span>
          {result.gotRight.length > 0 && (
            <ul className="text-ink-2">
              {result.gotRight.map((g) => (
                <li key={g}>✓ {g}</li>
              ))}
            </ul>
          )}
          {result.missing.length > 0 && (
            <ul className="list-disc pl-5 text-ink-2">
              {result.missing.map((m) => (
                <li key={m}>Hint: {m}</li>
              ))}
            </ul>
          )}
          <p className="text-ink-3">{comesBack(result)}</p>
          {/* Only now: shown before answering, the summary would give the answer away. */}
          {item.description && (
            <p className="rounded-control bg-sunk px-3 py-2 text-ink-2">
              <span className="font-medium">What it does:</span> {item.description}
            </p>
          )}
        </div>
      )}
    </li>
  );
}

function DemoClock({ daysAhead, onMoved }: { daysAhead: number; onMoved: () => void }) {
  const [days, setDays] = useState(1);
  const [busy, setBusy] = useState(false);

  async function move(to: number | "reset") {
    setBusy(true);
    try {
      await moveDemoClock(to);
      window.dispatchEvent(new Event(REVIEW_CHANGED));
      onMoved();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-4 flex flex-wrap items-center gap-2 rounded-control border border-dashed border-line-strong bg-sunk px-3 py-2 text-sm text-ink-2">
      <span className="font-medium">Demo:</span> pretend
      <select
        value={days}
        onChange={(e) => setDays(Number(e.target.value))}
        className="rounded border border-line-strong bg-surface px-1.5 py-0.5 text-ink"
        aria-label="Days"
      >
        {[1, 3, 7, 14].map((d) => (
          <option key={d} value={d}>
            {d}
          </option>
        ))}
      </select>
      {days === 1 ? "day" : "days"} passed
      <button type="button" onClick={() => move(days)} disabled={busy} className="rounded bg-ink px-2.5 py-0.5 text-paper hover:opacity-90 disabled:opacity-50">
        Go
      </button>
      <button type="button" onClick={() => move("reset")} disabled={busy || daysAhead === 0} className="underline disabled:no-underline disabled:opacity-50">
        Reset
      </button>
      <span className="ml-auto text-xs">
        {daysAhead === 0 ? "Clock is at the real time." : `Clock is ${daysAhead} ${daysAhead === 1 ? "day" : "days"} ahead.`}
      </span>
    </div>
  );
}

export default function ReviewPage() {
  const [result, setResult] = useState<LoadResult | null>(null);
  const [reloads, setReloads] = useState(0);
  const [searchParams] = useSearchParams();
  const project = searchParams.get("project");

  useEffect(() => {
    if (!project) return;
    let ignore = false;
    fetchReview(project)
      .then((data) => {
        if (!ignore) setResult({ project, data, error: null });
      })
      .catch(() => {
        if (!ignore) {
          setResult({ project, data: null, error: "Couldn't load reviews. Make sure lore ui is still running, then refresh." });
        }
      });
    return () => {
      ignore = true;
    };
  }, [project, reloads]);

  const current = project && result?.project === project ? result : null;
  const data = current?.data ?? null;

  return (
    <div className="min-h-screen bg-paper text-ink">
      <Navbar project={project} crumbs={[{ label: "Review" }]} />
      <div className="mx-auto w-full max-w-3xl px-4 sm:px-6 py-8">
        <h1 className="font-serif text-[28px] leading-tight font-semibold tracking-tight text-ink">Review</h1>
        <p className="mt-1 text-sm text-ink-3">
          Changes you understood come back after 1 day, then 3, 7 and 14. Pass to move on; miss it and it comes back
          tomorrow.
        </p>

        {!project && (
          <EmptyState title="No project selected">
            Run <Command>lore ui</Command> from inside your project folder.
          </EmptyState>
        )}
        {project && !current && <Loading>Loading reviews...</Loading>}
        {current?.error && <EmptyState title="Something went wrong" error>{current.error}</EmptyState>}

        {data && project && (
          <>
            {data.demo.enabled && <DemoClock daysAhead={data.demo.daysAhead} onMoved={() => setReloads((n) => n + 1)} />}

            <p className="mt-4 text-sm text-ink-2">
              <span className="font-semibold text-ink">{data.dueCount}</span> due today ·{" "}
              <span className="font-semibold text-ink">{data.upcomingCount}</span> coming up ·{" "}
              <span className="font-semibold text-ink">{data.doneCount}</span> finished
            </p>

            {data.due.length === 0 ? (
              <EmptyState title="Nothing to review today">
                {data.upcomingCount === 0 && "Mark changes understood, and they'll come back here a day later."}
              </EmptyState>
            ) : (
              // Keyed by the clock, so moving it gives fresh cards (no old answers left in them).
              <ul key={`${data.demo.daysAhead}-${reloads}`} className="mt-4 space-y-4">
                {data.due.map((item) => (
                  <ReviewCard key={item.changeId} item={item} question={data.question} project={project} />
                ))}
              </ul>
            )}
          </>
        )}
      </div>
    </div>
  );
}
