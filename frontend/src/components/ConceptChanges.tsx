// The changes that use one concept, newest session first. Each opens its session on that
// exact change. Your note on a change shows under it. Shared by the Concepts table (an
// opened row) and the graph's side panel.

import { Link } from "react-router-dom";
import { changeUrl, sessionTitle } from "../api/client";
import type { ConceptGraphNode } from "../api/client";

export default function ConceptChanges({ changes }: { changes: ConceptGraphNode["changes"] }) {
  return (
    <ul className="space-y-3">
      {changes.map((c) => (
        <li key={c.changeId} className="text-sm">
          <Link
            to={changeUrl(c.sessionId, c.filePath, c.changeId)}
            className="font-mono text-xs text-blue-600 hover:underline break-all"
          >
            {c.filePath}
          </Link>
          {c.understood ? (
            <span className="ml-1.5 text-xs text-green-700">✓ understood</span>
          ) : (
            <span className="ml-1.5 text-xs text-gray-500">not yet</span>
          )}
          <div className="text-xs text-gray-500">{sessionTitle(c.sessionReason)}</div>
          {c.description && <p className="mt-0.5 text-gray-700">{c.description}</p>}
          {c.note && (
            <p className="mt-1 whitespace-pre-wrap rounded border-l-2 border-blue-300 bg-blue-50/60 px-2 py-1 text-gray-800">
              <span className="text-xs font-medium text-blue-700">My note: </span>
              {c.note}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}
