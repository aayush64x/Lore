// Progress Bar Component — shows how many changes the user has understood.
//
// PURPOSE: Visual indicator of review progress for a session.
//
// PROPS TO ACCEPT:
//   - understood: number (how many changes marked understood)
//   - total: number (total changes in the session)
//
// RENDER: A bar that fills proportionally (understood / total).
//   Show the text "X of Y understood" next to or inside the bar.
//   When understood === total, the bar is full — session complete.

export default function ProgressBar() {
  return (
    <div className="progress-bar-container">
      <div className="progress-bar">
        <div className="progress-fill" style={{ width: '30%' }}></div>
      </div>
      <span className="progress-text">3 of 10 understood</span>
    </div>
  );
}
