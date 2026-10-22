// The changes that use one concept, newest session first. Each opens its session on that
// exact change. Your note on a change shows under it. Shared by the Concepts table (an
// opened row) and the graph's side panel.

import { Link } from "react-router-dom";
import { changeUrl, sessionTitle } from "../api/client";
import type { ConceptGraphNode } from "../api/client";

export default function ConceptChanges({ changes }: { changes: ConceptGraphNode["changes"] }) {
  return (
    <ul className="space-y-2">
      {changes.map((c) => (
        <li key={c.changeId} className="rounded-md border border-gray-200 bg-white px-3 py-2 text-sm">
          <div className="flex items-start gap-2">
            <Link
              to={changeUrl(c.sessionId, c.filePath, c.changeId)}
              className="font-mono text-xs text-blue-600 hover:underline break-all"
            >
              {c.filePath}
            </Link>
            {c.understood ? (
              <span className="ml-auto shrink-0 rounded-full border border-green-300 bg-green-50 px-2 py-0.5 text-[11px] text-green-700">
                ✓ Understood
              </span>
            ) : (
              <span className="ml-auto shrink-0 rounded-full border border-orange-200 bg-orange-50 px-2 py-0.5 text-[11px] text-orange-700">
                Not yet
              </span>
            )}
          </div>
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
