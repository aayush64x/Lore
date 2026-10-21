import { useState } from "react";
import type { ExplanationConcept } from "../api/client";

export interface FileSummary {
  path: string;
  total: number;
  understood: number;
  added: number;
  removed: number;
}

interface FileSidebarProps {
  files: FileSummary[];
  selected: string | null;
  onSelect: (path: string) => void;
  // Gathered from every saved explanation in the session. Hidden when empty.
  concepts: ExplanationConcept[];
}

// "src/pages/App.tsx" → name "App.tsx", folder "src/pages" (works with "\" too).
function splitPath(path: string): { name: string; folder: string } {
  const i = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  return i < 0 ? { name: path, folder: "" } : { name: path.slice(i + 1), folder: path.slice(0, i) };
}

// Left column of the session page: every changed file, then the session's concepts.
export default function FileSidebar({ files, selected, onSelect, concepts }: FileSidebarProps) {
  const [openConcept, setOpenConcept] = useState<string | null>(null);
  const concept = concepts.find((c) => c.name === openConcept);

  return (
    <nav aria-label="Changed files" className="py-3">
      <div className="px-4 pb-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
        Files <span className="font-normal">({files.length})</span>
      </div>
      {files.length === 0 && (
        <p className="px-4 py-2 text-sm text-gray-500">Nothing left to review.</p>
      )}
      <ul>
        {files.map((file) => {
          const done = file.understood === file.total;
          const { name, folder } = splitPath(file.path);
          const active = file.path === selected;
          return (
            <li key={file.path}>
              <button
                onClick={() => onSelect(file.path)}
                title={file.path}
                aria-current={active ? "page" : undefined}
                className={`w-full text-left px-4 py-2 flex items-center gap-2.5 border-l-[3px] transition-colors ${
                  active ? "bg-blue-50 border-blue-500" : "border-transparent hover:bg-gray-50"
                }`}
              >
                <span
                  className={`w-2 h-2 rounded-full shrink-0 ${done ? "bg-green-500" : "bg-orange-400"}`}
                  aria-label={done ? "Understood" : "Not understood yet"}
                />
                <span className="flex-1 min-w-0">
                  <span className="block font-mono text-sm truncate text-gray-900">{name}</span>
                  {folder && <span className="block font-mono text-[11px] text-gray-400 truncate">{folder}</span>}
                </span>
                <span className="font-mono text-[11px] whitespace-nowrap">
                  <span className="text-green-700">+{file.added}</span>{" "}
                  <span className="text-red-700">−{file.removed}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ul>

      {concepts.length > 0 && (
        <div className="px-4 pt-5 mt-3 border-t border-gray-100">
          <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Concepts in this session</div>
          <div className="flex flex-wrap gap-1.5">
            {concepts.map((c) => (
              <button
                key={c.name}
                onClick={() => setOpenConcept(openConcept === c.name ? null : c.name)}
                title={c.meaning}
                className={`text-xs px-2 py-0.5 rounded-full border transition-colors ${
                  openConcept === c.name
                    ? "bg-gray-800 text-white border-gray-800"
                    : "bg-gray-50 text-gray-700 border-gray-200 hover:bg-gray-100"
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
          {concept && <p className="text-xs text-gray-600 mt-2">{concept.meaning}</p>}
        </div>
      )}
    </nav>
  );
}
