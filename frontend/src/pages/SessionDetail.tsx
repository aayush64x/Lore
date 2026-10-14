import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { fetchSession } from "../api/client";
import type { SessionDetailResponse } from "../api/client";
import DiffView from "../components/DiffView";
import ExplainWalkthrough from "../components/ExplainWalkthrough";

export default function SessionDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [session, setSession] = useState<SessionDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    fetchSession(id)
      .then((data) => setSession(data))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <div>Loading...</div>;
  if (!session) return <div>Session not found.</div>;

  return (
    <div>
      <button onClick={() => navigate(-1)}>← Back</button>
      <h1>{session.reason ?? "Untitled session"}</h1>

      {session.changes.map((change) => (
        <div key={change.id}>
          <h3>{change.filePath}</h3>
          <DiffView diffContent={change.diffContent} />
          <ExplainWalkthrough changeId={change.id} />
        </div>
      ))}
    </div>
  );
}
