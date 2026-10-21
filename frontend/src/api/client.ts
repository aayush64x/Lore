// --- Types (match the backend DTOs) ---

// Used by the session list page — one per session row
export interface SessionResponse {
  id: string;
  reason: string | null;
  summary: string | null;
  summaryStatus: string;       // "PENDING", "COMPLETE", or "FAILED"
  startedAt: string;           // ISO timestamp, e.g. "2026-10-01T10:30:00Z"
  stoppedAt: string | null;
  fileCount: number;
  changeCount: number;
  understoodCount: number;
}

// Used by the session detail page — one session with all its changes
export interface SessionDetailResponse {
  id: string;
  projectPath: string;
  reason: string | null;
  summary: string | null;
  summaryStatus: string;
  startedAt: string;
  stoppedAt: string | null;
  changes: ChangeResponse[];
}

// One hunk from a diff — a single chunk of modified code
export interface ChangeResponse {
  id: string;
  filePath: string;
  changeType: string;          // "ADDED", "MODIFIED", "DELETED", or "RENAMED"
  oldFilePath: string | null;  // only for renames
  oldStart: number;
  oldCount: number;
  newStart: number;
  newCount: number;
  diffContent: string | null;  // the raw +/- lines, null for binary files
  description: string | null;  // AI-generated one-liner
  binary: boolean;
  understood: boolean;
  displayOrder: number;
  explanation: ExplanationData | null; // saved explanation, if this change was explained before
}

// --- API functions (your turn) ---
//
// 1. fetchSessions(project: string): Promise<SessionResponse[]>
//    GET /api/sessions?project={project}

export async function fetchSessions(project : string) : Promise<SessionResponse[]>{
  const response = await fetch(
    `/api/sessions?project=${encodeURIComponent(project)}`
  );
  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }
  const data = await response.json(); 
  return data;
}
// 2. fetchSession(id: string): Promise<SessionDetailResponse>
//    GET /api/sessions/{id}

export async function fetchSession(id : string): Promise<SessionDetailResponse>{
  const response = await fetch(`/api/sessions/${id}`)

  if(!response.ok){
    throw new Error(`HTTP error : ${response.status}`);
  }
  return response.json();
}

// TODO: Add a function to toggle a change's "understood" status.
//
// WHAT: Call the backend endpoint that flips the understood boolean.
//   The frontend calls this when the user clicks the toggle button.
//
// SIGNATURE: toggleUnderstood(id: string): Promise<{ understood: boolean }>
//
// ENDPOINT: PUT /api/changes/{id}/understood
//
// STEPS:
//   1. Send a PUT request to /api/changes/${id}/understood
//   2. Check response.ok — throw on failure
//   3. Return the parsed JSON (it has { understood: true/false })
//
// LOOK AT: fetchSession above for the pattern — same style, just PUT instead of GET.
//
// READ: MDN > Fetch API > Using Fetch — look at how to set the method
export async function toggleUnderstood(id : string) : Promise<{understood : boolean}>{
  const response = await fetch(`/api/changes/${id}/understood`,{
    method : "PUT",
  });
  
  if(!response.ok){
    throw new Error(`HTTP error : ${response.status}`);
  }
  const data = await response.json();
  return { understood : data.understood}
}


export interface ExplanationStep{
  text: string; 
  diff_line_start: number; 
  diff_line_end: number; 
}
export interface ExplanationConcept{
  name: string; 
  meaning: string; 
}
export interface ExplanationData{
  overview: string;
  steps: ExplanationStep[];
  concepts: ExplanationConcept[];
  current?: boolean;          // false if written for a different level than the user's now
  writtenFor?: string | null; // that level, like "New to coding" (null for old ones)
}

export async function explainChange(id: string): Promise<ExplanationData> {
  const response = await fetch(`/api/changes/${id}/explain`, { method: "POST" });

  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }
  return response.json();
}
// POST /api/sessions/{id}/retry-summary
// On success the server sends back the whole session with its new summary.
// On failure it sends { error } with a reason that's safe to show the user.
export async function retrySummary(id: string): Promise<SessionDetailResponse> {
  const response = await fetch(`/api/sessions/${id}/retry-summary`, { method: "POST" });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error ?? "Couldn't create the summary. Try again.");
  }
  return response.json();
}

// "What were you trying to do?" can be many lines. The first line is the title;
// the rest is shown under it on the session page.
export function sessionTitle(reason: string | null): string {
  const first = reason?.trim().split("\n")[0].trim();
  return first || "Untitled session";
}

export function sessionDetails(reason: string | null): string | null {
  const rest = reason?.trim().split("\n").slice(1).join("\n").trim();
  return rest || null;
}
