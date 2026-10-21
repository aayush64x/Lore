import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import { fetchSession, retrySummary, sessionDetails, sessionTitle } from "../api/client";
import type { SessionDetailResponse, ChangeResponse } from "../api/client";
import ProgressBar from "../components/ProgressBar";

const CHANGE_TYPE_LABELS: Record<string, string> = {
  ADDED: "New file",
  MODIFIED: "Edited",
  DELETED: "Deleted",
  RENAMED: "Renamed",
};

export default function SessionDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [session, setSession] = useState<SessionDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [retryError, setRetryError] = useState<string | null>(null);
  const [unreviewedOnly, setUnreviewedOnly] = useState(false);

  useEffect(() => {
    if (!id) return;

    fetchSession(id)
      .then((data) => setSession(data))
      .catch(() => setError("Session not found."))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <>
        <Navbar project={null} />
        <div className="max-w-3xl mx-auto px-4 py-6 text-gray-500">Loading session...</div>
      </>
    );
  }

  if (error || !session) {
    return (
      <>
        <Navbar project={null} crumbs={[{ label: "Session not found" }]} />
        <div className="max-w-3xl mx-auto px-4 py-6">
          <p className="text-red-600">{error ?? "Session not found."}</p>
        </div>
      </>
    );
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

  const changesByFile: Record<string, ChangeResponse[]> = {};
  for (const change of session.changes) {
    const key = change.filePath;
    if (!changesByFile[key]) changesByFile[key] = [];
    changesByFile[key].push(change);
  }

  // A file counts as reviewed once every change in it is understood.
  const files = Object.entries(changesByFile).map(([filePath, changes]) => ({
    filePath,
    changeType: changes[0].changeType,
    total: changes.length,
    understood: changes.filter((c) => c.understood).length,
  }));
  const visibleFiles = unreviewedOnly ? files.filter((f) => f.understood < f.total) : files;

  const totalChanges = session.changes.length;
  const understoodChanges = session.changes.filter((c) => c.understood).length;

  return (
    <>
    <Navbar project={session.projectPath} crumbs={[{ label: sessionTitle(session.reason) }]} />
    <div className="max-w-3xl mx-auto px-4 py-6">

      <div className="mb-4">
        <h1 className="text-xl font-semibold break-words">{sessionTitle(session.reason)}</h1>
        <div className="text-sm text-gray-500">{new Date(session.startedAt).toLocaleString()}</div>
        {sessionDetails(session.reason) && (
          <p className="mt-3 text-sm text-gray-700 whitespace-pre-wrap break-words">
            {sessionDetails(session.reason)}
          </p>
        )}
      </div>

      <div className="mb-4">
        <ProgressBar understood={understoodChanges} total={totalChanges} />
      </div>

      {session.summary ? (
        <div className="bg-white border border-gray-200 rounded-lg p-4 mb-6">
          <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">AI summary</div>
          <p className="text-sm text-gray-500">{session.summary}</p>
        </div>
      ) : (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-4 mb-6 text-sm text-gray-500">
          <p>
            {session.summaryStatus === "FAILED"
              ? "The AI summary couldn't be created for this session."
              : "No AI summary for this session yet."}
          </p>
          {retryError && <p className="text-red-600 mt-2">{retryError}</p>}
          <button
            onClick={handleRetrySummary}
            disabled={retrying}
            className="mt-2 text-blue-600 hover:underline disabled:text-gray-400 disabled:no-underline"
          >
            {retrying ? "Creating summary..." : "Retry"}
          </button>
        </div>
      )}

      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg font-semibold">
            Files changed <span className="text-gray-500 font-normal">({files.length})</span>
          </h2>
          <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
            <input
              type="checkbox"
              checked={unreviewedOnly}
              onChange={(e) => setUnreviewedOnly(e.target.checked)}
            />
            Unreviewed only
          </label>
        </div>

        {unreviewedOnly && visibleFiles.length === 0 && (
          <p className="text-sm text-gray-500 py-6 text-center">
            Nothing left to review. You've marked every change as understood.
          </p>
        )}

        <ul className="space-y-2">
          {visibleFiles.map((file) => {
            const done = file.understood === file.total;
            return (
              <li key={file.filePath}>
                <button
                  onClick={() => navigate(`/sessions/${id}/file?path=${encodeURIComponent(file.filePath)}`)}
                  className="w-full text-left p-4 border border-gray-200 rounded-lg bg-white hover:border-blue-500 transition-colors flex items-center gap-3"
                >
                  <span className="flex-1 min-w-0">
                    <span className="block font-mono text-sm font-medium break-all">{file.filePath}</span>
                    <span className="block text-xs text-gray-500 mt-1">
                      {CHANGE_TYPE_LABELS[file.changeType] ?? file.changeType} ·{" "}
                      {file.total} {file.total === 1 ? "change" : "changes"}
                    </span>
                  </span>
                  <span
                    className={`text-xs px-2 py-1 rounded whitespace-nowrap ${
                      done ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"
                    }`}
                  >
                    {done ? "✓ Understood" : `${file.understood} of ${file.total} understood`}
                  </span>
                  <span className="text-gray-400" aria-hidden="true">›</span>
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
    </>
  );
}
