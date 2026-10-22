package com.lore.git;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a long hunk of edits to an existing file into pieces of whole functions, so each
 * piece can be explained and marked understood on its own. Works on any language by
 * looking at layout, not syntax: a function (or class, or heading) starts right after an
 * empty line, at the least indent in the hunk, with the comment or annotation above it.
 * Lines that close a block (}, ), ], </tag>) never start one.
 *
 * Pieces take whole functions, up to MAX lines. Only a function longer than MAX on its own
 * is split by lines (with NewFileChunker). A piece with no added or removed line, just
 * unchanged context, joins the piece next to it.
 */
final class HunkSplitter {

    static final int MAX = NewFileChunker.MAX;

    private HunkSplitter() {}

    /**
     * Start index (0-based) of each piece in a hunk's body: its lines without the @@ header,
     * each starting with " ", "+", "-" or "\" (no newline marker). The first is always 0.
     */
    static List<Integer> pieceStarts(List<String> body) {
        if (body.size() <= MAX) return List.of(0);

        List<String> code = body.stream().map(l -> l.isEmpty() ? l : l.substring(1)).toList();
        List<Integer> starts = new ArrayList<>();
        int pieceSize = 0;
        List<Integer> units = functionStarts(body, code);
        for (int u = 0; u < units.size(); u++) {
            int from = units.get(u);
            int to = u + 1 < units.size() ? units.get(u + 1) : body.size();
            int size = to - from;
            if (size > MAX) {
                // One function longer than a piece: split it by lines, on its own.
                for (int s : NewFileChunker.chunkStarts(code.subList(from, to))) starts.add(from + s);
                pieceSize = MAX + 1; // the next function starts a new piece
            } else if (starts.isEmpty() || pieceSize + size > MAX) {
                starts.add(from);
                pieceSize = size;
            } else {
                pieceSize += size;
            }
        }
        // A "\ No newline at end of file" line belongs with the line before it.
        starts.removeIf(s -> body.get(s).startsWith("\\"));
        joinPiecesWithoutChanges(body, starts);
        return starts;
    }

    /** Where each function starts: index 0, then every boundary line (see the class comment). */
    private static List<Integer> functionStarts(List<String> body, List<String> code) {
        int least = Integer.MAX_VALUE;
        for (int i = 0; i < code.size(); i++) {
            if (isCode(body, code, i)) least = Math.min(least, indent(code.get(i)));
        }
        List<Integer> starts = new ArrayList<>(List.of(0));
        for (int i = 1; i < code.size(); i++) {
            if (isCode(body, code, i) && code.get(i - 1).isBlank() && indent(code.get(i)) == least) {
                starts.add(i);
            }
        }
        return starts;
    }

    /** A real line of code that could start a function: not empty, a marker or a closing line. */
    private static boolean isCode(List<String> body, List<String> code, int i) {
        String line = code.get(i);
        return !body.get(i).startsWith("\\") && !line.isBlank() && !closesBlock(line);
    }

    /** Removes the start of every piece that has no + or - line, joining it to a neighbor. */
    private static void joinPiecesWithoutChanges(List<String> body, List<Integer> starts) {
        for (int p = 0; p < starts.size() && starts.size() > 1; ) {
            int from = starts.get(p);
            int to = p + 1 < starts.size() ? starts.get(p + 1) : body.size();
            if (hasChange(body, from, to)) {
                p++;
            } else if (p == 0) {
                starts.remove(1); // the first piece joins the one after it
            } else {
                starts.remove(p); // joins the one before it
            }
        }
    }

    private static boolean hasChange(List<String> body, int from, int to) {
        for (int i = from; i < to; i++) {
            if (body.get(i).startsWith("+") || body.get(i).startsWith("-")) return true;
        }
        return false;
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return i;
    }

    private static boolean closesBlock(String line) {
        String t = line.strip();
        return t.startsWith("}") || t.startsWith(")") || t.startsWith("]") || t.startsWith("</");
    }
}
