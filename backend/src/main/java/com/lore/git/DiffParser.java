package com.lore.git;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiffParser {

    public record ParsedChange(
            String filePath,
            String changeType,
            String oldFilePath,
            int oldStart,
            int oldCount,
            int newStart,
            int newCount,
            String diffContent,
            boolean binary,
            int displayOrder
    ) {}

    // @@ -oldStart[,oldCount] +newStart[,newCount] @@
    private static final Pattern HUNK =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    private static final Set<String> SKIP_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "pom.xml.lock",
            "Gemfile.lock", "Pipfile.lock", "composer.lock", "Cargo.lock", "poetry.lock"
    );

    private static final Set<String> SKIP_DIRS = Set.of(
            "target", "build", "dist", "node_modules", ".gradle"
    );

    /** Everything we know about the file section we're currently reading. */
    private static class FileState {
        String path;
        String oldPath;
        String type = "MODIFIED";
        boolean binary = false;
        boolean hadHunk = false;
    }

    /** The hunk we're currently collecting lines for. */
    private static class HunkState {
        int oldStart, oldCount, newStart, newCount;
        final StringBuilder text = new StringBuilder();
    }

    /**
     * Parses raw output of "git diff-tree -r -p -M" into one ParsedChange per hunk.
     * Files with no hunks (binary, pure rename, empty new file, empty deleted file)
     * still produce one ParsedChange with zero line numbers and no diff content.
     */
    public List<ParsedChange> parse(String rawDiff) {
        List<ParsedChange> changes = new ArrayList<>();
        if (rawDiff == null || rawDiff.isBlank()) {
            return changes;
        }

        int[] order = {0};          // display order counter, shared with helpers
        FileState file = null;
        HunkState hunk = null;

        for (String rawLine : rawDiff.split("\n", -1)) {
            String line = rawLine.endsWith("\r")
                    ? rawLine.substring(0, rawLine.length() - 1)
                    : rawLine;

            if (line.startsWith("diff --git ")) {
                // A new file section starts: close out the previous one.
                finishHunk(file, hunk, changes, order);
                finishFile(file, changes, order);
                hunk = null;
                file = new FileState();
                file.path = pathFromDiffHeader(line);

            } else if (file == null) {
                // Anything before the first "diff --git" (e.g. a commit SHA line) is ignored.
                continue;

            } else if (hunk == null && line.startsWith("new file mode")) {
                file.type = "ADDED";

            } else if (hunk == null && line.startsWith("deleted file mode")) {
                file.type = "DELETED";

            } else if (hunk == null && line.startsWith("rename from ")) {
                file.type = "RENAMED";
                file.oldPath = unquote(line.substring("rename from ".length()));

            } else if (hunk == null && line.startsWith("rename to ")) {
                file.path = unquote(line.substring("rename to ".length()));

            } else if (hunk == null && line.startsWith("Binary files ")) {
                file.binary = true;

            } else if (hunk == null && line.startsWith("+++ ")) {
                // "+++ b/path" is a more reliable source of the new path than the
                // "diff --git" header (which is ambiguous if a filename contains " b/").
                // Git appends a tab after paths that contain spaces; drop it.
                String p = line.substring(4).replaceAll("\t$", "");
                if (!p.equals("/dev/null")) {
                    file.path = unquote(stripPrefix(p, "b/"));
                }

            } else if (line.startsWith("@@")) {
                Matcher m = HUNK.matcher(line);
                if (m.find()) {
                    finishHunk(file, hunk, changes, order);
                    hunk = new HunkState();
                    hunk.oldStart = Integer.parseInt(m.group(1));
                    hunk.oldCount = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
                    hunk.newStart = Integer.parseInt(m.group(3));
                    hunk.newCount = m.group(4) == null ? 1 : Integer.parseInt(m.group(4));
                    hunk.text.append(line).append('\n');
                } else if (hunk != null) {
                    hunk.text.append(line).append('\n');
                }

            } else if (hunk != null) {
                // Content line inside a hunk: " ", "+", "-", or "\ No newline at end of file".
                // Skip the empty string produced by the trailing newline of the whole diff.
                if (!line.isEmpty()) {
                    hunk.text.append(line).append('\n');
                }
            }
            // Everything else ("index ...", "--- a/...", "similarity index ...") is ignored.
        }

        // The diff doesn't end with a "diff --git" line, so close out the last file here.
        finishHunk(file, hunk, changes, order);
        finishFile(file, changes, order);

        return changes;
    }

    /** Turns a finished hunk into a ParsedChange (unless the file is skipped). */
    private void finishHunk(FileState file, HunkState hunk, List<ParsedChange> changes, int[] order) {
        if (file == null || hunk == null) return;
        file.hadHunk = true;
        if (shouldSkip(file)) return;
        List<String> body = hunk.text.toString().lines().skip(1).toList(); // drop the @@ line
        List<Integer> starts = HunkSplitter.pieceStarts(body);
        if (starts.size() > 1) {
            addHunkPieces(file, hunk, body, starts, changes, order);
            return;
        }
        changes.add(new ParsedChange(
                file.path, file.type, file.oldPath,
                hunk.oldStart, hunk.oldCount, hunk.newStart, hunk.newCount,
                hunk.text.toString(), false, order[0]++
        ));
    }

    /**
     * A long hunk (edits, or a whole new file) becomes several changes, one per piece of
     * whole functions (see HunkSplitter), each with its own "@@ -oldStart,oldCount +newStart,newCount @@" header
     * counted from the lines before it, so it reads like a hunk git could have written.
     */
    private void addHunkPieces(FileState file, HunkState hunk, List<String> body, List<Integer> starts,
                               List<ParsedChange> changes, int[] order) {
        // The next line number on each side. Git writes "-5,0" for "after line 5".
        int oldLine = hunk.oldCount == 0 ? hunk.oldStart + 1 : hunk.oldStart;
        int newLine = hunk.newCount == 0 ? hunk.newStart + 1 : hunk.newStart;
        for (int p = 0; p < starts.size(); p++) {
            int from = starts.get(p);
            int to = p + 1 < starts.size() ? starts.get(p + 1) : body.size();
            int oldCount = 0, newCount = 0;
            for (String l : body.subList(from, to)) {
                if (l.startsWith("-") || l.startsWith(" ") || l.isEmpty()) oldCount++;
                if (l.startsWith("+") || l.startsWith(" ") || l.isEmpty()) newCount++;
            }
            int oldStart = oldCount == 0 ? oldLine - 1 : oldLine;
            int newStart = newCount == 0 ? newLine - 1 : newLine;
            StringBuilder text = new StringBuilder()
                    .append("@@ -").append(oldStart).append(',').append(oldCount)
                    .append(" +").append(newStart).append(',').append(newCount).append(" @@\n");
            for (String l : body.subList(from, to)) text.append(l).append('\n');
            changes.add(new ParsedChange(
                    file.path, file.type, file.oldPath,
                    oldStart, oldCount, newStart, newCount,
                    text.toString(), false, order[0]++
            ));
            oldLine += oldCount;
            newLine += newCount;
        }
    }

    /**
     * Called when a file section ends. If the file had no hunks (binary, pure rename,
     * empty new file, deleted empty file), record it as one change with no content.
     */
    private void finishFile(FileState file, List<ParsedChange> changes, int[] order) {
        if (file == null || file.hadHunk || shouldSkip(file)) return;
        changes.add(new ParsedChange(
                file.path, file.type, file.oldPath,
                0, 0, 0, 0, null, file.binary, order[0]++
        ));
    }

    private boolean shouldSkip(FileState file) {
        return shouldSkip(file.path) || (file.oldPath != null && shouldSkip(file.oldPath));
    }

    private boolean shouldSkip(String path) {
        if (path == null) return false;
        String[] parts = path.split("/");
        String fileName = parts[parts.length - 1];
        if (SKIP_FILES.contains(fileName)) return true;
        // Skip if any folder in the path is a build/output folder.
        for (int i = 0; i < parts.length - 1; i++) {
            if (SKIP_DIRS.contains(parts[i])) return true;
        }
        return false;
    }

    /** "diff --git a/foo b/foo" -> "foo" (the new-side path). */
    private static String pathFromDiffHeader(String line) {
        String rest = line.substring("diff --git ".length());
        int idx = rest.lastIndexOf(" b/");
        if (idx < 0) idx = rest.lastIndexOf(" \"b/");
        String b = idx >= 0 ? rest.substring(idx + 1) : rest;
        return unquote(stripPrefix(unquote(b), "b/"));
    }

    private static String stripPrefix(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
    }

    /** Git wraps unusual paths in double quotes; remove them. */
    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}