package com.lore.git;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a long new file into chunks of about MIN-MAX lines, so each one can be
 * explained and marked understood on its own. Works on any language by looking at
 * layout, not syntax. It cuts right after an empty line, at the line with the least
 * indent: a top-level function, class or heading (with the comment above it) beats
 * a section inside one. Lines that close a block (}, ), ], </tag>) are never used.
 * With no empty line to use, it cuts at MAX lines.
 */
final class NewFileChunker {

    static final int MIN = 30;
    static final int MAX = 80;

    private NewFileChunker() {}

    /** Start index (0-based) of each chunk. The first is always 0. */
    static List<Integer> chunkStarts(List<String> lines) {
        List<Integer> starts = new ArrayList<>(List.of(0));
        int n = lines.size();
        int start = 0;
        while (n - start > MAX) {
            int lo = start + MIN;
            int hi = start + MAX;
            // Leave at least MIN lines for the last chunk.
            if (n - hi < MIN) hi = n - MIN;

            int cut = bestCut(lines, lo, hi);
            if (cut < 0) cut = hi;

            starts.add(cut);
            start = cut;
        }
        return starts;
    }

    /** The index in [lo, hi] with the least indent after an empty line (latest on a tie), or -1. */
    private static int bestCut(List<String> lines, int lo, int hi) {
        int best = -1;
        int bestIndent = Integer.MAX_VALUE;
        for (int i = hi; i >= lo; i--) {
            String line = lines.get(i);
            if (!lines.get(i - 1).isBlank() || line.isBlank() || closesBlock(line)) continue;
            int indent = indent(line);
            if (indent < bestIndent) {
                best = i;
                bestIndent = indent;
            }
        }
        return best;
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
