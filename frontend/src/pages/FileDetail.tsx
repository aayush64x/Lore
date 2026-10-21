// File page — every change in one file of a session.
//
// URL: /sessions/:id/file?path=src/App.tsx
// The path is a query param because file paths contain slashes.

import { useState, useEffect } from "react";
import { useParams, useNavigate, useSearchParams, useLocation } from "react-router-dom";
import { fetchSession, sessionTitle, toggleUnderstood } from "../api/client";
import type { SessionDetailResponse } from "../api/client";
import ChangeCard from "../components/ChangeCard";
import ProgressBar from "../components/ProgressBar";
import Navbar from "../components/Navbar";

export default function FileDetail() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const filePath = searchParams.get("path");
  const navigate = useNavigate();
  const location = useLocation();
  const [session, setSession] = useState<SessionDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [highlight, setHighlight] = useState<{ changeId: string; start: number; end: number } | null>(null);

  useEffect(() => {
    if (!id) return;

    fetchSession(id)
      .then((data) => setSession(data))
      .catch(() => setError("Session not found."))
      .finally(() => setLoading(false));
  }, [id]);

  // Go back to the file list. If this page was opened directly (no history in
  // the app), there's nothing to go back to, so open the session page instead.
  function backToFiles() {
    if (location.key !== "default") navigate(-1);
    else navigate(`/sessions/${id}`);
  }

  // project › session title › this file
  const navbar = (
    <Navbar
      project={session?.projectPath ?? null}
      crumbs={[
        ...(session ? [{ label: sessionTitle(session.reason), to: `/sessions/${id}` }] : []),
        ...(filePath ? [{ label: filePath, mono: true }] : []),
      ]}
    />
  );

  if (loading) {
    return (
      <>
        {navbar}
        <div className="max-w-3xl mx-auto px-4 py-6 text-gray-500">Loading file...</div>
      </>
    );
  }

  const changes = session?.changes.filter((c) => c.filePath === filePath) ?? [];

  if (error || !session || changes.length === 0) {
    return (
      <>
        {navbar}
        <div className="max-w-3xl mx-auto px-4 py-6">
          <p className="text-red-600">{error ?? "This file has no changes in this session."}</p>
        </div>
      </>
    );
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

  // Files in the same order as the file list, so "Next file" matches it.
  const filePaths = [...new Set(session.changes.map((c) => c.filePath))];
  const nextFile = filePaths[filePaths.indexOf(filePath!) + 1];
  const understood = changes.filter((c) => c.understood).length;
  const oldFilePath = changes.find((c) => c.oldFilePath)?.oldFilePath;

  return (
    <>
    {navbar}
    <div className="max-w-3xl mx-auto px-4 py-6">
      <div className="mb-4">
        <h1 className="font-mono text-lg font-semibold break-all">{filePath}</h1>
        {oldFilePath && (
          <div className="text-sm text-gray-500 font-mono break-all">Renamed from {oldFilePath}</div>
        )}
      </div>

      <div className="mb-4">
        <ProgressBar understood={understood} total={changes.length} />
      </div>

      {changes.map((change) => (
        <ChangeCard
          key={change.id}
          change={change}
          onToggleUnderstood={() => handleToggleUnderstood(change.id)}
          highlight={highlight?.changeId === change.id ? highlight : null}
          onHighlight={(start, end) => setHighlight({ changeId: change.id, start, end })}
          onClearHighlight={() => setHighlight(null)}
        />
      ))}

      <div className="mt-6 flex justify-end text-sm">
        {nextFile ? (
          // replace: Back still returns to the file list, not the previous file.
          <button
            onClick={() =>
              navigate(`/sessions/${id}/file?path=${encodeURIComponent(nextFile)}`, { replace: true })
            }
            className="text-blue-600 hover:underline font-mono break-all text-right"
          >
            Next file: {nextFile} →
          </button>
        ) : (
          <button onClick={backToFiles} className="text-blue-600 hover:underline">
            That's the last file. Back to all files
          </button>
        )}
      </div>
    </div>
    </>
  );
}
