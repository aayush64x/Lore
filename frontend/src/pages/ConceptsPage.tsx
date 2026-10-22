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

const BAR_COLOR = ["bg-fill-none", "bg-fill-some", "bg-fill-most"];

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
    <li id={conceptAnchor(concept.name)} className={highlighted ? "bg-warn-soft" : open ? "bg-sunk" : ""}>
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="w-full text-left px-4 sm:px-5 py-4 transition-colors duration-150 hover:bg-sunk"
      >
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-serif text-[17px] leading-snug font-semibold text-ink">{concept.name}</span>
          {isNew && (
            <span className="text-[11px] font-medium px-1.5 py-0.5 rounded border border-accent/25 bg-accent-soft text-accent">
              New this session
            </span>
          )}
          <span className="ml-auto text-xs text-ink-3 whitespace-nowrap" aria-hidden="true">
            {open ? "Hide changes ▴" : "Show changes ▾"}
          </span>
        </div>
        {concept.meaning && <p className="mt-1 text-sm leading-relaxed text-ink-2">{concept.meaning}</p>}
        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-3">
          <span className="flex items-center gap-2">
            <span className="inline-block w-32 h-1.5 rounded-full bg-line overflow-hidden">
              <span className={`block h-full rounded-full ${BAR_COLOR[level(concept)]}`} style={{ width: `${percent}%` }} />
            </span>
            <span>
              <span className="font-semibold text-ink">
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
        <div className="px-4 sm:px-5 pb-5">
          <div className="ml-1 border-l-2 border-line-strong pl-4">
            <p className="mb-3 text-[13px] font-medium text-ink-2">
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
    <div className="min-h-screen bg-paper text-ink">
      <Navbar project={project} crumbs={[{ label: "Concepts" }]} />
      <div className="mx-auto w-full max-w-5xl px-4 sm:px-6 py-8">
        <h1 className="font-serif text-[28px] leading-tight font-semibold tracking-tight text-ink">Concepts learned</h1>
        <p className="mt-2 text-[15px] leading-relaxed text-ink-2">
          The ideas your changes use, weakest first. Understood counts the changes you marked understood or passed a
          teach-back on.
        </p>

        {!project && (
          <p className="text-ink-3 text-center py-12">
            No project selected. Run <code className="bg-sunk px-1.5 py-0.5 rounded text-sm font-mono">lore ui</code>{" "}
            from inside your project folder.
          </p>
        )}
        {loading && <p className="text-ink-3 text-center py-12">Loading concepts...</p>}
        {current?.error && <p className="text-bad text-center py-12">{current.error}</p>}
        {empty && (
          <p className="text-ink-3 text-center py-12">
            No concepts yet. They're added when you run{" "}
            <code className="bg-sunk px-1.5 py-0.5 rounded text-sm font-mono">lore stop</code>.
          </p>
        )}

        {data && !empty && (
          <>
            {nextUp && nextChange && (
              <div className="mt-6 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-card border border-line border-l-4 border-l-accent bg-surface px-4 py-3 text-sm">
                <span className="text-ink-2">
                  <span className="font-semibold text-ink">Next up:</span> {nextUp.name} ({nextUp.seen - nextUp.understood}{" "}
                  {nextUp.seen - nextUp.understood === 1 ? "change" : "changes"} left)
                </span>
                <Link
                  to={changeUrl(nextChange.sessionId, nextChange.filePath, nextChange.changeId)}
                  className="ml-auto text-sm font-medium px-3.5 py-1.5 rounded-control bg-accent text-on-accent transition-colors duration-150 hover:bg-accent-hover"
                >
                  Study it →
                </Link>
              </div>
            )}

            <div className="mt-6 flex flex-wrap items-center gap-4">
              <div role="tablist" aria-label="View" className="inline-flex rounded-control border border-line bg-surface p-0.5 text-sm">
                {(["table", "graph"] as const).map((view) => {
                  const active = view === "graph" ? graphView : !graphView;
                  return (
                    <button
                      key={view}
                      type="button"
                      role="tab"
                      aria-selected={active}
                      onClick={() => showView(view)}
                      className={`px-3 py-1 rounded transition-colors duration-150 ${active ? "bg-sunk font-medium text-ink" : "text-ink-3 hover:text-ink"}`}
                    >
                      {view === "table" ? "Table" : "Graph"}
                    </button>
                  );
                })}
              </div>
              <p className="text-sm text-ink-2">
                <span className="font-semibold text-ink">{data.stats.concepts.length}</span>{" "}
                {data.stats.concepts.length === 1 ? "concept" : "concepts"} ·{" "}
                <span className="font-semibold text-ink">
                  {data.stats.understoodChanges} of {data.stats.changes}
                </span>{" "}
                {data.stats.changes === 1 ? "change" : "changes"} understood
              </p>
            </div>

            {graphView && <ConceptGraph data={data.graph} />}

            {!graphView && (
              <ul className="mt-4 overflow-hidden rounded-card border border-line bg-surface divide-y divide-line">
                {studyList.map((c) => row(c, c.name))}
              </ul>
            )}

            {!graphView && data.stats.others.length > 0 && (
              <section
                id={conceptAnchor("suggested")}
                className={`mt-10 rounded-card border p-4 sm:p-5 ${
                  target === conceptAnchor("suggested") ? "border-warn bg-warn-soft" : "border-line bg-surface"
                }`}
              >
                <h2 className="font-serif text-lg font-semibold text-ink">Suggested, not on your list</h2>
                <p className="mt-1 text-sm text-ink-2">
                  Nothing on the list fit these changes, so the AI suggested a name. To tag with one from now on, add
                  it to your concept list (see "Concepts" in Lore's README).
                </p>
                <ul className="mt-4 overflow-hidden rounded-control border border-line bg-surface divide-y divide-line">
                  {data.stats.others.map((c) => row(c, `other:${c.name.trim().toLowerCase().replace(/\s+/g, " ")}`))}
                </ul>
              </section>
            )}
          </>
        )}
      </div>
    </div>
  );
}
