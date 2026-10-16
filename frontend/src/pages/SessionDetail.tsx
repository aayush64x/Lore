import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { fetchSession, retrySummary, toggleUnderstood } from "../api/client";
import type { SessionDetailResponse, ChangeResponse } from "../api/client";
import DiffView from "../components/DiffView";
import ExplainWalkthrough from "../components/ExplainWalkthrough";
import ProgressBar from "../components/ProgressBar";

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
      <div className="max-w-3xl mx-auto px-4 py-6 text-gray-500">Loading session...</div>
    );
  }

  if (error || !session) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-6">
        <button onClick={() => navigate("/")} className="text-blue-600 text-sm hover:underline mb-4">
          ← Back to sessions
        </button>
        <p className="text-red-600">{error ?? "Session not found."}</p>
      </div>
    );
  }

  // Flip locally first so the button feels instant; flip back if the server says no.
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

  function handleRetrySummary() {
    if (!id) return;
    setRetrying(true);
    setRetryError(null);
    retrySummary(id)
      .then((data) => setSession(data))
      .catch((err: Error) => setRetryError(err.message))
      .finally(() => setRetrying(false));
  }

  const visibleChanges = unreviewedOnly
    ? session.changes.filter((c) => !c.understood)
    : session.changes;

  const changesByFile: Record<string, ChangeResponse[]> = {};
  for (const change of visibleChanges) {
    const key = change.filePath;
    if (!changesByFile[key]) changesByFile[key] = [];
    changesByFile[key].push(change);
  }

  const totalChanges = session.changes.length;
  const understoodChanges = session.changes.filter((c) => c.understood).length;

  return (
    <div className="max-w-3xl mx-auto px-4 py-6">
      <button onClick={() => navigate(-1)} className="text-blue-600 text-sm hover:underline inline-block mb-4">
        ← Back to sessions
      </button>

      <div className="mb-4">
        <h1 className="text-xl font-semibold">{session.reason ?? "Untitled session"}</h1>
        <div className="text-sm text-gray-500">{new Date(session.startedAt).toLocaleString()}</div>
      </div>

      <div className="mb-4">
        <ProgressBar understood={understoodChanges} total={totalChanges} />
      </div>

      {session.summary ? (
        <div className="bg-white border border-gray-200 rounded-lg p-4 mb-6">
          <p className="text-gray-700">{session.summary}</p>
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
          <h2 className="text-lg font-semibold">Changes</h2>
          <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
            <input
              type="checkbox"
              checked={unreviewedOnly}
              onChange={(e) => setUnreviewedOnly(e.target.checked)}
            />
            Unreviewed only
          </label>
        </div>

        {unreviewedOnly && visibleChanges.length === 0 && (
          <p className="text-sm text-gray-500 py-6 text-center">
            Nothing left to review. You've marked every change as understood.
          </p>
        )}

        {Object.entries(changesByFile).map(([filePath, changes]) => (
          <div key={filePath} className="mb-6">
            <h3 className="font-mono text-sm font-medium mb-2">{filePath}</h3>

            {changes.map((change) => (
              <div key={change.id} className="bg-white border border-gray-200 rounded-lg mb-2 overflow-hidden">
                <div className="px-4 py-3 flex gap-3 items-center text-sm">
                  <span className="text-gray-500 font-mono text-xs">
                    Lines {change.newStart}–{change.newStart + Math.max(change.newCount - 1, 0)}
                  </span>
                  <span className="flex-1">{change.description ?? change.changeType}</span>
                  <button
                    onClick={() => handleToggleUnderstood(change.id)}
                    className={`text-xs px-2 py-1 rounded ${
                      change.understood
                        ? "bg-green-100 text-green-700 hover:bg-green-200"
                        : "bg-gray-100 text-gray-500 hover:bg-gray-200"
                    } transition-colors`}
                  >
                    {change.understood ? "Understood" : "Not understood"}
                  </button>
                </div>
                <DiffView diffContent={change.diffContent} />
                {!change.binary && (
                  <div className="px-4 py-3 border-t border-gray-100 text-sm">
                    <ExplainWalkthrough changeId={change.id} />
                  </div>
                )}
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
