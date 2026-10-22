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
import Navbar from "../components/Navbar";

type LoadResult =
  | { project: string; data: ReviewResponse; error: null }
  | { project: string; data: null; error: string };

const VERDICT = {
  pass: { label: "Passed", className: "bg-green-50 text-green-700 border-green-300" },
  partly: { label: "Partly", className: "bg-amber-50 text-amber-800 border-amber-300" },
  fail: { label: "Not yet", className: "bg-red-50 text-red-700 border-red-200" },
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
    <li className="rounded-lg border border-gray-200 bg-white p-5">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="font-mono text-sm text-gray-900 break-all">{item.filePath}</div>
          <Link to={`/sessions/${item.sessionId}`} className="text-xs text-blue-600 hover:underline">
            {sessionTitle(item.sessionReason)}
          </Link>
        </div>
        <span className="shrink-0 text-xs text-gray-500">{item.gapDays}-day review</span>
      </div>

      {item.concepts.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Concepts">
          {item.concepts.map((c) => (
            <Link
              key={c.name}
              to={conceptsUrl(project, c.other ? "suggested" : c.name)}
              className={`text-xs px-2 py-0.5 rounded-full border ${
                c.other
                  ? "border-dashed border-gray-300 text-gray-500 hover:text-gray-800"
                  : "border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100"
              }`}
            >
              {c.other ? `Other: ${c.name}` : c.name}
            </Link>
          ))}
        </div>
      )}

      {item.diffContent && (
        <details className="mt-3 rounded-md border border-gray-200">
          <summary className="cursor-pointer select-none px-3 py-2 text-xs text-gray-600 hover:text-gray-900">
            Show the change
          </summary>
          <DiffView
            rows={diffRows(item.diffContent, item.oldStart, item.newStart)}
            plainCode={item.changeType === "ADDED"}
          />
        </details>
      )}

      <label className="mt-4 block text-sm font-medium text-gray-900" htmlFor={`answer-${item.changeId}`}>
        {question}
      </label>
      <textarea
        id={`answer-${item.changeId}`}
        value={answer}
        onChange={(e) => setAnswer(e.target.value)}
        disabled={busy || result !== null}
        rows={3}
        placeholder="Explain it in a sentence or two, without looking first."
        className="mt-1.5 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400 focus:border-blue-500 focus:outline-none disabled:bg-gray-50"
      />

      {!result && (
        <div className="mt-2 flex items-center gap-3">
          <button
            type="button"
            onClick={check}
            disabled={busy || answer.trim() === ""}
            className="text-sm font-medium px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {busy ? "Checking..." : "Check"}
          </button>
          {error && <span role="alert" className="text-sm text-red-600">{error}</span>}
        </div>
      )}

      {result && verdict && (
        <div className="mt-3 space-y-2 text-sm" role="status">
          <span className={`inline-block text-xs font-medium px-2.5 py-1 rounded-full border ${verdict.className}`}>
            {verdict.label}
          </span>
          {result.gotRight.length > 0 && (
            <ul className="text-gray-700">
              {result.gotRight.map((g) => (
                <li key={g}>✓ {g}</li>
              ))}
            </ul>
          )}
          {result.missing.length > 0 && (
            <ul className="list-disc pl-5 text-gray-600">
              {result.missing.map((m) => (
                <li key={m}>Hint: {m}</li>
              ))}
            </ul>
          )}
          <p className="text-gray-500">{comesBack(result)}</p>
          {/* Only now: shown before answering, the summary would give the answer away. */}
          {item.description && (
            <p className="rounded-md bg-gray-50 px-3 py-2 text-gray-700">
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
    <div className="mt-4 flex flex-wrap items-center gap-2 rounded-md border border-dashed border-purple-300 bg-purple-50 px-3 py-2 text-sm text-purple-700">
      <span className="font-medium">Demo:</span> pretend
      <select
        value={days}
        onChange={(e) => setDays(Number(e.target.value))}
        className="rounded border border-purple-300 bg-white px-1.5 py-0.5 text-gray-900"
        aria-label="Days"
      >
        {[1, 3, 7, 14].map((d) => (
          <option key={d} value={d}>
            {d}
          </option>
        ))}
      </select>
      {days === 1 ? "day" : "days"} passed
      <button type="button" onClick={() => move(days)} disabled={busy} className="rounded bg-purple-700 px-2.5 py-0.5 text-white hover:opacity-90 disabled:opacity-50">
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
    <>
      <Navbar project={project} crumbs={[{ label: "Review" }]} />
      <div className="px-6 py-6 max-w-3xl">
        <h1 className="text-xl font-semibold">Review</h1>
        <p className="mt-1 text-sm text-gray-500">
          Changes you understood come back after 1 day, then 3, 7 and 14. Pass to move on; miss it and it comes back
          tomorrow.
        </p>

        {!project && <p className="text-gray-500 text-center py-12">No project selected. Run lore ui from inside your project folder.</p>}
        {project && !current && <p className="text-gray-500 text-center py-12">Loading reviews...</p>}
        {current?.error && <p className="text-red-600 text-center py-12">{current.error}</p>}

        {data && project && (
          <>
            {data.demo.enabled && <DemoClock daysAhead={data.demo.daysAhead} onMoved={() => setReloads((n) => n + 1)} />}

            <p className="mt-4 text-sm text-gray-700">
              <span className="font-semibold text-gray-900">{data.dueCount}</span> due today ·{" "}
              <span className="font-semibold text-gray-900">{data.upcomingCount}</span> coming up ·{" "}
              <span className="font-semibold text-gray-900">{data.doneCount}</span> finished
            </p>

            {data.due.length === 0 ? (
              <p className="text-gray-500 text-center py-12">
                Nothing to review today.
                {data.upcomingCount === 0 && " Mark changes understood, and they'll come back here a day later."}
              </p>
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
    </>
  );
}
