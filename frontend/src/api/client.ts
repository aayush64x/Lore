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
  goalGaps: string[] | null; // parts of the goal no change did; [] = all done; null = not checked
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
  goalMatch: "yes" | "partly" | "no" | null; // against the goal given at lore stop; null = not checked
  goalReason: string | null;
  understoodBy: "teach_back" | "self" | null; // passed a teach-back, or marked by hand / skipped
  teachBack: TeachBackState;
}

export interface TeachBackState {
  attemptsUsed: number;
  attemptsLeft: number;
  lastVerdict: "pass" | "partly" | "fail" | null;
  keyPoints: string[] | null; // null until revealed (after 2 answers that didn't pass, or a pass)
}

export interface TeachBackResult extends TeachBackState {
  verdict: "pass" | "partly" | "fail";
  gotRight: string[];
  missing: string[]; // hints that point at what's missing, never the answer
  understood: boolean;
  understoodBy: "teach_back" | "self" | null;
}

// A refused or failed teach-back: the server's reason, plus the tries state when it sent one.
export class TeachBackError extends Error {
  state: Partial<TeachBackState>;
  constructor(message: string, state: Partial<TeachBackState>) {
    super(message);
    this.state = state;
  }
}

// POST /api/changes/{id}/teach-back. The server enforces every rule (3 tries, when key points
// show, what counts as understood); a failed AI call doesn't use a try.
export async function teachBack(id: string, answer: string): Promise<TeachBackResult> {
  const response = await fetch(`/api/changes/${id}/teach-back`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ answer }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new TeachBackError(data.error ?? `Couldn't check your answer (HTTP ${response.status}).`, data);
  }
  return data;
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
export async function toggleUnderstood(id : string) : Promise<{understood : boolean; understoodBy: "teach_back" | "self" | null}>{
  const response = await fetch(`/api/changes/${id}/understood`,{
    method : "PUT",
  });
  
  if(!response.ok){
    throw new Error(`HTTP error : ${response.status}`);
  }
  const data = await response.json();
  return { understood : data.understood, understoodBy: data.understoodBy ?? null }
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
export interface PossibleIssue {
  diff_line: number; // numbered diff line it's on (like steps); 0 = no line
  text: string;
}
export interface ExplanationData{
  overview: string;
  steps: ExplanationStep[];
  concepts: ExplanationConcept[];
  current?: boolean;          // false if written for a different level than the user's now
  writtenFor?: string | null; // that level, like "New to coding" (null for old ones)
  damaged?: boolean;          // saved with stray JSON characters (e.g. "}]}") before they were checked
  testable?: boolean | null;  // false = nothing to understand (imports, formatting); null = made before teach-back
  hasKeyPoints?: boolean;     // teach-back can grade it (the key points stay on the server)
  possibleIssue?: PossibleIssue | null; // a real edge case or bug the AI found, or null
}

// refresh: make a new one even if a saved one would do (used to add key points to an old one).
export async function explainChange(id: string, refresh = false): Promise<ExplanationData> {
  const response = await fetch(`/api/changes/${id}/explain${refresh ? "?refresh=true" : ""}`, { method: "POST" });

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
