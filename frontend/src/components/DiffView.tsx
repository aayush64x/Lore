// Diff View Component — renders a single change's diff with colored lines.
//
// PURPOSE: Show the raw diff content with syntax coloring so the user can
// see exactly what was added and removed.
//
// PROPS TO ACCEPT:
//   - diffContent: string (the raw diff text from the Change)
//
// HOW TO RENDER:
//   Split diffContent by newlines. Each line gets a CSS class based on its first character:
//     "+" → className "diff-added" (green background)
//     "-" → className "diff-removed" (red background)
//     "@@" → className "diff-header" (grey)
//     " " or anything else → className "diff-context" (no highlight)
//
//   Render as a <pre> with each line as a <div> inside it.

export default function DiffView() {
  return (
    <pre className="diff-view">
      <div className="diff-header">@@ -10,5 +12,8 @@</div>
      <div className="diff-context"> import java.util.List;</div>
      <div className="diff-context"> </div>
      <div className="diff-removed">-    public void login(String user) {'{'}</div>
      <div className="diff-added">+    public void login(String user, String password) {'{'}</div>
      <div className="diff-added">+        validatePassword(password);</div>
      <div className="diff-context">         // existing code</div>
    </pre>
  );
}
