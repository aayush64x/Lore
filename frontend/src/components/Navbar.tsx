// Navbar — the bar at the top of every page.
//
// Shows "Lore" (goes to the session list), a Concepts button, and a breadcrumb trail:
//   project › session title › file
// Every crumb with a link is clickable. Long names are cut off with "…";
// hover one to see it in full. Pages can add their own items in the
// middle and on the right. A soft shadow shows once the page is scrolled.

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { conceptsUrl, sessionsUrl } from "../api/client";
import ThemeToggle from "./ThemeToggle";

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


// "/Users/me/code/my-app" → "my-app" (also works with Windows "\").
function projectName(project: string): string {
  return project.split(/[\\/]/).filter(Boolean).pop() ?? project;
}

export default function Navbar({ project, crumbs = [], center, right, scrolled = false }: NavbarProps) {
  const [windowScrolled, setWindowScrolled] = useState(false);
  const onConcepts = useLocation().pathname === "/concepts";

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
      {/* basis: room for the Concepts pill and the breadcrumbs before the rest wraps below. */}
      <nav aria-label="Breadcrumb" className="flex items-center gap-3 min-w-0 flex-1 basis-[28rem]">
        <Link to={sessionsUrl(project)} className="font-semibold text-base text-gray-900 shrink-0">
          Lore
        </Link>

        {project && (
          // A pill next to the logo, in the same place on every page; solid while you're on it.
          <Link
            to={conceptsUrl(project)}
            aria-current={onConcepts ? "page" : undefined}
            className={`shrink-0 inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm font-medium transition-colors ${
              onConcepts
                ? "bg-blue-600 border-blue-600 text-white"
                : "bg-blue-50 border-blue-200 text-blue-700 hover:bg-blue-100 hover:border-blue-300"
            }`}
          >
            <svg viewBox="0 0 20 20" fill="currentColor" className="w-4 h-4" aria-hidden="true">
              <path d="M10 4.5C8.6 3.6 6.8 3 5 3c-1 0-2 .2-3 .5v12c1-.3 2-.5 3-.5 1.8 0 3.6.6 5 1.5V4.5zM11 4.5v12c1.4-.9 3.2-1.5 5-1.5 1 0 2 .2 3 .5v-12C18 3.2 17 3 16 3c-1.8 0-3.6.6-5 1.5z" />
            </svg>
            Concepts
          </Link>
        )}

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
      <div className={right ? "shrink-0" : "shrink-0 ml-auto"}>
        <ThemeToggle />
      </div>
      </div>
    </header>
  );
}
