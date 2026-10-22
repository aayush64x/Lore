// Navbar — the bar at the top of every page.
//
// Shows "Lore" (goes to the session list), Review and Concepts buttons, and a breadcrumb trail:
//   project › session title › file
// Every crumb with a link is clickable. Long names are cut off with "…";
// hover one to see it in full. Pages can add their own items in the
// middle and on the right. A soft shadow shows once the page is scrolled.
// On a phone the breadcrumbs get their own line (cut short to fit) and the
// light/dark switch moves up next to the buttons, so nothing sticks out sideways.

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { REVIEW_CHANGED, conceptsUrl, fetchReviewCount, reviewUrl, sessionsUrl } from "../api/client";
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


const BOOK = (
  <path d="M10 4.5C8.6 3.6 6.8 3 5 3c-1 0-2 .2-3 .5v12c1-.3 2-.5 3-.5 1.8 0 3.6.6 5 1.5V4.5zM11 4.5v12c1.4-.9 3.2-1.5 5-1.5 1 0 2 .2 3 .5v-12C18 3.2 17 3 16 3c-1.8 0-3.6.6-5 1.5z" />
);
const CLOCK = (
  <path
    fillRule="evenodd"
    d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16zm.75-12.25a.75.75 0 0 0-1.5 0V10c0 .2.08.39.22.53l2.5 2.5a.75.75 0 1 0 1.06-1.06l-2.28-2.28V5.75z"
    clipRule="evenodd"
  />
);

// A pill next to the logo, in the same place on every page; solid while you're on its page.
// badge: a count shown in a small circle (hidden at 0).
function NavPill({ to, active, label, icon, badge = 0 }: { to: string; active: boolean; label: string; icon: ReactNode; badge?: number }) {
  return (
    <Link
      to={to}
      aria-current={active ? "page" : undefined}
      aria-label={badge > 0 ? `${label}, ${badge} due` : label}
      className={`shrink-0 inline-flex items-center gap-1.5 rounded-control border px-3 py-1 text-sm font-medium transition-colors duration-150 ${
        active
          ? "bg-accent border-accent text-on-accent"
          : "bg-surface border-line text-ink-2 hover:bg-sunk hover:text-ink"
      }`}
    >
      <svg viewBox="0 0 20 20" fill="currentColor" className="w-4 h-4" aria-hidden="true">
        {icon}
      </svg>
      {label}
      {badge > 0 && (
        <span className="min-w-5 h-5 px-1.5 rounded-full bg-bad text-on-accent text-xs font-semibold leading-5 text-center">
          {badge}
        </span>
      )}
    </Link>
  );
}

// "/Users/me/code/my-app" → "my-app" (also works with Windows "\").
function projectName(project: string): string {
  return project.split(/[\\/]/).filter(Boolean).pop() ?? project;
}

export default function Navbar({ project, crumbs = [], center, right, scrolled = false }: NavbarProps) {
  const [windowScrolled, setWindowScrolled] = useState(false);
  const path = useLocation().pathname;
  const [reviewDue, setReviewDue] = useState(0);

  // The Review badge: how many reviews are due. Counted again whenever a review is
  // answered or the demo clock moves (the Review page sends REVIEW_CHANGED).
  useEffect(() => {
    if (!project) return;
    let ignore = false;
    const count = () =>
      fetchReviewCount(project)
        .then((n) => !ignore && setReviewDue(n))
        .catch(() => {}); // No badge is fine; the page itself still works.
    count();
    window.addEventListener(REVIEW_CHANGED, count);
    return () => {
      ignore = true;
      window.removeEventListener(REVIEW_CHANGED, count);
    };
  }, [project]);

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
      className={`bg-surface border-b border-line sticky top-0 z-10 transition-shadow ${
        scrolled || windowScrolled ? "shadow-sm" : ""
      }`}
    >
      <div
        className={`px-4 sm:px-6 min-h-12 py-2 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm`}
      >
      {/* basis: room for the Concepts and Review pills and the breadcrumbs before the rest wraps below. */}
      <nav aria-label="Breadcrumb" className="flex flex-wrap sm:flex-nowrap items-center gap-x-3 gap-y-2 min-w-0 flex-1 basis-full sm:basis-[36rem]">
        <Link to={sessionsUrl(project)} className="font-serif font-semibold text-lg text-ink shrink-0">
          Lore
        </Link>

        {project && (
          <>
            <NavPill to={reviewUrl(project)} active={path === "/review"} label="Review" icon={CLOCK} badge={reviewDue} />
            <NavPill to={conceptsUrl(project)} active={path === "/concepts"} label="Concepts" icon={BOOK} />
          </>
        )}

        {all.length > 0 && <span className="hidden sm:block h-5 w-px bg-line shrink-0" aria-hidden="true" />}

        <div className="sm:hidden ml-auto shrink-0">
          <ThemeToggle />
        </div>

        <ol className="flex items-center gap-1.5 min-w-0 basis-full order-last sm:order-none sm:basis-auto">
          {all.map((crumb, i) => {
            const last = i === all.length - 1;
            // The project and the file stay whole (up to a limit); the session
            // title in between is what gets cut short when space runs out.
            const edge = i === 0 || last;
            const text = `truncate ${crumb.mono ? "font-mono text-xs" : ""}`;
            return (
              <li key={i} className={`flex items-center gap-1.5 min-w-0 ${edge ? "sm:shrink-0 max-w-[16rem]" : "shrink"}`}>
                {i > 0 && <span className="text-ink-3 shrink-0" aria-hidden="true">›</span>}
                {crumb.to ? (
                  <Link to={crumb.to} title={crumb.label} className={`${text} text-accent underline-offset-2 hover:underline`}>
                    {crumb.label}
                  </Link>
                ) : (
                  <span title={crumb.label} aria-current={last ? "page" : undefined} className={`${text} text-ink-2`}>
                    {crumb.label}
                  </span>
                )}
              </li>
            );
          })}
        </ol>
      </nav>
      {center && <div className="shrink-0">{center}</div>}
      {right && <div className="min-w-0 sm:shrink-0 sm:ml-auto">{right}</div>}
      <div className={`hidden sm:block ${right ? "shrink-0" : "shrink-0 ml-auto"}`}>
        <ThemeToggle />
      </div>
      </div>
    </header>
  );
}
