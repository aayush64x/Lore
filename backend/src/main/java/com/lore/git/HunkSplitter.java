package com.lore.git;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Splits a long hunk (edits to a file, or a whole new file) into pieces of whole functions,
 * so each piece can be explained and marked understood on its own. Works on any language
 * by looking at layout, not syntax: a function (or class, or heading) starts right after an
 * empty line, with the comment or annotation above it. Lines that close a block (}, ), ],
 * </tag>) never start one.
 *
 * It looks for those starts at the least indent first (top-level functions). A block that
 * is still longer than MAX, like a class, is looked into one indent deeper (its methods),
 * and so on. Pieces take whole blocks, up to MAX lines. Only a block with no starts inside
 * it at any indent is split by lines, into even pieces. A piece with no added or removed
 * line, just unchanged context, joins the piece next to it.
 */
final class HunkSplitter {

    static final int MAX = 80;
    /** A piece smaller than this (like a file's imports) may take the start of a long block after it. */
    static final int MIN = 30;

    private HunkSplitter() {}

    /**
     * Start index (0-based) of each piece in a hunk's body: its lines without the @@ header,
     * each starting with " ", "+", "-" or "\" (no newline marker). The first is always 0.
     */
    static List<Integer> pieceStarts(List<String> body) {
        if (body.stream().filter(l -> !l.startsWith("\\")).count() <= MAX) return List.of(0);

        List<String> code = body.stream().map(l -> l.isEmpty() ? l : l.substring(1)).toList();
        List<Block> blocks = new ArrayList<>();
        addBlocks(body, code, 0, body.size(), blocks);

        List<Integer> starts = new ArrayList<>();
        int pieceSize = 0;
        for (Block block : blocks) {
            int size = block.to - block.from;
            if (size > MAX) {
                // No function starts inside it: split it by lines, on its own.
                starts.addAll(lineStarts(body, code, block.from, block.to));
                pieceSize = MAX + 1; // the next block starts a new piece
            } else if (starts.isEmpty() || (block.startsPiece && pieceSize >= MIN) || pieceSize + size > MAX) {
                starts.add(block.from);
                pieceSize = size;
            } else {
                pieceSize += size;
            }
            if (block.endsPiece) pieceSize = MAX + 1;
        }
        // A "\ No newline at end of file" line belongs with the line before it.
        starts.removeIf(s -> s > 0 && body.get(s).startsWith("\\"));
        joinPiecesWithoutChanges(body, starts);
        return starts;
    }

    /**
     * Lines [from, to). The parts of a block too long for one piece don't share a piece with
     * lines outside it, so a long method's header is never packed with the method before it:
     * its first part starts a piece (unless the piece so far is under MIN lines, like a
     * file's imports), and its last part ends one.
     */
    private static final class Block {
        final int from, to;
        boolean startsPiece, endsPiece;

        Block(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }

    /**
     * Adds [from, to) as one block if it fits in a piece, else splits it where blocks start
     * at the least indent that has a start inside it, and looks into each part the same way.
     * A block with no start inside it at any indent is added whole, even if it's too long.
     */
    private static void addBlocks(List<String> body, List<String> code, int from, int to, List<Block> out) {
        if (to - from <= MAX) {
            out.add(new Block(from, to));
            return;
        }
        TreeSet<Integer> indents = new TreeSet<>();
        for (int i = from + 1; i < to; i++) {
            if (isBlockStart(body, code, i)) indents.add(indent(code.get(i)));
        }
        if (indents.isEmpty()) {
            out.add(new Block(from, to));
            return;
        }
        int first = out.size();
        int level = indents.first();
        int start = from;
        for (int i = from + 1; i < to; i++) {
            if (isBlockStart(body, code, i) && indent(code.get(i)) == level) {
                addBlocks(body, code, start, i, out);
                start = i;
            }
        }
        addBlocks(body, code, start, to, out);
        out.get(first).startsPiece = true;
        out.get(out.size() - 1).endsPiece = true;
    }

    /** A real line of code right after an empty line: not a marker or a closing line. */
    private static boolean isBlockStart(List<String> body, List<String> code, int i) {
        String line = code.get(i);
        return code.get(i - 1).isBlank() && !body.get(i).startsWith("\\")
                && !line.isBlank() && !closesBlock(line);
    }

    /**
     * Splits [from, to) into even pieces of at most MAX lines. A cut that would land on a
     * closing line or the no newline marker moves down past it, so it stays with its block.
     */
    private static List<Integer> lineStarts(List<String> body, List<String> code, int from, int to) {
        int count = (to - from + MAX - 1) / MAX;
        int size = (to - from + count - 1) / count;
        List<Integer> starts = new ArrayList<>(List.of(from));
        for (int c = 1; c < count; c++) {
            int cut = from + c * size;
            while (cut < to - 1 && (body.get(cut).startsWith("\\") || closesBlock(code.get(cut)))) cut++;
            if (cut > starts.get(starts.size() - 1)) starts.add(cut);
        }
        return starts;
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
