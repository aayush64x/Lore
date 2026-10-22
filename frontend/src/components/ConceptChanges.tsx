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
        <li key={c.changeId} className="rounded-control border border-line bg-surface px-3.5 py-3 text-sm">
          <div className="flex items-start gap-2">
            <Link
              to={changeUrl(c.sessionId, c.filePath, c.changeId)}
              className="font-mono text-xs text-accent underline-offset-2 hover:underline break-all"
            >
              {c.filePath}
            </Link>
            {c.understood ? (
              <span className="ml-auto shrink-0 rounded-full border border-ok/30 bg-ok-soft px-2 py-0.5 text-[11px] font-medium text-ok">
                ✓ Understood
              </span>
            ) : (
              <span className="ml-auto shrink-0 rounded-full border border-warn/30 bg-warn-soft px-2 py-0.5 text-[11px] font-medium text-warn">
                Not yet
              </span>
            )}
          </div>
          <div className="mt-0.5 text-xs text-ink-3">{sessionTitle(c.sessionReason)}</div>
          {c.description && <p className="mt-1.5 leading-relaxed text-ink">{c.description}</p>}
          {c.note && (
            <p className="mt-2 whitespace-pre-wrap border-l-2 border-accent/50 pl-3 text-ink-2">
              <span className="text-xs font-medium text-ink-3">My note: </span>
              {c.note}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}
