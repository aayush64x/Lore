package com.lore.git;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HunkSplitterTest {

    /** A Java-like method inside a class: a comment, a header, body lines, a closing brace. */
    private static List<String> method(String name, int bodyLines, boolean edited) {
        List<String> m = new ArrayList<>(List.of("     /** " + name + " */", "     void " + name + "() {"));
        for (int i = 0; i < bodyLines; i++) {
            m.add((edited && i == 0 ? "+" : " ") + "        step" + i + "();");
        }
        m.add("     }");
        return m;
    }

    /** Hunk body lines: methods separated by one empty context line, each one edited. */
    private static List<String> hunk(int... bodySizes) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < bodySizes.length; i++) {
            if (i > 0) lines.add(" ");
            lines.addAll(method("m" + i, bodySizes[i], true));
        }
        return lines;
    }

    private static List<Integer> sizes(List<String> body, List<Integer> starts) {
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : body.size();
            sizes.add(end - starts.get(i));
        }
        return sizes;
    }

    @Test
    void short_hunks_are_one_piece() {
        assertEquals(List.of(0), HunkSplitter.pieceStarts(hunk(20, 20)));
        assertEquals(List.of(0), HunkSplitter.pieceStarts(hunk(77))); // exactly 80 lines
    }

    @Test
    void pieces_start_at_a_method_with_its_comment_and_hold_whole_methods() {
        List<String> body = hunk(20, 20, 20, 20, 20, 20); // 6 methods of 23 lines
        List<Integer> starts = HunkSplitter.pieceStarts(body);

        assertTrue(starts.size() >= 2, "starts: " + starts);
        for (int s : starts.subList(1, starts.size())) {
            assertTrue(body.get(s).contains("/** m"), "piece should start at a method's comment: " + body.get(s));
            assertEquals(" ", body.get(s - 1));
        }
        // Each method is 23 lines plus the empty line after it: 3 fit (72), a 4th would pass 80.
        assertEquals(List.of(72, 71), sizes(body, starts));
    }

    @Test
    void a_blank_line_inside_a_method_is_not_a_boundary() {
        List<String> body = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i > 0) body.add(" ");
            body.addAll(method("m" + i, 10, true));
            // An empty line inside the body, then more body: deeper indent, so never a cut.
            body.add(body.size() - 1, " ");
            body.add(body.size() - 1, "+        more" + i + "();");
        }
        body.addAll(List.of(" ", "     /** last */", "     void last() {"));
        for (int i = 0; i < 30; i++) body.add("+        x" + i + "();");
        body.add("     }");

        for (int s : HunkSplitter.pieceStarts(body).subList(1, HunkSplitter.pieceStarts(body).size())) {
            assertTrue(body.get(s).contains("/**"), "cut inside a method at: " + body.get(s));
        }
    }

    @Test
    void a_method_longer_than_a_piece_is_split_by_lines_on_its_own() {
        List<String> body = new ArrayList<>(method("small", 10, true));
        body.add(" ");
        // Every line of the huge method is new, as in a real hunk (git never keeps long unchanged runs).
        method("huge", 150, true).forEach(l -> body.add(l.startsWith(" ") && l.contains("step") ? "+" + l.substring(1) : l));
        body.add(" ");
        body.addAll(method("after", 10, true));

        List<Integer> starts = HunkSplitter.pieceStarts(body);
        List<Integer> sizes = sizes(body, starts);

        assertEquals(0, starts.get(0));
        assertEquals(14, starts.get(1), "the huge method starts its own piece");
        for (int size : sizes) assertTrue(size <= 80, "size " + size);
        int afterStart = body.indexOf("     /** after */");
        assertTrue(starts.contains(afterStart), "the method after the huge one starts a new piece: " + starts);
    }

    @Test
    void a_piece_with_only_unchanged_lines_joins_its_neighbor() {
        List<String> body = new ArrayList<>(method("a", 60, true));
        body.add(" ");
        body.addAll(method("untouched", 3, false)); // context only, between two big methods
        body.add(" ");
        body.addAll(method("b", 60, true));

        List<Integer> starts = HunkSplitter.pieceStarts(body);

        assertEquals(2, starts.size(), "starts: " + starts);
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : body.size();
            assertTrue(body.subList(starts.get(i), end).stream().anyMatch(l -> l.startsWith("+")),
                    "piece " + i + " has no change");
        }
    }

    @Test
    void the_no_newline_marker_stays_with_the_line_before_it() {
        List<String> body = hunk(30, 30, 30);
        body.add("\\ No newline at end of file");

        List<Integer> starts = HunkSplitter.pieceStarts(body);

        for (int s : starts) assertFalse(body.get(s).startsWith("\\"));
    }
}
