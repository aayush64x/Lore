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
