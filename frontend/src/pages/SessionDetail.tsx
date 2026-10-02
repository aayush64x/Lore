// Session Detail Page — shows one session's summary and all its changes.
//
// PURPOSE: The user clicked a session from the list. Now they see what changed
// and can dig into individual diffs.
//
// DATA NEEDED: Call fetchSession(id) on mount.
//   - "id" comes from the URL param: /sessions/:id
//   - Use useParams() from react-router-dom to read it.
//
// STATES TO HANDLE:
//   - Loading: show "Loading session..."
//   - Not found: show an error if the fetch returns 404
//   - Loaded: render the session detail
//
// NAVIGATION: Back link to the session list (/)

export default function SessionDetail() {
  return (
    <div className="session-detail-page">
      {/* Back link */}
      <a href="/" className="back-link">← Back to sessions</a>

      {/* Session header */}
      <div className="session-header">
        <h1 className="session-reason">Added login feature</h1>
        <div className="session-date">Oct 1, 2:30 PM</div>
      </div>

      {/* AI summary */}
      <div className="session-summary">
        <p>Implemented user authentication with password validation and session management.</p>
      </div>

      {/* Changes list — grouped by file */}
      <div className="changes-list">
        <h2>Changes</h2>

        {/* One block per file. Map over changes grouped by filePath. */}
        <div className="file-group">
          <h3 className="file-name">src/LoginService.java</h3>

          {/* One entry per change (hunk) in this file */}
          <div className="change-entry">
            <div className="change-header">
              <span className="change-lines">Lines 10–25</span>
              <span className="change-description">Add password validation to the login method</span>
              <span className="change-status">Not understood</span>
            </div>

            {/* DiffView goes here — render diffContent with colored lines */}
            <pre className="diff-view">
              {/* Each line of diffContent:
                  - starts with "+" → green (added)
                  - starts with "-" → red (removed)
                  - starts with " " → default (context)
                  - starts with "@@" → grey (hunk header)
              */}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}
