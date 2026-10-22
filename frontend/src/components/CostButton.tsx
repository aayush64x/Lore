// "AI cost ~$0.02" in the session header. Clicking it opens a small panel with what
// each job cost; "Show details" adds tokens in/out and the model names. Closes on a
// click outside or Escape.

import { useEffect, useRef, useState } from "react";
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
  return "Teach-back grading";
}

export default function CostButton({ cost }: { cost: SessionCost }) {
  const [open, setOpen] = useState(false);
  const [details, setDetails] = useState(false);
  const box = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!open) return;
    function onMouseDown(e: MouseEvent) {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onMouseDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onMouseDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <span ref={box} className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        aria-haspopup="dialog"
        className="text-gray-500 hover:text-gray-900 underline decoration-dotted underline-offset-2"
      >
        AI cost {money(cost.dollars, cost.tokens)}
      </button>
      {open && (
        <div
          role="dialog"
          aria-label="AI cost"
          className="absolute left-0 top-full mt-2 z-30 w-80 rounded-lg border border-gray-200 bg-white shadow-lg p-3 text-sm text-gray-700"
        >
          <ul className="space-y-2">
            {cost.jobs.map((j) => (
              <li key={j.job}>
                <div className="flex justify-between gap-3">
                  <span>{jobName(j)}</span>
                  <span className="font-medium text-gray-900 tabular-nums">
                    {j.calls === 0 ? "none yet" : money(j.dollars, j.tokens)}
                  </span>
                </div>
                {details && j.calls > 0 && (
                  <div className="mt-0.5 text-xs text-gray-500">
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
            className="mt-3 text-xs text-blue-600 hover:underline"
          >
            {details ? "Hide details" : "Show details"}
          </button>
          <p className="mt-2 pt-2 border-t border-gray-100 text-xs text-gray-500">
            Estimate. Charged to your own API key.
          </p>
        </div>
      )}
    </span>
  );
}
