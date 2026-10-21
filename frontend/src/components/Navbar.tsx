// Navbar — the bar at the top of every page.
//
// Shows "Lore" (goes to the session list) and a breadcrumb trail:
//   project › session title › file
// Every crumb but the last is a link. Long names are cut off with "…";
// hover one to see it in full.

import { Link } from "react-router-dom";

export interface Crumb {
  label: string;
  to?: string;
  mono?: boolean; // file paths use the code font
}

interface NavbarProps {
  project: string | null; // full project folder path, if known
  crumbs?: Crumb[];       // after the project crumb
}

// The session list's address for a project.
function sessionsUrl(project: string | null): string {
  return project ? `/?project=${encodeURIComponent(project)}` : "/";
}

// "/Users/me/code/my-app" → "my-app" (also works with Windows "\").
function projectName(project: string): string {
  return project.split(/[\\/]/).filter(Boolean).pop() ?? project;
}

export default function Navbar({ project, crumbs = [] }: NavbarProps) {
  const all: Crumb[] = [
    ...(project ? [{ label: projectName(project), to: sessionsUrl(project) }] : []),
    ...crumbs,
  ];

  return (
    <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
      <nav
        aria-label="Breadcrumb"
        className="max-w-3xl mx-auto px-4 h-12 flex items-center gap-3 text-sm"
      >
        <Link to={sessionsUrl(project)} className="font-semibold text-base text-gray-900 shrink-0">
          Lore
        </Link>

        {all.length > 0 && <span className="h-5 w-px bg-gray-200 shrink-0" aria-hidden="true" />}

        <ol className="flex items-center gap-1.5 min-w-0">
          {all.map((crumb, i) => {
            const last = i === all.length - 1;
            const text = `truncate ${crumb.mono ? "font-mono text-xs" : ""}`;
            return (
              <li key={i} className={`flex items-center gap-1.5 min-w-0 ${last ? "" : "shrink-[2]"}`}>
                {i > 0 && <span className="text-gray-400 shrink-0" aria-hidden="true">›</span>}
                {crumb.to && !last ? (
                  <Link to={crumb.to} title={crumb.label} className={`${text} text-blue-600 hover:underline`}>
                    {crumb.label}
                  </Link>
                ) : (
                  <span title={crumb.label} aria-current={last ? "page" : undefined} className={`${text} text-gray-700`}>
                    {crumb.label}
                  </span>
                )}
              </li>
            );
          })}
        </ol>
      </nav>
    </header>
  );
}
