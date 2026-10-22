// "AI cost ~$0.02" in the session header. Clicking it opens a small panel with what
// each job cost; "Show details" adds tokens in/out and the model names. Closes on a
// click outside, Escape, or scrolling.
//
// The panel is placed against the window (position: fixed), not inside the header: the
// header sits in a box that hides overflow (so it can slide away), which cut it off.

import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import type { SessionCost } from "../api/client";

// "~$0.02", "<$0.01", or "~12,345 tokens" when a model had no price.
function money(dollars: number | null, tokens: number): string {
  if (dollars === null) return `~${tokens.toLocaleString()} tokens`;
  return dollars < 0.01 ? "<$0.01" : `~$${dollars.toFixed(2)}`;
}

type Job = SessionCost["jobs"][number];

function jobName(j: Job): string {
  if (j.job === "summary") return "Summary";
  if (j.job === "explain") return `Explain (${j.calls} ${j.calls === 1 ? "call" : "calls"})`;
  if (j.job === "follow_up") return `Follow-up questions (${j.calls})`;
  return "Teach-back grading";
}

export default function CostButton({ cost }: { cost: SessionCost }) {
  const [open, setOpen] = useState(false);
  const [details, setDetails] = useState(false);
  const box = useRef<HTMLSpanElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const [place, setPlace] = useState<CSSProperties>({});

  // Just under the button, kept 8px inside the window's edges.
  function placePanel() {
    const r = button.current?.getBoundingClientRect();
    if (!r) return;
    const width = Math.min(320, window.innerWidth - 16);
    setPlace({ top: r.bottom + 8, left: Math.max(8, Math.min(r.left, window.innerWidth - width - 8)), width });
  }

  useEffect(() => {
    if (!open) return;
    function onMouseDown(e: MouseEvent) {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    // Any scroll (the page or a column) moves the button, and may hide the header: close.
    function onScroll() {
      setOpen(false);
    }
    document.addEventListener("mousedown", onMouseDown);
    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("scroll", onScroll, true);
    window.addEventListener("resize", placePanel);
    return () => {
      document.removeEventListener("mousedown", onMouseDown);
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("scroll", onScroll, true);
      window.removeEventListener("resize", placePanel);
    };
  }, [open]);

  return (
    <span ref={box} className="relative inline-block">
      <button
        ref={button}
        type="button"
        onClick={() => {
          if (!open) placePanel();
          setOpen(!open);
        }}
        aria-expanded={open}
        aria-haspopup="dialog"
        className="text-ink-3 hover:text-ink underline decoration-dotted underline-offset-2"
      >
        AI cost {money(cost.dollars, cost.tokens)}
      </button>
      {open && (
        <div
          role="dialog"
          aria-label="AI cost"
          style={place}
          className="fixed z-30 rounded-card border border-line bg-surface shadow-lg p-3 text-sm text-ink-2"
        >
          <ul className="space-y-2">
            {cost.jobs.map((j) => (
              <li key={j.job}>
                <div className="flex justify-between gap-3">
                  <span>{jobName(j)}</span>
                  <span className="font-medium text-ink tabular-nums">
                    {j.calls === 0 ? "none yet" : money(j.dollars, j.tokens)}
                  </span>
                </div>
                {details && j.calls > 0 && (
                  <div className="mt-0.5 text-xs text-ink-3">
                    {j.tokensIn.toLocaleString()} tokens in · {j.tokensOut.toLocaleString()} out ·{" "}
                    <span className="font-mono">{j.models.join(", ")}</span>
                  </div>
                )}
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => setDetails(!details)}
            aria-expanded={details}
            className="mt-3 text-xs text-accent hover:underline"
          >
            {details ? "Hide details" : "Show details"}
          </button>
          <p className="mt-2 pt-2 border-t border-line text-xs text-ink-3">
            Estimate. Charged to your own API key.
          </p>
        </div>
      )}
    </span>
  );
}
