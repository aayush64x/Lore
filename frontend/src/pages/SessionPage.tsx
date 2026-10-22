// Session page — one session: its goal and numbers on top, the changed files on
// the left, and the selected file's diff and explain panel on the right.
//
// URLs (both show this page; the second one opens with that file selected):
//   /sessions/:id
//   /sessions/:id/file?path=src/App.tsx
// Add &unreviewed=1 (or ?unreviewed=1) to hide what's already understood.
// The path is a query param because file paths contain slashes.
//
// Wide screens: everything fits the screen and the diff scrolls in its own box.
// Below 1000px the columns stack and the page scrolls.

import { useState, useEffect, useMemo, useRef } from "react";
import type { CSSProperties } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import {
  DELETE_CONFIRM,
  TeachBackError,
  deleteSession,
  explainChange,
  fetchSession,
  retrySummary,
  sessionTitle,
  sessionsUrl,
  teachBack,
  toggleUnderstood,
} from "../api/client";
import type { ChangeResponse, ExplanationConcept, ExplanationData, SessionDetailResponse, TeachBackResult } from "../api/client";
import ChangeDiff from "../components/ChangeDiff";
import ExplainPanel from "../components/ExplainPanel";
import FileSidebar from "../components/FileSidebar";
import type { FileSummary } from "../components/FileSidebar";
import ProgressBar from "../components/ProgressBar";
import Navbar from "../components/Navbar";
import SessionHeader from "../components/SessionHeader";
import TeachBackPanel from "../components/TeachBackPanel";
import { diffRows, lineCounts, stepLabel } from "../components/diffLines";

// A copy of a per-change record without one change's entry.
function omit<T>(record: Record<string, T>, key: string): Record<string, T> {
  const rest = { ...record };
  delete rest[key];
  return rest;
}

const SIDEBAR_KEY = "lore.sidebarCollapsed";

// Storage can be blocked or empty (private window, cleared data); then it starts open.
function readSidebarCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === "1";
  } catch {
    return false;
  }
}

export default function SessionPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const pathParam = searchParams.get("path");
  const unreviewedOnly = searchParams.get("unreviewed") === "1";
  const navigate = useNavigate();
  const [session, setSession] = useState<SessionDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);

  // Per change, so switching changes (or files) keeps what was already explained.
  // Saved explanations come with the session; this holds ones made on this visit.
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [explanations, setExplanations] = useState<Record<string, ExplanationData>>({});
  const [explainErrors, setExplainErrors] = useState<Record<string, string>>({});
  const [explainingId, setExplainingId] = useState<string | null>(null);
  const [activeSteps, setActiveSteps] = useState<Record<string, number>>({});
  const [hoverStep, setHoverStep] = useState<number | null>(null);
  // Teach-back: the change being tested (its panel replaces the explanation), drafts and
  // grades per change, and a short "passed" note shown on the change we moved on to.
  const [testingId, setTestingId] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [testResults, setTestResults] = useState<Record<string, TeachBackResult>>({});
  const [testErrors, setTestErrors] = useState<Record<string, string>>({});
  const [testBusyId, setTestBusyId] = useState<string | null>(null);
  const [passNote, setPassNote] = useState<{ forChangeId: string; file: string } | null>(null);
  const [diffScrolled, setDiffScrolled] = useState(false);
  const diffBoxRef = useRef<HTMLDivElement>(null);
  // Files sidebar folded to a thin rail (wide screens). Remembered in this browser.
  const [sidebarCollapsed, setSidebarCollapsed] = useState(readSidebarCollapsed);
  // A change to scroll to once its file is on screen (it may be in another file).
  const pendingScroll = useRef<string | null>(null);

  useEffect(() => {
    if (!id) return;

    fetchSession(id)
      .then((data) => setSession(data))
      .catch(() => setError("Session not found."))
      .finally(() => setLoading(false));
  }, [id]);

  const allChanges = useMemo(() => session?.changes ?? [], [session]);

  // Files in the order they were changed, each with its changes and counts.
  const files = useMemo(() => {
    const byPath = new Map<string, ChangeResponse[]>();
    for (const c of allChanges) {
      if (!byPath.has(c.filePath)) byPath.set(c.filePath, []);
      byPath.get(c.filePath)!.push(c);
    }
    return [...byPath].map(([path, changes]) => {
      const counts = changes.map((c) => lineCounts(c.diffContent));
      return {
        path,
        changes,
        total: changes.length,
        understood: changes.filter((c) => c.understood).length,
        passed: changes.filter((c) => c.understoodBy === "teach_back").length,
        added: counts.reduce((n, c) => n + c.added, 0),
        removed: counts.reduce((n, c) => n + c.removed, 0),
      };
    });
  }, [allChanges]);
  // The order "Start learning" walks through: file by file, top to bottom.
  const learningOrder = useMemo(() => files.flatMap((f) => f.changes), [files]);

  // No file in the URL: open the first one with something left to learn.
  const filePath = pathParam ?? (files.find((f) => f.understood < f.total) ?? files[0])?.path ?? null;
  const currentFile = files.find((f) => f.path === filePath) ?? null;

  // Unreviewed only skips files that are all understood, but keeps the current one
  // so "File X of Y" makes sense.
  const sidebarFiles: FileSummary[] = unreviewedOnly
    ? files.filter((f) => f.path === filePath || f.understood < f.total)
    : files;
  const fileIndex = sidebarFiles.findIndex((f) => f.path === filePath);
  const prevFile = fileIndex > 0 ? sidebarFiles[fileIndex - 1].path : null;
  const nextFile = fileIndex >= 0 && fileIndex < sidebarFiles.length - 1 ? sidebarFiles[fileIndex + 1].path : null;

  const fileChanges = currentFile?.changes ?? [];
  // Unreviewed only also hides understood changes, but never the one you're looking at,
  // so marking it understood doesn't make it vanish from under you.
  const changes = unreviewedOnly
    ? fileChanges.filter((c) => !c.understood || c.id === selectedId)
    : fileChanges;
  const rowsById = useMemo(() => {
    const map: Record<string, ReturnType<typeof diffRows>> = {};
    for (const c of allChanges) map[c.id] = c.diffContent ? diffRows(c.diffContent, c.oldStart, c.newStart) : [];
    return map;
  }, [allChanges]);

  const selected =
    changes.find((c) => c.id === selectedId) ?? changes.find((c) => !c.understood) ?? changes[0] ?? null;
  const selectedIndex = selected ? changes.indexOf(selected) : -1;
  const explanationOf = (c: ChangeResponse) => explanations[c.id] ?? c.explanation ?? null;
  const explanation = selected ? explanationOf(selected) : null;
  const activeStep = selected ? activeSteps[selected.id] ?? 0 : 0;

  // Every concept from the session's explanations, once each (by name, ignoring case).
  const concepts = useMemo(() => {
    const seen = new Map<string, ExplanationConcept>();
    for (const c of allChanges) {
      for (const concept of (explanations[c.id] ?? c.explanation)?.concepts ?? []) {
        const key = concept.name.trim().toLowerCase();
        if (key && !seen.has(key)) seen.set(key, concept);
      }
    }
    return [...seen.values()];
  }, [allChanges, explanations]);

  // After a change is picked (maybe in another file), scroll to it once it's drawn.
  useEffect(() => {
    if (!pendingScroll.current) return;
    const el = diffBoxRef.current?.querySelector(`[data-change="${pendingScroll.current}"]`);
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "start" });
      pendingScroll.current = null;
    }
  });

  function toggleSidebar() {
    const next = !sidebarCollapsed;
    setSidebarCollapsed(next);
    try {
      localStorage.setItem(SIDEBAR_KEY, next ? "1" : "0");
    } catch {
      // Storage blocked (private window, etc.): it just won't be remembered.
    }
  }

  function fileUrl(path: string, unreviewed = unreviewedOnly) {
    return `/sessions/${id}/file?path=${encodeURIComponent(path)}${unreviewed ? "&unreviewed=1" : ""}`;
  }

  // replace: Back in the browser still leaves the session, not the previous file.
  function goToFile(path: string | null) {
    if (!path) return;
    setSelectedId(null);
    setHoverStep(null);
    diffBoxRef.current?.scrollTo({ top: 0 });
    navigate(fileUrl(path), { replace: true });
  }

  function goToChange(change: ChangeResponse) {
    setSelectedId(change.id);
    setHoverStep(null);
    pendingScroll.current = change.id;
    if (change.filePath !== filePath) navigate(fileUrl(change.filePath), { replace: true });
  }

  // The next change not yet understood after `afterId`, wrapping round to the start.
  function nextToLearn(afterId: string | null): ChangeResponse | null {
    const start = afterId ? learningOrder.findIndex((c) => c.id === afterId) + 1 : 0;
    const ordered = [...learningOrder.slice(start), ...learningOrder.slice(0, start)];
    return ordered.find((c) => !c.understood && c.id !== afterId) ?? null;
  }

  // Opens the first change not yet understood and starts its walkthrough at step 1,
  // explaining it first if it has no explanation yet. Something always visibly
  // happens, even when that change is already on screen.
  function startLearning() {
    const first = nextToLearn(null);
    if (!first) return;
    goToChange(first);
    setActiveSteps((prev) => ({ ...prev, [first.id]: 0 }));
    if (!explanationOf(first) && !first.binary && explainingId !== first.id) handleExplain(first.id);
  }

  function handleRetrySummary() {
    if (!id) return;
    setRetrying(true);
    setRetryError(null);
    retrySummary(id)
      .then((data) => setSession(data))
      .catch((err: Error) => setRetryError(err.message))
      .finally(() => setRetrying(false));
  }

  // Asks first; once deleted there's nothing to show here, so go back to the list.
  function handleDelete() {
    if (!session || !window.confirm(DELETE_CONFIRM)) return;
    setDeleting(true);
    setDeleteError(null);
    deleteSession(session.id)
      .then(() => navigate(sessionsUrl(session.projectPath)))
      .catch((err: Error) => {
        setDeleteError(err.message);
        setDeleting(false);
      });
  }

  async function handleExplain(changeId: string, refresh = false) {
    setExplainingId(changeId);
    setExplainErrors((prev) => {
      const rest = { ...prev };
      delete rest[changeId];
      return rest;
    });
    try {
      const data = await explainChange(changeId, refresh);
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

  function updateChange(changeId: string, patch: Partial<ChangeResponse>) {
    setSession((prev) =>
      prev ? { ...prev, changes: prev.changes.map((c) => (c.id === changeId ? { ...c, ...patch } : c)) } : prev
    );
  }

  // By hand it's always "self": marking sets it, unmarking clears it. Shown at once, then
  // set to what the server says (or put back if the request failed).
  function handleToggleUnderstood(changeId: string) {
    const before = allChanges.find((c) => c.id === changeId);
    if (!before) return;
    updateChange(changeId, { understood: !before.understood, understoodBy: before.understood ? null : "self" });
    toggleUnderstood(changeId)
      .then((r) => updateChange(changeId, r))
      .catch(() => updateChange(changeId, { understood: before.understood, understoodBy: before.understoodBy }));
  }

  // Opens the teach-back panel. A finished grade is cleared so there's a fresh box,
  // unless there are no tries left (then the grade and key points stay up).
  function startTest(change: ChangeResponse) {
    setTestingId(change.id);
    setPassNote(null);
    setTestErrors((prev) => omit(prev, change.id));
    if (change.teachBack.attemptsLeft > 0 || change.understoodBy === "teach_back") {
      setTestResults((prev) => omit(prev, change.id));
    }
  }

  async function submitTest(change: ChangeResponse) {
    setTestBusyId(change.id);
    setTestErrors((prev) => omit(prev, change.id));
    try {
      const r = await teachBack(change.id, drafts[change.id] ?? "");
      const { attemptsUsed, attemptsLeft, lastVerdict, keyPoints } = r;
      updateChange(change.id, {
        understood: r.understood,
        understoodBy: r.understoodBy,
        teachBack: { attemptsUsed, attemptsLeft, lastVerdict, keyPoints },
      });
      setTestResults((prev) => ({ ...prev, [change.id]: r }));
      // A pass moves on, like "Got it" does. If nothing is left, or it was practice, the
      // grade stays on screen.
      if (r.verdict === "pass" && !r.practice) {
        const next = nextToLearn(change.id);
        if (next) {
          setTestingId(null);
          setPassNote({ forChangeId: next.id, file: change.filePath });
          goToChange(next);
        }
      }
    } catch (e) {
      const message = e instanceof TeachBackError ? e.message : "Couldn't check your answer. Try again.";
      setTestErrors((prev) => ({ ...prev, [change.id]: message }));
      if (e instanceof TeachBackError && typeof e.state.attemptsUsed === "number") {
        updateChange(change.id, { teachBack: { ...change.teachBack, ...e.state } });
      }
    } finally {
      setTestBusyId(null);
    }
  }

  // "Got it? Mark as understood": mark it, then go straight to the next one to learn.
  function handleMarkUnderstood(change: ChangeResponse) {
    if (!change.understood) handleToggleUnderstood(change.id);
    setTestingId(null);
    const next = nextToLearn(change.id);
    if (next) goToChange(next);
  }

  // [ and ] move between files; ← and → between explain steps; b folds the files sidebar.
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
      else if (e.key === "b") toggleSidebar();
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

  const total = allChanges.length;
  const understood = allChanges.filter((c) => c.understood).length;

  const navButton = "text-gray-600 hover:text-gray-900 disabled:text-gray-300 disabled:cursor-default";
  const fileNav = fileIndex >= 0 && (
    <div className="flex items-center gap-3 text-sm whitespace-nowrap">
      <button onClick={() => goToFile(prevFile)} disabled={!prevFile} title="Previous file ( [ )" className={navButton}>
        ← Prev file
      </button>
      <span className="text-gray-400">·</span>
      <span className="text-gray-700">
        File {fileIndex + 1} of {sidebarFiles.length}
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
          onChange={(e) =>
            navigate(filePath ? fileUrl(filePath, e.target.checked) : `/sessions/${id}${e.target.checked ? "?unreviewed=1" : ""}`, {
              replace: true,
            })
          }
        />
        Unreviewed only
      </label>
    </div>
  );

  // project › session title › this file
  const navbar = (
    <Navbar
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
        <div className="px-6 py-6 text-gray-500">Loading session...</div>
      </>
    );
  }

  if (error || !session) {
    return (
      <>
        <Navbar project={null} crumbs={[{ label: "Session not found" }]} />
        <div className="px-6 py-6">
          <p className="text-red-600">{error ?? "Session not found."}</p>
        </div>
      </>
    );
  }

  return (
    <div className="min-[1000px]:h-screen min-[1000px]:flex min-[1000px]:flex-col">
      {navbar}
      <SessionHeader
        session={session}
        fileCount={files.length}
        added={files.reduce((n, f) => n + f.added, 0)}
        removed={files.reduce((n, f) => n + f.removed, 0)}
        understood={understood}
        total={total}
        onStartLearning={startLearning}
        onRetrySummary={handleRetrySummary}
        retrying={retrying}
        retryError={retryError}
        onDelete={handleDelete}
        deleting={deleting}
        deleteError={deleteError}
      />
      {/* The sidebar's width is a CSS variable in the grid, so changing it animates the
          column and the diff grows into the space instead of jumping. */}
      <main
        style={{ "--sidebar-w": sidebarCollapsed ? "52px" : "280px" } as CSSProperties}
        className="min-[1000px]:flex-1 min-[1000px]:min-h-0 grid min-[1000px]:grid-cols-[var(--sidebar-w)_minmax(0,1fr)_clamp(400px,30vw,480px)] transition-[grid-template-columns] duration-200 ease-out motion-reduce:transition-none"
      >
        <aside className="bg-white border-b border-gray-200 min-[1000px]:border-b-0 min-[1000px]:border-r min-[1000px]:overflow-y-auto min-[1000px]:overflow-x-hidden">
          <FileSidebar
            files={sidebarFiles}
            selected={filePath}
            onSelect={goToFile}
            concepts={concepts}
            collapsed={sidebarCollapsed}
            onToggle={toggleSidebar}
          />
        </aside>

        <div
          ref={diffBoxRef}
          onScroll={(e) => setDiffScrolled(e.currentTarget.scrollTop > 0)}
          className="min-w-0 px-6 py-5 min-[1000px]:overflow-y-auto"
        >
          {!currentFile ? (
            <p className="text-sm text-gray-500 py-10 text-center">
              {pathParam ? "This file has no changes in this session." : "This session has no changes."}
            </p>
          ) : changes.length === 0 ? (
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
                onSelect={() => change.id !== selected?.id && setSelectedId(change.id)}
                issue={explanationOf(change)?.possibleIssue ?? null}
              />
            ))
          )}
        </div>

        <aside className="bg-white border-t border-gray-200 min-[1000px]:border-t-0 min-[1000px]:border-l min-[1000px]:overflow-y-auto">
          {selected && passNote?.forChangeId === selected.id && (
            <p className="mx-5 mt-4 px-3 py-2 rounded-md bg-green-50 border border-green-200 text-sm text-green-800">
              ✓ Passed <span className="font-mono text-xs">{passNote.file}</span>. Here's the next one.
            </p>
          )}
          {selected && testingId === selected.id && (
            <TeachBackPanel
              change={selected}
              answer={drafts[selected.id] ?? ""}
              onAnswerChange={(text) => setDrafts((prev) => ({ ...prev, [selected.id]: text }))}
              busy={testBusyId === selected.id}
              result={testResults[selected.id] ?? null}
              error={testErrors[selected.id] ?? null}
              onSubmit={() => submitTest(selected)}
              onCancel={() => setTestingId(null)}
              onSkip={() => handleMarkUnderstood(selected)}
            />
          )}
          {selected && testingId !== selected.id && (
            <ExplainPanel
              change={selected}
              index={selectedIndex}
              count={changes.length}
              onPrevChange={() => selectedIndex > 0 && goToChange(changes[selectedIndex - 1])}
              onNextChange={() => selectedIndex < changes.length - 1 && goToChange(changes[selectedIndex + 1])}
              explanation={explanation}
              loading={explainingId === selected.id}
              error={explainErrors[selected.id] ?? null}
              onExplain={() => handleExplain(selected.id)}
              activeStep={activeStep}
              onActivateStep={activateStep}
              onHoverStep={setHoverStep}
              stepLabel={(start, end) => stepLabel(rowsById[selected.id] ?? [], start, end)}
              onToggleUnderstood={() => handleToggleUnderstood(selected.id)}
              onMarkUnderstood={() => handleMarkUnderstood(selected)}
              onTestMe={() => startTest(selected)}
              onExplainForTest={() => handleExplain(selected.id, true)}
            />
          )}
        </aside>
      </main>
    </div>
  );
}
