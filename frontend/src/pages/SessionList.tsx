// Session List Page — the home page of Lore's web UI.
//
// PURPOSE: Show all sessions for the current project, newest first.
// The user scans this list and clicks a session to study its changes.
//
// DATA NEEDED: Call fetchSessions(project) on mount.
//   - "project" comes from the URL query param: ?project=/path/to/project
//   - Use useSearchParams() from react-router-dom to read it.
//
// STATES TO HANDLE:
//   - Loading: show "Loading sessions..."
//   - Empty: show the empty state message (no sessions yet)
//   - Loaded: render the session rows
//
// NAVIGATION: Clicking a row goes to /sessions/:id. A running session (lore start
// without lore stop) has nothing to study yet, so its row isn't clickable.
//
// DELETE: each row has a Delete button (disabled while the session is running).
// It asks first, then removes the row once the server has deleted the session.

import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { DELETE_CONFIRM, deleteSession, fetchSessions, sessionTitle } from "../api/client";
import type { SessionResponse } from "../api/client";
import Navbar from "../components/Navbar";

// The result remembers which project it was loaded for, so "loading" can be worked
// out during render instead of being set inside the effect.
type LoadResult =
  | { project: string; sessions: SessionResponse[]; error: null }
  | { project: string; sessions: null; error: string };

export default function SessionList() {
  const [result, setResult] = useState<LoadResult | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [searchParams] = useSearchParams();
  const project = searchParams.get("project");
  const navigate = useNavigate();

  useEffect(() => {
    if (!project) return;

    let ignore = false;
    fetchSessions(project)
      .then((data) => {
        if (!ignore) setResult({ project, sessions: data, error: null });
      })
      .catch(() => {
        if (!ignore) {
          setResult({
            project,
            sessions: null,
            error: "Couldn't load sessions. Make sure lore ui is still running, then refresh.",
          });
        }
      });
    return () => {
      ignore = true;
    };
  }, [project]);

  async function handleDelete(id: string) {
    if (!window.confirm(DELETE_CONFIRM)) return;
    setDeletingId(id);
    setDeleteError(null);
    try {
      await deleteSession(id);
      setResult((r) => (r && r.sessions ? { ...r, sessions: r.sessions.filter((s) => s.id !== id) } : r));
    } catch (e) {
      setDeleteError(e instanceof Error ? e.message : "Couldn't delete the session. Try again.");
    } finally {
      setDeletingId(null);
    }
  }

  const current = project && result?.project === project ? result : null;
  const loading = !!project && current === null;
  const sessions = current?.sessions ?? null;
  const error = current?.error ?? null;

  return (
    <div className="min-h-screen bg-paper text-ink">
    <Navbar project={project} />
    <div className="mx-auto w-full max-w-5xl px-4 sm:px-6 py-8">
      <h1 className="font-serif text-[28px] leading-tight font-semibold tracking-tight text-ink mb-6">Sessions</h1>

      {!project && (
        <div className="text-ink-3 text-center py-12">
          <p>
            No project selected. Run{" "}
            <code className="bg-sunk px-1.5 py-0.5 rounded text-sm font-mono">
              lore ui
            </code>{" "}
            from inside your project folder to see its sessions.
          </p>
        </div>
      )}

      {loading && (
        <div className="text-ink-3 text-center py-12">Loading sessions...</div>
      )}

      {error && (
        <div className="text-bad text-center py-12">{error}</div>
      )}

      {!loading && sessions && sessions.length === 0 && (
        <div className="text-ink-3 text-center py-12">
          <p>
            No sessions yet. Run{" "}
            <code className="bg-sunk px-1.5 py-0.5 rounded text-sm font-mono">
              lore start
            </code>{" "}
            in your terminal to record your first one.
          </p>
        </div>
      )}

      {deleteError && (
        <div role="alert" className="mb-3 text-sm text-bad">{deleteError}</div>
      )}

      {!loading && sessions && sessions.length > 0 && (
        <ul className="space-y-2">
          {sessions.map((session) => {
            const running = session.status === "ACTIVE";
            return (
              <li
                key={session.id}
                onClick={running ? undefined : () => navigate(`/sessions/${session.id}`)}
                className={`p-4 sm:px-5 border border-line rounded-card bg-surface transition-colors duration-150 flex items-start gap-4 ${
                  running ? "" : "cursor-pointer hover:border-line-strong hover:bg-sunk/50"
                }`}
              >
                <div className="flex-1 min-w-0">
                  <div className="font-serif text-[17px] leading-snug font-semibold text-ink break-words">
                    {running ? "Session in progress" : sessionTitle(session.reason)}
                  </div>
                  <div className="text-sm text-ink-3 flex gap-4 mt-1">
                    <span>{new Date(session.startedAt).toLocaleString()}</span>
                    {!running && <span>{session.fileCount} files</span>}
                    {!running && <span>{session.changeCount} changes</span>}
                  </div>
                  <div className="text-sm text-ink-3 mt-1">
                    {running ? (
                      <span className="inline-block px-2 py-0.5 rounded bg-warn-soft text-warn border border-warn/30">
                        Running. Run lore stop to review it.
                      </span>
                    ) : (
                      `${session.understoodCount} of ${session.changeCount} understood`
                    )}
                  </div>
                </div>
                <button
                  type="button"
                  disabled={running || deletingId === session.id}
                  title={running ? "Stop this session first with lore stop." : "Delete this session"}
                  onClick={(e) => {
                    // The row opens the session on click; this button must not.
                    e.stopPropagation();
                    handleDelete(session.id);
                  }}
                  className="shrink-0 text-sm px-3 py-1 rounded-control border border-line text-bad hover:bg-bad-soft hover:border-bad/30 disabled:text-ink-3 disabled:bg-surface disabled:border-line disabled:cursor-not-allowed"
                >
                  {deletingId === session.id ? "Deleting..." : "Delete"}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
    </div>
  );
}
