// --- Types (match the backend DTOs) ---

// Used by the session list page — one per session row
export interface SessionResponse {
  id: string;
  status: "ACTIVE" | "COMPLETE"; // ACTIVE = still recording (lore start without lore stop)
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
  status: "ACTIVE" | "COMPLETE" | "DISCARDED";
  projectPath: string;
  reason: string | null;
  summary: string | null;
  summaryStatus: string;
  startedAt: string;
  stoppedAt: string | null;
  changes: ChangeResponse[];
  goalGaps: string[] | null; // parts of the goal no change did; [] = all done; null = not checked
  cost: SessionCost | null; // null for sessions from before costs were saved, and for providers other than Anthropic
}

// Estimated AI cost of a session, from token counts and the price table.
// dollars is null when a call used a model with no price: show tokens then.
export interface SessionCost {
  dollars: number | null;
  tokens: number;
  jobs: {
    job: "summary" | "explain" | "grading" | "follow_up";
    dollars: number | null;
    tokens: number;
    calls: number;
    tokensIn: number; // includes cache reads and writes
    tokensOut: number;
    models: string[];
  }[];
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
  concepts: { name: string; other: boolean }[]; // concept tags from the summary; other = not on the list
  questions: QuestionResponse[]; // follow-up questions asked under the explanation, oldest first
}

export interface QuestionResponse {
  question: string;
  answer: string;
  createdAt: string;
}

export interface TeachBackState {
  attemptsUsed: number;
  attemptsLeft: number;
  lastVerdict: "pass" | "partly" | "fail" | null;
  keyPoints: string[] | null; // null until revealed (after 2 answers that didn't pass, or a pass)
}

export interface TeachBackResult extends TeachBackState {
  verdict: "pass" | "partly" | "fail";
  practice: boolean; // answered after the change was already passed: no try used, the pass stays
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

// One concept on the Concepts page. group is its heading in concepts.txt (null for
// suggestions, and for a concept no longer on the list).
export interface ConceptCount {
  name: string;
  group: string | null;
  meaning: string | null; // one sentence from concepts.txt; null if the line has none
  seen: number;       // changes tagged with it
  understood: number; // of those, marked understood or passed in teach-back
  sessions: number;
  lastSeen: string | null; // ISO timestamp
}

export interface ConceptsResponse {
  concepts: ConceptCount[]; // from the list, most seen first
  others: ConceptCount[];   // "Other" suggestions, to review and maybe add to the list
  changes: number;           // tagged changes, each counted once however many tags it has
  understoodChanges: number; // of those, understood
}

// The Concepts page's graph view. A node is a concept (id is its name, or "other:<name>"
// for a suggestion); a link joins two concepts tagged on the same change, count times.
export interface ConceptGraphNode {
  id: string;
  name: string;
  group: string | null; // heading in concepts.txt; null for suggestions
  meaning: string | null;
  other: boolean;
  seen: number;
  understood: number;
  changes: {
    changeId: string;
    sessionId: string;
    sessionReason: string | null;
    filePath: string;
    description: string | null;
    understood: boolean;
    stoppedAt: string | null;
  }[];
}

export interface ConceptGraphData {
  nodes: ConceptGraphNode[];
  links: { source: string; target: string; count: number }[];
  groups: string[]; // headings that have a node, in concepts.txt order
}

// GET /api/concepts/graph?project=...
export async function fetchConceptGraph(project: string): Promise<ConceptGraphData> {
  const response = await fetch(`/api/concepts/graph?project=${encodeURIComponent(project)}`);
  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }
  return response.json();
}

// GET /api/concepts?project=...
export async function fetchConcepts(project: string): Promise<ConceptsResponse> {
  const response = await fetch(`/api/concepts?project=${encodeURIComponent(project)}`);
  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }
  return response.json();
}

// One change that's due for review (see the Review page).
export interface ReviewItem {
  changeId: string;
  sessionId: string;
  sessionReason: string | null;
  filePath: string;
  changeType: string;
  description: string | null;
  diffContent: string | null;
  oldStart: number;
  newStart: number;
  concepts: { name: string; other: boolean }[];
  gapDays: number; // the gap it's on now: 1, 3, 7 or 14
  dueAt: string;
}

export interface ReviewResponse {
  question: string;
  due: ReviewItem[];
  dueCount: number;
  upcomingCount: number;
  doneCount: number;
  demo: { enabled: boolean; daysAhead: number }; // the "pretend days passed" control
}

export interface ReviewResult {
  verdict: "pass" | "partly" | "fail";
  gotRight: string[];
  missing: string[]; // hints, never the answer
  done: boolean;     // passed the last (14-day) review: it won't come back
  gapDays: number;   // when it comes back
  nextDueAt: string;
}

// Sent when a review is answered or the demo clock moves, so the navbar badge re-counts.
export const REVIEW_CHANGED = "lore-review-changed";

// GET /api/review?project=...
export async function fetchReview(project: string): Promise<ReviewResponse> {
  const response = await fetch(`/api/review?project=${encodeURIComponent(project)}`);
  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }
  return response.json();
}

// GET /api/review/count?project=... -> how many are due (the navbar badge)
export async function fetchReviewCount(project: string): Promise<number> {
  const response = await fetch(`/api/review/count?project=${encodeURIComponent(project)}`);
  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }
  const data: { due: number } = await response.json();
  return data.due;
}

// POST /api/review/{changeId}. On failure the server sends { error } that's safe to show.
export async function answerReview(changeId: string, answer: string): Promise<ReviewResult> {
  const response = await fetch(`/api/review/${changeId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ answer }),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error ?? "Couldn't check your answer. Try again.");
  }
  return response.json();
}

// Demo only (demo.time-travel=true): POST /api/demo/days-passed?days=N, or /api/demo/reset.
export async function moveDemoClock(days: number | "reset"): Promise<void> {
  const url = days === "reset" ? "/api/demo/reset" : `/api/demo/days-passed?days=${days}`;
  const response = await fetch(url, { method: "POST" });
  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }
}

// GET /api/sessions/{id}/cost
// The cost so far, re-read after an Explain or a teach-back answer adds to it.
export async function fetchSessionCost(id: string): Promise<SessionCost | null> {
  const response = await fetch(`/api/sessions/${id}/cost`);
  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }
  const data: { cost: SessionCost | null } = await response.json();
  return data.cost;
}

// POST /api/changes/{id}/questions: a follow-up question about one change.
// On failure the server sends { error } that's safe to show.
export async function askQuestion(changeId: string, question: string): Promise<QuestionResponse> {
  const response = await fetch(`/api/changes/${changeId}/questions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question }),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error ?? "Couldn't answer that. Try again.");
  }
  return response.json();
}

// DELETE /api/sessions/{id}
// Deletes the session and everything saved for it. A running session is refused
// with { error } ("Stop this session first with lore stop.").
export async function deleteSession(id: string): Promise<void> {
  const response = await fetch(`/api/sessions/${id}`, { method: "DELETE" });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error ?? "Couldn't delete the session. Try again.");
  }
}

// Asked before every delete, on the list and on the session page.
export const DELETE_CONFIRM = "Delete this session? This can't be undone.";

// The session list's address for a project.
export function sessionsUrl(project: string | null): string {
  return project ? `/?project=${encodeURIComponent(project)}` : "/";
}

// The Review page's address for a project.
export function reviewUrl(project: string): string {
  return `/review?project=${encodeURIComponent(project)}`;
}

// A session page opened on one change: its file, with that change selected.
export function changeUrl(sessionId: string, filePath: string, changeId: string): string {
  return `/sessions/${sessionId}/file?path=${encodeURIComponent(filePath)}&change=${changeId}`;
}

// The Concepts page's address for a project, optionally scrolled to one concept's row.
export function conceptsUrl(project: string, concept?: string): string {
  return `/concepts?project=${encodeURIComponent(project)}${concept ? `#${conceptAnchor(concept)}` : ""}`;
}

// The id of a concept's row on the Concepts page: "Async code (promises/await)" -> "concept-async-code-promises-await".
export function conceptAnchor(name: string): string {
  return "concept-" + name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
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
