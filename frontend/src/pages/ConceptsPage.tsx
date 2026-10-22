// Concepts page — what this project's sessions taught, across all of them.
//
// Every change is tagged with 1-3 concepts from concepts.txt when its session is stopped.
// For each concept: how many changes used it ("seen"), how many of those you understood
// (marked by hand or passed in teach-back), in how many sessions, and when last.
// Concepts are grouped under their heading in concepts.txt; groups that come up most go
// first. "Other" suggestions (nothing on the list fit) are shown apart, to review.
//
// URL: /concepts?project=/path/to/project, optionally #concept-<name> (from a concept chip on
// the session page) to scroll to that row and highlight it, or #concept-suggested for the
// suggestions.

import { useEffect, useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import { conceptAnchor, fetchConcepts } from "../api/client";
import type { ConceptCount, ConceptsResponse } from "../api/client";
import Navbar from "../components/Navbar";
import ProgressBar from "../components/ProgressBar";

type LoadResult =
  | { project: string; data: ConceptsResponse; error: null }
  | { project: string; data: null; error: string };

const NO_GROUP = "No longer on your list";

function lastSeen(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString() : "";
}

// Groups in order of how often they come up; concepts stay most seen first.
function groupsOf(concepts: ConceptCount[]): [string, ConceptCount[]][] {
  const groups = new Map<string, ConceptCount[]>();
  for (const c of concepts) {
    const name = c.group ?? NO_GROUP;
    groups.set(name, [...(groups.get(name) ?? []), c]);
  }
  const seen = (list: ConceptCount[]) => list.reduce((n, c) => n + c.seen, 0);
  return [...groups.entries()].sort(([a, x], [b, y]) => {
    if (a === NO_GROUP || b === NO_GROUP) return a === NO_GROUP ? 1 : -1;
    return seen(y) - seen(x) || a.localeCompare(b);
  });
}

function ConceptTable({ concepts, showProgress, target }: { concepts: ConceptCount[]; showProgress: boolean; target: string }) {
  return (
    // Fixed column widths, so every group's table lines up with the others.
    <table className="w-full table-fixed text-sm">
      <colgroup>
        <col className="w-[32%]" />
        <col className="w-[40%]" />
        <col className="w-[12%]" />
        <col className="w-[16%]" />
      </colgroup>
      <thead>
        <tr className="text-left text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
          <th className="py-2 pr-4 font-semibold">Concept</th>
          <th className="py-2 pr-4 font-semibold">{showProgress ? "Understood" : "Seen"}</th>
          <th className="py-2 pr-4 font-semibold text-right">Sessions</th>
          <th className="py-2 font-semibold text-right">Last seen</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {concepts.map((c) => (
          <tr key={c.name} id={conceptAnchor(c.name)} className={conceptAnchor(c.name) === target ? "bg-yellow-50" : ""}>
            <td className="py-2.5 pr-4 font-medium text-gray-900">{c.name}</td>
            <td className="py-2.5 pr-4">
              {showProgress ? (
                <ProgressBar understood={c.understood} total={c.seen} />
              ) : (
                <span className="text-gray-700">
                  {c.seen} {c.seen === 1 ? "change" : "changes"}
                </span>
              )}
            </td>
            <td className="py-2.5 pr-4 text-right text-gray-700 tabular-nums">{c.sessions}</td>
            <td className="py-2.5 text-right text-gray-500 tabular-nums">{lastSeen(c.lastSeen)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default function ConceptsPage() {
  const [result, setResult] = useState<LoadResult | null>(null);
  const [searchParams] = useSearchParams();
  const project = searchParams.get("project");
  const target = useLocation().hash.slice(1);

  useEffect(() => {
    if (!project) return;
    let ignore = false;
    fetchConcepts(project)
      .then((data) => {
        if (!ignore) setResult({ project, data, error: null });
      })
      .catch(() => {
        if (!ignore) {
          setResult({ project, data: null, error: "Couldn't load concepts. Make sure lore ui is still running, then refresh." });
        }
      });
    return () => {
      ignore = true;
    };
  }, [project]);

  // Once the rows are on the page, bring the linked one into view.
  const loaded = result !== null;
  useEffect(() => {
    if (loaded && target) document.getElementById(target)?.scrollIntoView({ block: "center" });
  }, [loaded, target]);

  const current = project && result?.project === project ? result : null;
  const loading = !!project && current === null;
  const data = current?.data ?? null;
  const empty = data !== null && data.concepts.length === 0 && data.others.length === 0;

  return (
    <>
      <Navbar project={project} crumbs={[{ label: "Concepts" }]} />
      <div className="px-6 py-6 max-w-5xl">
        <h1 className="text-xl font-semibold">Concepts learned</h1>
        <p className="mt-1 text-sm text-gray-500">
          Each change is tagged with the concepts you'd need to understand it. Understood counts the changes you
          marked understood or passed a teach-back on.
        </p>

        {!project && (
          <p className="text-gray-500 text-center py-12">
            No project selected. Run <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm font-mono">lore ui</code>{" "}
            from inside your project folder.
          </p>
        )}
        {loading && <p className="text-gray-500 text-center py-12">Loading concepts...</p>}
        {current?.error && <p className="text-red-600 text-center py-12">{current.error}</p>}
        {empty && (
          <p className="text-gray-500 text-center py-12">
            No concepts yet. They're added when you run{" "}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm font-mono">lore stop</code>.
          </p>
        )}

        {data && !empty && (
          <>
            {data.concepts.length > 0 && (
              <p className="mt-4 text-sm text-gray-700">
                <span className="font-semibold text-gray-900">{data.concepts.length}</span>{" "}
                {data.concepts.length === 1 ? "concept" : "concepts"} ·{" "}
                <span className="font-semibold text-gray-900">
                  {data.understoodChanges} of {data.changes}
                </span>{" "}
                {data.changes === 1 ? "change" : "changes"} understood
              </p>
            )}

            {groupsOf(data.concepts).map(([group, concepts]) => (
              <section key={group} className="mt-6">
                <h2 className="text-sm font-semibold text-gray-900">{group}</h2>
                <ConceptTable concepts={concepts} showProgress target={target} />
              </section>
            ))}

            {data.others.length > 0 && (
              <section
                id={conceptAnchor("suggested")}
                className={`mt-8 rounded-lg border p-4 ${
                  target === conceptAnchor("suggested") ? "border-amber-400 bg-amber-50" : "border-amber-200 bg-amber-50/50"
                }`}
              >
                <h2 className="text-sm font-semibold text-amber-900">Suggested, not on your list</h2>
                <p className="mt-1 text-xs text-amber-900/80">
                  Nothing on the list fit these changes, so the AI suggested a name. To tag with one from now on, add
                  it to your concept list (see "Concepts" in Lore's README).
                </p>
                <ConceptTable concepts={data.others} showProgress={false} target={target} />
              </section>
            )}
          </>
        )}
      </div>
    </>
  );
}
