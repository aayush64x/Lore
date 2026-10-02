// Session List Page — the home page of Lore's web UI.
//
// PURPOSE: Show all sessions for the current project, newest first.
// The user scans this list and clicks a session to study its changes.
//
// DATA NEEDED: Call fetchSessions(project) on mount.
//   - "project" comes from the URL query param: ?project=/path/to/project
//   - Use useSearchParams() from react-router-dom to read it.
//
// STATES TO HANDLE:
//   - Loading: show "Loading sessions..."
//   - Empty: show the empty state message (no sessions yet)
//   - Loaded: render the session rows
//
// NAVIGATION: Clicking a row goes to /sessions/:id

export default function SessionList() {
  return (
    <div className="session-list-page">
      <h1 className="project-name">Lore</h1>

      {/* Empty state — show when there are no sessions */}
      <div className="empty-state">
        <p>No sessions yet. Run <code>lore start</code> in your terminal to record your first one.</p>
      </div>

      {/* Session rows — show when sessions exist */}
      <ul className="session-list">
        {/* Map over sessions here. Each item should be a <li> like this: */}
        <li className="session-row">
          <div className="session-reason">Added login feature</div>
          <div className="session-meta">
            <span className="session-date">Oct 1, 2:30 PM</span>
            <span className="session-files">3 files</span>
            <span className="session-changes">7 changes</span>
          </div>
          <div className="session-progress">0 of 7 understood</div>
        </li>
      </ul>
    </div>
  );
}
