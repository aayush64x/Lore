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
  // Wide screens only: a thin rail with just the status dots. Narrow screens always
  // show the full list, since the sidebar sits above the diff there.
  collapsed: boolean;
  onToggle: () => void;
}

// "src/pages/App.tsx" → name "App.tsx", folder "src/pages" (works with "\" too).
function splitPath(path: string): { name: string; folder: string } {
  const i = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  return i < 0 ? { name: path, folder: "" } : { name: path.slice(i + 1), folder: path.slice(0, i) };
}

function Chevron({ left }: { left: boolean }) {
  return (
    <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d={left ? "M10 3 5 8l5 5" : "M6 3l5 5-5 5"} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function FileIcon() {
  return (
    <svg viewBox="0 0 16 16" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.25">
      <path d="M4 1.5h5.5L13 5v9.5H4z" strokeLinejoin="round" />
      <path d="M9.5 1.5V5H13" strokeLinejoin="round" />
    </svg>
  );
}

const toggleButton = "p-1 rounded text-gray-400 hover:text-gray-800 hover:bg-gray-100 transition-colors";

// Left column of the session page: every changed file, then the session's concepts.
// Collapsed (wide screens): a thin rail with a file count and one status dot per file.
export default function FileSidebar({ files, selected, onSelect, concepts, collapsed, onToggle }: FileSidebarProps) {
  const [openConcept, setOpenConcept] = useState<string | null>(null);
  const concept = concepts.find((c) => c.name === openConcept);

  return (
    <>
    {collapsed && (
      <nav aria-label="Changed files" className="hidden min-[1000px]:flex flex-col items-center gap-3 py-3 w-[52px]">
        <button onClick={onToggle} title="Show files (b)" aria-label="Show files" aria-expanded="false" className={toggleButton}>
          <Chevron left={false} />
        </button>
        <span className="relative text-gray-500" title={`${files.length} ${files.length === 1 ? "file" : "files"}`}>
          <FileIcon />
          <span className="absolute -top-1.5 -right-2 min-w-4 h-4 px-1 rounded-full bg-gray-700 text-white text-[10px] leading-4 text-center">
            {files.length}
          </span>
        </span>
        <ul className="flex flex-col items-center gap-1">
          {files.map((file) => {
            const done = file.understood === file.total;
            const active = file.path === selected;
            return (
              <li key={file.path}>
                <button
                  onClick={() => onSelect(file.path)}
                  title={`${file.path} · ${done ? "understood" : "not understood yet"}`}
                  aria-label={file.path}
                  aria-current={active ? "page" : undefined}
                  className={`w-8 h-7 flex items-center justify-center rounded ${active ? "bg-blue-50 ring-1 ring-blue-300" : "hover:bg-gray-100"}`}
                >
                  <span className={`w-2 h-2 rounded-full ${done ? "bg-green-500" : "bg-orange-400"}`} />
                </button>
              </li>
            );
          })}
        </ul>
      </nav>
    )}
    {/* min-w keeps the text from re-wrapping while the column animates; the aside clips it. */}
    <nav aria-label="Changed files" className={`py-3 min-[1000px]:min-w-[280px] ${collapsed ? "min-[1000px]:hidden" : ""}`}>
      <div className="px-4 pb-2 flex items-center justify-between">
        <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
          Files <span className="font-normal">({files.length})</span>
        </span>
        <button
          onClick={onToggle}
          title="Hide files (b)"
          aria-label="Hide files"
          aria-expanded="true"
          className={`hidden min-[1000px]:block ${toggleButton}`}
        >
          <Chevron left={true} />
        </button>
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
    </>
  );
}
