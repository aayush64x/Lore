// One row of a diff, with the file line numbers shown in the gutter.
export interface DiffRow {
  text: string;
  kind: "header" | "added" | "removed" | "context" | "note";
  oldLine: number | null; // blank for added rows
  newLine: number | null; // blank for removed rows
}

const HUNK_HEADER = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/;

// Splits diffContent into rows. Row i is diff line i + 1, the same numbering the
// AI uses for explain steps. Context lines count up both numbers, removed lines
// only the old one, added lines only the new one.
export function diffRows(diffContent: string, oldStart: number, newStart: number): DiffRow[] {
  // Drop the empty string after the final newline so it isn't drawn as a line.
  const lines = diffContent.replace(/\n$/, "").split("\n");
  let oldLine = oldStart;
  let newLine = newStart;
  return lines.map((text) => {
    const header = text.match(HUNK_HEADER);
    if (header) {
      oldLine = Number(header[1]);
      newLine = Number(header[2]);
      return { text, kind: "header", oldLine: null, newLine: null };
    }
    if (text.startsWith("\\")) return { text, kind: "note", oldLine: null, newLine: null };
    if (text.startsWith("+")) return { text, kind: "added", oldLine: null, newLine: newLine++ };
    if (text.startsWith("-")) return { text, kind: "removed", oldLine: oldLine++, newLine: null };
    return { text, kind: "context", oldLine: oldLine++, newLine: newLine++ };
  });
}

// "Lines 12–15" for an explain step, using new-file line numbers like the gutter.
// diffStart/diffEnd are 1-based diff positions. A step that only covers removed
// lines has no new numbers, so it shows the old ones instead.
export function stepLabel(rows: DiffRow[], diffStart: number, diffEnd: number): string {
  const inRange = rows.slice(Math.max(diffStart - 1, 0), Math.max(diffEnd, 0));
  const range = (nums: number[], prefix: string) => {
    const first = nums[0];
    const last = nums[nums.length - 1];
    return first === last ? `${prefix} ${first}` : `${prefix}s ${first}–${last}`;
  };
  const newNums = inRange.map((r) => r.newLine).filter((n): n is number => n !== null);
  if (newNums.length > 0) return range(newNums, "Line");
  const oldNums = inRange.map((r) => r.oldLine).filter((n): n is number => n !== null);
  if (oldNums.length > 0) return range(oldNums, "Removed line");
  return `Lines ${diffStart}–${diffEnd}`;
}

// How many lines a change adds and removes, for "+12 −3" counts.
export function lineCounts(diffContent: string | null): { added: number; removed: number } {
  let added = 0;
  let removed = 0;
  for (const line of (diffContent ?? "").split("\n")) {
    if (line.startsWith("+")) added++;
    else if (line.startsWith("-")) removed++;
  }
  return { added, removed };
}
