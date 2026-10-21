// Navbar — the bar at the top of every page.
//
// Shows "Lore" (goes to the session list) and a breadcrumb trail:
//   project › session title › file
// Every crumb with a link is clickable. Long names are cut off with "…";
// hover one to see it in full. Pages can add their own items in the
// middle and on the right. A soft shadow shows once the page is scrolled.

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export interface Crumb {
  label: string;
  to?: string;
  mono?: boolean; // file paths use the code font
}

interface NavbarProps {
  project: string | null; // full project folder path, if known
  crumbs?: Crumb[];       // after the project crumb
  center?: ReactNode;
  right?: ReactNode;
  // Show the shadow even if the window isn't scrolled (a page's own scroll box is).
  scrolled?: boolean;
}

// The session list's address for a project.
function sessionsUrl(project: string | null): string {
  return project ? `/?project=${encodeURIComponent(project)}` : "/";
}

// "/Users/me/code/my-app" → "my-app" (also works with Windows "\").
function projectName(project: string): string {
  return project.split(/[\\/]/).filter(Boolean).pop() ?? project;
}

export default function Navbar({ project, crumbs = [], center, right, scrolled = false }: NavbarProps) {
  const [windowScrolled, setWindowScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setWindowScrolled(window.scrollY > 0);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const all: Crumb[] = [
    ...(project ? [{ label: projectName(project), to: sessionsUrl(project) }] : []),
    ...crumbs,
  ];

  return (
    <header
      className={`bg-white border-b border-gray-200 sticky top-0 z-10 transition-shadow ${
        scrolled || windowScrolled ? "shadow-sm" : ""
      }`}
    >
      <div
        className={`px-6 min-h-12 py-2 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm`}
      >
      <nav aria-label="Breadcrumb" className="flex items-center gap-3 min-w-0 flex-1 basis-80">
        <Link to={sessionsUrl(project)} className="font-semibold text-base text-gray-900 shrink-0">
          Lore
        </Link>

        {all.length > 0 && <span className="h-5 w-px bg-gray-200 shrink-0" aria-hidden="true" />}

        <ol className="flex items-center gap-1.5 min-w-0">
          {all.map((crumb, i) => {
            const last = i === all.length - 1;
            // The project and the file stay whole (up to a limit); the session
            // title in between is what gets cut short when space runs out.
            const edge = i === 0 || last;
            const text = `truncate ${crumb.mono ? "font-mono text-xs" : ""}`;
            return (
              <li key={i} className={`flex items-center gap-1.5 min-w-0 ${edge ? "shrink-0 max-w-[16rem]" : "shrink"}`}>
                {i > 0 && <span className="text-gray-400 shrink-0" aria-hidden="true">›</span>}
                {crumb.to ? (
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
      {center && <div className="shrink-0">{center}</div>}
      {right && <div className="shrink-0 ml-auto">{right}</div>}
      </div>
    </header>
  );
}
