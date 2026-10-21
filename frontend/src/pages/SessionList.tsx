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
// NAVIGATION: Clicking a row goes to /sessions/:id

import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { fetchSessions, sessionTitle } from "../api/client";
import type { SessionResponse } from "../api/client";

// The result remembers which project it was loaded for, so "loading" can be worked
// out during render instead of being set inside the effect.
type LoadResult =
  | { project: string; sessions: SessionResponse[]; error: null }
  | { project: string; sessions: null; error: string };

export default function SessionList() {
  const [result, setResult] = useState<LoadResult | null>(null);

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

  const current = project && result?.project === project ? result : null;
  const loading = !!project && current === null;
  const sessions = current?.sessions ?? null;
  const error = current?.error ?? null;

  return (
    <div className="max-w-3xl mx-auto px-4 py-6">
      <h1 className="text-2xl font-semibold mb-6">Lore</h1>

      {!project && (
        <div className="text-gray-500 text-center py-12">
          <p>
            No project selected. Run{" "}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm font-mono">
              lore ui
            </code>{" "}
            from inside your project folder to see its sessions.
          </p>
        </div>
      )}

      {loading && (
        <div className="text-gray-500 text-center py-12">Loading sessions...</div>
      )}

      {error && (
        <div className="text-red-600 text-center py-12">{error}</div>
      )}

      {!loading && sessions && sessions.length === 0 && (
        <div className="text-gray-500 text-center py-12">
          <p>
            No sessions yet. Run{" "}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm font-mono">
              lore start
            </code>{" "}
            in your terminal to record your first one.
          </p>
        </div>
      )}

      {!loading && sessions && sessions.length > 0 && (
        <ul className="space-y-2">
          {sessions.map((session) => (
            <li
              key={session.id}
              onClick={() => navigate(`/sessions/${session.id}`)}
              className="p-4 border border-gray-200 rounded-lg cursor-pointer bg-white hover:border-blue-500 transition-colors"
            >
              <div className="font-medium break-words">{sessionTitle(session.reason)}</div>
              <div className="text-sm text-gray-500 flex gap-4 mt-1">
                <span>{new Date(session.startedAt).toLocaleString()}</span>
                <span>{session.fileCount} files</span>
                <span>{session.changeCount} changes</span>
              </div>
              <div className="text-sm text-gray-500 mt-1">
                {session.understoodCount} of {session.changeCount} understood
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
