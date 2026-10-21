// File page — every change in one file of a session.
//
// URL: /sessions/:id/file?path=src/App.tsx (&unreviewed=1 to skip understood changes)
// The path is a query param because file paths contain slashes.
//
// Wide screens: the diffs scroll in their own box on the left, and the explain
// panel for the selected change sits on the right. Both fill the screen under
// the navbar. Below 1000px the panel goes under the diffs and the page scrolls.

import { useState, useEffect, useMemo, useRef } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { explainChange, fetchSession, sessionTitle, toggleUnderstood } from "../api/client";
import type { ExplanationData, SessionDetailResponse } from "../api/client";
import ChangeDiff from "../components/ChangeDiff";
import ExplainPanel from "../components/ExplainPanel";
import ProgressBar from "../components/ProgressBar";
import Navbar from "../components/Navbar";
import { diffRows, stepLabel } from "../components/diffLines";

export default function FileDetail() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const filePath = searchParams.get("path");
  const unreviewedOnly = searchParams.get("unreviewed") === "1";
  const navigate = useNavigate();
  const [session, setSession] = useState<SessionDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Per change, so switching changes (or files) keeps what was already explained.
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [explanations, setExplanations] = useState<Record<string, ExplanationData>>({});
  const [explainErrors, setExplainErrors] = useState<Record<string, string>>({});
  const [explainingId, setExplainingId] = useState<string | null>(null);
  const [activeSteps, setActiveSteps] = useState<Record<string, number>>({});
  const [hoverStep, setHoverStep] = useState<number | null>(null);
  const [diffScrolled, setDiffScrolled] = useState(false);
  const diffBoxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!id) return;

    fetchSession(id)
      .then((data) => setSession(data))
      .catch(() => setError("Session not found."))
      .finally(() => setLoading(false));
  }, [id]);

  const fileChanges = useMemo(
    () => session?.changes.filter((c) => c.filePath === filePath) ?? [],
    [session, filePath]
  );
  // Unreviewed only hides understood changes, but never the one you're looking at,
  // so marking it understood doesn't make it vanish from under you.
  const changes = unreviewedOnly
    ? fileChanges.filter((c) => !c.understood || c.id === selectedId)
    : fileChanges;
  const rowsById = useMemo(() => {
    const map: Record<string, ReturnType<typeof diffRows>> = {};
    for (const c of fileChanges) map[c.id] = c.diffContent ? diffRows(c.diffContent, c.oldStart, c.newStart) : [];
    return map;
  }, [fileChanges]);

  const selected = changes.find((c) => c.id === selectedId) ?? changes[0] ?? null;
  const selectedIndex = selected ? changes.indexOf(selected) : -1;
  const explanation = selected ? explanations[selected.id] ?? null : null;
  const activeStep = selected ? activeSteps[selected.id] ?? 0 : 0;

  // Files in the same order as the file list. Unreviewed only skips files that
  // are all understood, but keeps the current one so "File X of Y" makes sense.
  const files = useMemo(() => {
    if (!session) return [];
    const all = [...new Set(session.changes.map((c) => c.filePath))];
    if (!unreviewedOnly) return all;
    return all.filter(
      (f) => f === filePath || session.changes.some((c) => c.filePath === f && !c.understood)
    );
  }, [session, unreviewedOnly, filePath]);
  const fileIndex = filePath ? files.indexOf(filePath) : -1;
  const prevFile = fileIndex > 0 ? files[fileIndex - 1] : null;
  const nextFile = fileIndex >= 0 && fileIndex < files.length - 1 ? files[fileIndex + 1] : null;

  function fileUrl(path: string, unreviewed = unreviewedOnly) {
    return `/sessions/${id}/file?path=${encodeURIComponent(path)}${unreviewed ? "&unreviewed=1" : ""}`;
  }

  // replace: Back in the browser still returns to the file list, not the previous file.
  function goToFile(path: string | null) {
    if (!path) return;
    setSelectedId(null);
    setHoverStep(null);
    diffBoxRef.current?.scrollTo({ top: 0 });
    navigate(fileUrl(path), { replace: true });
  }

  function selectChange(changeId: string, scroll: boolean) {
    setSelectedId(changeId);
    setHoverStep(null);
    if (scroll) {
      diffBoxRef.current
        ?.querySelector(`[data-change="${changeId}"]`)
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }

  async function handleExplain(changeId: string) {
    setExplainingId(changeId);
    setExplainErrors((prev) => {
      const rest = { ...prev };
      delete rest[changeId];
      return rest;
    });
    try {
      const data = await explainChange(changeId);
      setExplanations((prev) => ({ ...prev, [changeId]: data }));
      setActiveSteps((prev) => ({ ...prev, [changeId]: 0 }));
    } catch {
      setExplainErrors((prev) => ({ ...prev, [changeId]: "Couldn't explain this change." }));
    } finally {
      setExplainingId(null);
    }
  }

  function activateStep(i: number) {
    if (!selected || !explanation || i < 0 || i >= explanation.steps.length) return;
    setActiveSteps((prev) => ({ ...prev, [selected.id]: i }));
    const step = explanation.steps[i];
    if (step.diff_line_start < 1) return;
    // Rows are tagged with their diff line number. Plain code hides the @@ header,
    // so take the first row that exists at or after the step's start.
    const target = [
      ...(diffBoxRef.current?.querySelectorAll<HTMLElement>(`[data-change="${selected.id}"] [data-diff-line]`) ?? []),
    ].find((el) => Number(el.dataset.diffLine) >= step.diff_line_start);
    target?.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  function handleToggleUnderstood(changeId: string) {
    const flip = (prev: SessionDetailResponse | null) =>
      prev
        ? {
            ...prev,
            changes: prev.changes.map((c) =>
              c.id === changeId ? { ...c, understood: !c.understood } : c
            ),
          }
        : prev;

    setSession(flip);
    toggleUnderstood(changeId).catch(() => setSession(flip));
  }

  // [ and ] move between files; ← and → between explain steps.
  // Ignored while typing or with a modifier key held (so browser shortcuts still work).
  // Re-attached after every render so it always sees the current file and step.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (e.metaKey || e.ctrlKey || e.altKey || target.closest("input, textarea, select, [contenteditable]")) return;
      if (e.key === "[") goToFile(prevFile);
      else if (e.key === "]") goToFile(nextFile);
      else if (e.key === "ArrowLeft" && explanation) activateStep(activeStep - 1);
      else if (e.key === "ArrowRight" && explanation) activateStep(activeStep + 1);
      else return;
      e.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  // Hovering a step shows its lines; otherwise the active step's lines stay lit.
  // The AI can send 0 when it couldn't point at lines; nothing to highlight then.
  const shownStep = explanation?.steps[hoverStep ?? activeStep];
  const highlight =
    shownStep && shownStep.diff_line_start >= 1 && shownStep.diff_line_end >= shownStep.diff_line_start
      ? { start: shownStep.diff_line_start, end: shownStep.diff_line_end }
      : null;

  const total = session?.changes.length ?? 0;
  const understood = session?.changes.filter((c) => c.understood).length ?? 0;

  const navButton = "text-gray-600 hover:text-gray-900 disabled:text-gray-300 disabled:cursor-default";
  const fileNav = files.length > 0 && fileIndex >= 0 && (
    <div className="flex items-center gap-3 text-sm whitespace-nowrap">
      <button onClick={() => goToFile(prevFile)} disabled={!prevFile} title="Previous file ( [ )" className={navButton}>
        ← Prev file
      </button>
      <span className="text-gray-400">·</span>
      <span className="text-gray-700">
        File {fileIndex + 1} of {files.length}
      </span>
      <span className="text-gray-400">·</span>
      <button onClick={() => goToFile(nextFile)} disabled={!nextFile} title="Next file ( ] )" className={navButton}>
        Next file →
      </button>
    </div>
  );
  const progress = session && (
    <div className="flex items-center gap-4">
      <div className="w-56">
        <ProgressBar understood={understood} total={total} />
      </div>
      <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer whitespace-nowrap">
        <input
          type="checkbox"
          checked={unreviewedOnly}
          onChange={(e) => navigate(fileUrl(filePath ?? "", e.target.checked), { replace: true })}
        />
        Unreviewed only
      </label>
    </div>
  );

  // project › session title › this file
  const navbar = (
    <Navbar
      fullWidth
      scrolled={diffScrolled}
      project={session?.projectPath ?? null}
      crumbs={[
        ...(session ? [{ label: sessionTitle(session.reason), to: `/sessions/${id}${unreviewedOnly ? "?unreviewed=1" : ""}` }] : []),
        ...(filePath ? [{ label: filePath, mono: true, to: fileUrl(filePath) }] : []),
      ]}
      center={fileNav}
      right={progress}
    />
  );

  if (loading) {
    return (
      <>
        {navbar}
        <div className="px-6 py-6 text-gray-500">Loading file...</div>
      </>
    );
  }

  if (error || !session || fileChanges.length === 0) {
    return (
      <>
        {navbar}
        <div className="px-6 py-6">
          <p className="text-red-600">{error ?? "This file has no changes in this session."}</p>
        </div>
      </>
    );
  }

  return (
    <div className="min-[1000px]:h-screen min-[1000px]:flex min-[1000px]:flex-col">
      {navbar}
      <main className="min-[1000px]:flex-1 min-[1000px]:min-h-0 grid min-[1000px]:grid-cols-[minmax(0,1fr)_clamp(420px,32vw,480px)]">
        <div
          ref={diffBoxRef}
          onScroll={(e) => setDiffScrolled(e.currentTarget.scrollTop > 0)}
          className="min-w-0 px-6 py-5 min-[1000px]:overflow-y-auto"
        >
          {changes.length === 0 ? (
            <p className="text-sm text-gray-500 py-10 text-center">
              Every change in this file is understood.{" "}
              {nextFile && (
                <button onClick={() => goToFile(nextFile)} className="text-blue-600 hover:underline">
                  Next file →
                </button>
              )}
            </p>
          ) : (
            changes.map((change) => (
              <ChangeDiff
                key={change.id}
                change={change}
                rows={rowsById[change.id] ?? []}
                active={change.id === selected?.id}
                highlight={change.id === selected?.id ? highlight : null}
                onSelect={() => change.id !== selected?.id && selectChange(change.id, false)}
              />
            ))
          )}
        </div>

        <aside className="bg-white border-t border-gray-200 min-[1000px]:border-t-0 min-[1000px]:border-l min-[1000px]:overflow-y-auto">
          {selected && (
            <ExplainPanel
              change={selected}
              index={selectedIndex}
              count={changes.length}
              onPrevChange={() => selectedIndex > 0 && selectChange(changes[selectedIndex - 1].id, true)}
              onNextChange={() =>
                selectedIndex < changes.length - 1 && selectChange(changes[selectedIndex + 1].id, true)
              }
              explanation={explanation}
              loading={explainingId === selected.id}
              error={explainErrors[selected.id] ?? null}
              onExplain={() => handleExplain(selected.id)}
              activeStep={activeStep}
              onActivateStep={activateStep}
              onHoverStep={setHoverStep}
              stepLabel={(start, end) => stepLabel(rowsById[selected.id] ?? [], start, end)}
              onToggleUnderstood={() => handleToggleUnderstood(selected.id)}
            />
          )}
        </aside>
      </main>
    </div>
  );
}
