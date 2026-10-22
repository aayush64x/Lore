// Concepts page — a study list of the concepts this project's changes use.
//
// Every change is tagged with 1-3 concepts from concepts.txt when its session is stopped.
// The table lists them weakest first (none understood, then some, then most), each with
// its one-sentence meaning, "X of Y changes understood", how many sessions it showed up
// in, and "New this session" if it first appeared in the latest session. Opening a row
// lists its changes; each opens on its session page. "Next up" points at the weakest
// concept's first change you haven't understood. "Other" suggestions (nothing on the list
// fit) are shown apart.
//
// A Table / Graph switch picks the view (?view=graph); the graph shows the same concepts
// as circles grouped by category.
//
// URL: /concepts?project=/path/to/project, optionally #concept-<name> (from a concept chip on
// the session page) to open and highlight that row, or #concept-suggested for the suggestions.

import { useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { changeUrl, conceptAnchor, fetchConceptGraph, fetchConcepts, fetchSessions } from "../api/client";
import type { ConceptCount, ConceptGraphData, ConceptGraphNode, ConceptsResponse } from "../api/client";
import ConceptChanges from "../components/ConceptChanges";
import ConceptGraph from "../components/ConceptGraph";
import Navbar from "../components/Navbar";

type LoadResult =
  | { project: string; stats: ConceptsResponse; graph: ConceptGraphData; latestSessionId: string | null; error: null }
  | { project: string; error: string };

/** 0 = none understood, 1 = some, 2 = most (75%+). */
function level(c: ConceptCount): number {
  if (c.understood === 0) return 0;
  return c.understood / c.seen >= 0.75 ? 2 : 1;
}

/** Weakest first: by level, then the smaller share understood, then the most used. */
function weakestFirst(concepts: ConceptCount[]): ConceptCount[] {
  return [...concepts].sort(
    (a, b) =>
      level(a) - level(b) ||
      a.understood / a.seen - b.understood / b.seen ||
      b.seen - a.seen ||
      a.name.localeCompare(b.name),
  );
}

/** The session a concept first showed up in (its changes come newest first). */
function firstSessionId(node: ConceptGraphNode | undefined): string | null {
  return node && node.changes.length > 0 ? node.changes[node.changes.length - 1].sessionId : null;
}

const BAR_COLOR = ["bg-gray-300", "bg-yellow-400", "bg-green-600"];

function StudyRow({
  concept,
  node,
  isNew,
  open,
  highlighted,
  onToggle,
}: {
  concept: ConceptCount;
  node: ConceptGraphNode | undefined;
  isNew: boolean;
  open: boolean;
  highlighted: boolean;
  onToggle: () => void;
}) {
  const percent = Math.round((concept.understood / concept.seen) * 100);
  return (
    <li id={conceptAnchor(concept.name)} className={highlighted ? "bg-yellow-50" : open ? "bg-gray-50" : ""}>
      <button type="button" onClick={onToggle} aria-expanded={open} className="w-full text-left px-4 py-3 hover:bg-gray-50">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-medium text-gray-900">{concept.name}</span>
          {isNew && (
            <span className="text-[11px] font-medium px-1.5 py-0.5 rounded bg-blue-50 text-blue-700 border border-blue-200">
              New this session
            </span>
          )}
          <span className="ml-auto text-xs text-gray-500 whitespace-nowrap" aria-hidden="true">
            {open ? "Hide changes ▴" : "Show changes ▾"}
          </span>
        </div>
        {concept.meaning && <p className="mt-0.5 text-sm text-gray-500">{concept.meaning}</p>}
        <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-600">
          <span className="flex items-center gap-2">
            <span className="inline-block w-32 h-1.5 rounded-full bg-gray-200 overflow-hidden">
              <span className={`block h-full rounded-full ${BAR_COLOR[level(concept)]}`} style={{ width: `${percent}%` }} />
            </span>
            <span>
              <span className="font-semibold text-gray-900">
                {concept.understood} of {concept.seen}
              </span>{" "}
              {concept.seen === 1 ? "change" : "changes"} understood
            </span>
          </span>
          <span>
            seen in {concept.sessions} {concept.sessions === 1 ? "session" : "sessions"}
          </span>
        </div>
      </button>
      {open && node && (
        // Indented under the row, on a guide line, so it reads as this concept's own list.
        <div className="px-4 pb-4">
          <div className="ml-1 border-l-2 border-blue-200 pl-4">
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-gray-500">
              {node.changes.length} {node.changes.length === 1 ? "change uses" : "changes use"} {concept.name}
            </p>
            <ConceptChanges changes={node.changes} />
          </div>
        </div>
      )}
    </li>
  );
}

export default function ConceptsPage() {
  const [result, setResult] = useState<LoadResult | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const graphView = searchParams.get("view") === "graph";
  const project = searchParams.get("project");
  const target = useLocation().hash.slice(1);
  // Opened rows, by their anchor id. A concept chip's link opens its row.
  const [opened, setOpened] = useState<Set<string>>(() => new Set(target ? [target] : []));

  useEffect(() => {
    if (!project) return;
    let ignore = false;
    Promise.all([fetchConcepts(project), fetchConceptGraph(project), fetchSessions(project)])
      .then(([stats, graph, sessions]) => {
        const latest = sessions
          .filter((s) => s.status === "COMPLETE" && s.stoppedAt)
          .sort((a, b) => (b.stoppedAt ?? "").localeCompare(a.stoppedAt ?? ""))[0];
        if (!ignore) setResult({ project, stats, graph, latestSessionId: latest?.id ?? null, error: null });
      })
      .catch(() => {
        if (!ignore) setResult({ project, error: "Couldn't load concepts. Make sure lore ui is still running, then refresh." });
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

  function showView(view: "table" | "graph") {
    const next = new URLSearchParams(searchParams);
    if (view === "graph") next.set("view", "graph");
    else next.delete("view");
    setSearchParams(next);
  }

  function toggle(anchor: string) {
    setOpened((prev) => {
      const next = new Set(prev);
      if (next.has(anchor)) next.delete(anchor);
      else next.add(anchor);
      return next;
    });
  }

  const current = project && result?.project === project ? result : null;
  const loading = !!project && current === null;
  const data = current && current.error === null ? current : null;
  const empty = data !== null && data.stats.concepts.length === 0 && data.stats.others.length === 0;

  const nodes = new Map((data?.graph.nodes ?? []).map((n) => [n.id, n]));
  const studyList = data ? weakestFirst(data.stats.concepts) : [];
  // Next up: the weakest concept with something left, and its first change not understood.
  const nextUp = studyList.find((c) => c.understood < c.seen);
  const nextChange = nextUp ? nodes.get(nextUp.name)?.changes.find((c) => !c.understood) : undefined;
  const row = (concept: ConceptCount, nodeId: string) => (
    <StudyRow
      key={nodeId}
      concept={concept}
      node={nodes.get(nodeId)}
      isNew={!!data?.latestSessionId && firstSessionId(nodes.get(nodeId)) === data.latestSessionId}
      open={opened.has(conceptAnchor(concept.name))}
      highlighted={conceptAnchor(concept.name) === target}
      onToggle={() => toggle(conceptAnchor(concept.name))}
    />
  );

  return (
    <>
      <Navbar project={project} crumbs={[{ label: "Concepts" }]} />
      <div className="px-6 py-6 max-w-4xl">
        <h1 className="text-xl font-semibold">Concepts learned</h1>
        <p className="mt-1 text-sm text-gray-500">
          The ideas your changes use, weakest first. Understood counts the changes you marked understood or passed a
          teach-back on.
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
            {nextUp && nextChange && (
              <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm">
                <span className="text-gray-700">
                  <span className="font-semibold text-gray-900">Next up:</span> {nextUp.name} ({nextUp.seen - nextUp.understood}{" "}
                  {nextUp.seen - nextUp.understood === 1 ? "change" : "changes"} left)
                </span>
                <Link
                  to={changeUrl(nextChange.sessionId, nextChange.filePath, nextChange.changeId)}
                  className="ml-auto text-sm font-medium px-3 py-1.5 rounded-md bg-blue-600 text-white hover:bg-blue-700"
                >
                  Study it →
                </Link>
              </div>
            )}

            <div className="mt-4 flex flex-wrap items-center gap-4">
              <div role="tablist" aria-label="View" className="inline-flex rounded-md border border-gray-300 p-0.5 text-sm">
                {(["table", "graph"] as const).map((view) => {
                  const active = view === "graph" ? graphView : !graphView;
                  return (
                    <button
                      key={view}
                      type="button"
                      role="tab"
                      aria-selected={active}
                      onClick={() => showView(view)}
                      className={`px-3 py-1 rounded ${active ? "bg-blue-600 text-white" : "text-gray-600 hover:text-gray-900"}`}
                    >
                      {view === "table" ? "Table" : "Graph"}
                    </button>
                  );
                })}
              </div>
              <p className="text-sm text-gray-700">
                <span className="font-semibold text-gray-900">{data.stats.concepts.length}</span>{" "}
                {data.stats.concepts.length === 1 ? "concept" : "concepts"} ·{" "}
                <span className="font-semibold text-gray-900">
                  {data.stats.understoodChanges} of {data.stats.changes}
                </span>{" "}
                {data.stats.changes === 1 ? "change" : "changes"} understood
              </p>
            </div>

            {graphView && <ConceptGraph data={data.graph} />}

            {!graphView && (
              <ul className="mt-4 rounded-lg border border-gray-200 bg-white divide-y divide-gray-100">
                {studyList.map((c) => row(c, c.name))}
              </ul>
            )}

            {!graphView && data.stats.others.length > 0 && (
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
                <ul className="mt-3 rounded-md border border-amber-200 bg-white divide-y divide-gray-100">
                  {data.stats.others.map((c) => row(c, `other:${c.name.trim().toLowerCase().replace(/\s+/g, " ")}`))}
                </ul>
              </section>
            )}
          </>
        )}
      </div>
    </>
  );
}
