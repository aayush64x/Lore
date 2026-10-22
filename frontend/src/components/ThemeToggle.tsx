// Light/dark switch in the navbar. Adds or removes the "dark" class on <html> (index.css
// swaps the colors) and remembers the choice; until one is made, index.html follows the
// system setting. The icon follows the <html> class, so every copy of the switch (the
// navbar has one for phones and one for wider screens) always agrees.

import { useEffect, useState } from "react";

const KEY = "lore-theme";

export default function ThemeToggle() {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains("dark"));

  useEffect(() => {
    const observer = new MutationObserver(() => setDark(document.documentElement.classList.contains("dark")));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  function toggle() {
    const next = !dark;
    document.documentElement.classList.toggle("dark", next);
    try {
      localStorage.setItem(KEY, next ? "dark" : "light");
    } catch {
      // Private window or storage blocked: the switch still works until the page reloads.
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
      title={dark ? "Light mode" : "Dark mode"}
      className="shrink-0 p-1.5 rounded-control text-ink-3 transition-colors duration-150 hover:text-ink hover:bg-sunk"
    >
      {dark ? (
        // Sun
        <svg viewBox="0 0 20 20" fill="currentColor" className="w-5 h-5" aria-hidden="true">
          <path d="M10 2a.75.75 0 0 1 .75.75v1.5a.75.75 0 0 1-1.5 0v-1.5A.75.75 0 0 1 10 2zm0 13a.75.75 0 0 1 .75.75v1.5a.75.75 0 0 1-1.5 0v-1.5A.75.75 0 0 1 10 15zm8-5a.75.75 0 0 1-.75.75h-1.5a.75.75 0 0 1 0-1.5h1.5A.75.75 0 0 1 18 10zM5 10a.75.75 0 0 1-.75.75h-1.5a.75.75 0 0 1 0-1.5h1.5A.75.75 0 0 1 5 10zm9.66-4.66a.75.75 0 0 1 0 1.06l-1.06 1.06a.75.75 0 1 1-1.06-1.06l1.06-1.06a.75.75 0 0 1 1.06 0zM7.46 12.54a.75.75 0 0 1 0 1.06L6.4 14.66a.75.75 0 1 1-1.06-1.06l1.06-1.06a.75.75 0 0 1 1.06 0zm7.2 2.12a.75.75 0 0 1-1.06 0l-1.06-1.06a.75.75 0 1 1 1.06-1.06l1.06 1.06a.75.75 0 0 1 0 1.06zM7.46 7.46a.75.75 0 0 1-1.06 0L5.34 6.4A.75.75 0 1 1 6.4 5.34l1.06 1.06a.75.75 0 0 1 0 1.06zM10 6.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z" />
        </svg>
      ) : (
        // Moon
        <svg viewBox="0 0 20 20" fill="currentColor" className="w-5 h-5" aria-hidden="true">
          <path d="M7.46 2.24a.75.75 0 0 1 .17.8 6.5 6.5 0 0 0 8.33 8.33.75.75 0 0 1 .97.97A8 8 0 1 1 6.66 2.07a.75.75 0 0 1 .8.17z" />
        </svg>
      )}
    </button>
  );
}
