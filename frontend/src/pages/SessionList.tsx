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
import { fetchSessions } from "../api/client";
import type { SessionResponse } from "../api/client";

export default function SessionList() {
  const [loading, setLoading] = useState<boolean>(false);
  const [sessions, setSessions] = useState<SessionResponse[] | null>(null);

  const [searchParams] = useSearchParams();
  const project = searchParams.get("project");
  const navigate = useNavigate();

  useEffect(() => {
    if (!project) return;

    setLoading(true);
    fetchSessions(project)
      .then((data) => setSessions(data))
      .finally(() => setLoading(false));
  }, [project]);

  return (
    <div className="max-w-3xl mx-auto px-4 py-6">
      <h1 className="text-2xl font-semibold mb-6">Lore</h1>

      {loading && (
        <div className="text-gray-500 text-center py-12">Loading sessions...</div>
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
              <div className="font-medium">{session.reason}</div>
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
