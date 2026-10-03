import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { fetchSession } from "../api/client";
import type { SessionDetailResponse, ChangeResponse } from "../api/client";
import DiffView from "../components/DiffView";
import ProgressBar from "../components/ProgressBar";

export default function SessionDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [session, setSession] = useState<SessionDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;

    setLoading(true);
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

  const changesByFile: Record<string, ChangeResponse[]> = {};
  for (const change of session.changes) {
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

      {session.summary && (
        <div className="bg-white border border-gray-200 rounded-lg p-4 mb-6">
          <p className="text-gray-700">{session.summary}</p>
        </div>
      )}

      <div>
        <h2 className="text-lg font-semibold mb-3">Changes</h2>

        {Object.entries(changesByFile).map(([filePath, changes]) => (
          <div key={filePath} className="mb-6">
            <h3 className="font-mono text-sm font-medium mb-2">{filePath}</h3>

            {changes.map((change) => (
              <div key={change.id} className="bg-white border border-gray-200 rounded-lg mb-2 overflow-hidden">
                <div className="px-4 py-3 flex gap-3 items-center text-sm">
                  <span className="text-gray-500 font-mono text-xs">
                    Lines {change.newStart}–{change.newStart + change.newCount}
                  </span>
                  <span className="flex-1">{change.description ?? change.changeType}</span>
                  <span className={`text-xs ${change.understood ? "text-green-600" : "text-gray-400"}`}>
                    {change.understood ? "Understood" : "Not understood"}
                  </span>
                </div>
                <DiffView diffContent={change.diffContent} />
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
