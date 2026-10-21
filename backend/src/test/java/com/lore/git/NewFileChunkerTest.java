package com.lore.git;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewFileChunkerTest {

    /** A JS-like function: a comment, a header, indented body lines, a closing brace. */
    private static List<String> function(String name, int bodyLines) {
        List<String> f = new ArrayList<>(List.of("// " + name, "function " + name + "() {"));
        for (int i = 0; i < bodyLines; i++) f.add("  step" + i + "();");
        f.add("}");
        return f;
    }

    /** Functions separated by one empty line, like most real files. */
    private static List<String> file(int... bodySizes) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < bodySizes.length; i++) {
            if (i > 0) lines.add("");
            lines.addAll(function("f" + i, bodySizes[i]));
        }
        return lines;
    }

    private static List<Integer> sizes(List<String> lines) {
        List<Integer> starts = NewFileChunker.chunkStarts(lines);
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : lines.size();
            sizes.add(end - starts.get(i));
        }
        return sizes;
    }

    @Test
    void short_files_are_one_chunk() {
        assertEquals(List.of(0), NewFileChunker.chunkStarts(file(20, 20)));
        assertEquals(List.of(0), NewFileChunker.chunkStarts(file(76))); // exactly 80 lines
    }

    @Test
    void cuts_where_a_function_starts_with_its_comment() {
        List<String> lines = file(30, 30, 30, 30); // 4 functions of 33 lines + 3 empty lines
        List<Integer> starts = NewFileChunker.chunkStarts(lines);

        for (int s : starts.subList(1, starts.size())) {
            assertTrue(lines.get(s).startsWith("// f"), "chunk should start at a comment: " + lines.get(s));
            assertEquals("", lines.get(s - 1));
        }
        for (int size : sizes(lines)) assertTrue(size >= 30 && size <= 80, "size " + size);
    }

    @Test
    void never_cuts_at_a_closing_brace_or_inside_an_indented_block() {
        List<String> lines = new ArrayList<>(file(40));
        lines.add("");
        lines.add("}");               // top-level closing brace after an empty line
        lines.add("");
        lines.addAll(function("g", 40));
        List<Integer> starts = NewFileChunker.chunkStarts(lines);

        assertEquals(2, starts.size());
        assertEquals("// g", lines.get(starts.get(1)));
    }

    @Test
    void falls_back_to_an_empty_line_inside_a_big_block() {
        List<String> lines = new ArrayList<>(List.of("class Big {"));
        for (int i = 0; i < 150; i++) lines.add(i % 20 == 19 ? "" : "    line" + i + "();");
        lines.add("}");

        List<Integer> starts = NewFileChunker.chunkStarts(lines);
        assertTrue(starts.size() > 1);
        for (int s : starts.subList(1, starts.size())) assertEquals("", lines.get(s - 1));
        for (int size : sizes(lines)) assertTrue(size >= 30 && size <= 80, "size " + size);
    }

    @Test
    void cuts_at_80_lines_when_there_are_no_empty_lines() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 200; i++) lines.add("x" + i);
        assertEquals(List.of(80, 80, 40), sizes(lines));
    }

    @Test
    void never_leaves_a_tiny_last_chunk() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 85; i++) lines.add("x" + i);
        assertEquals(List.of(55, 30), sizes(lines));
    }

    @Test
    void prefers_the_least_indented_section_inside_a_big_block() {
        List<String> lines = new ArrayList<>(List.of("function Page() {"));
        for (int i = 0; i < 40; i++) lines.add("  const a" + i + " = 1;");
        lines.add("");
        lines.add("  return (");                        // indent 2: a better cut
        for (int i = 0; i < 20; i++) lines.add("    <p>" + i + "</p>");
        lines.add("");
        lines.add("      <span>deep</span>");          // indent 6: later, but deeper
        for (int i = 0; i < 40; i++) lines.add("    <p>more</p>");
        lines.add("  );");
        lines.add("}");

        List<Integer> starts = NewFileChunker.chunkStarts(lines);
        assertEquals("  return (", lines.get(starts.get(1)));
    }
}
