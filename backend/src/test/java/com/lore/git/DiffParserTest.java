package com.lore.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffParserTest {

    private DiffParser parser;

    @BeforeEach
    void setUp() {
        parser = new DiffParser();
    }

    @Test
    void single_file_single_hunk() {
        String diff = """
                diff --git a/hello.txt b/hello.txt
                --- a/hello.txt
                +++ b/hello.txt
                @@ -1,3 +1,4 @@
                 line1
                -line2
                +line2modified
                +line3
                 line4
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(1, changes.size());
        var c = changes.get(0);
        assertEquals("hello.txt", c.filePath());
        assertEquals("MODIFIED", c.changeType());
        assertEquals(1, c.oldStart());
        assertEquals(3, c.oldCount());
        assertEquals(1, c.newStart());
        assertEquals(4, c.newCount());
        assertFalse(c.binary());
        assertNull(c.oldFilePath());
        assertTrue(c.diffContent().contains("-line2"));
        assertTrue(c.diffContent().contains("+line2modified"));
    }

    @Test
    void single_file_multiple_hunks() {
        String diff = """
                diff --git a/app.java b/app.java
                --- a/app.java
                +++ b/app.java
                @@ -1,3 +1,3 @@
                 first
                -old1
                +new1
                 mid
                @@ -10,3 +10,3 @@
                 second
                -old2
                +new2
                 end
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(2, changes.size());
        assertEquals(1, changes.get(0).oldStart());
        assertEquals(10, changes.get(1).oldStart());
        assertEquals(0, changes.get(0).displayOrder());
        assertEquals(1, changes.get(1).displayOrder());
    }

    @Test
    void added_file() {
        String diff = """
                diff --git a/new.txt b/new.txt
                new file mode 100644
                --- /dev/null
                +++ b/new.txt
                @@ -0,0 +1,2 @@
                +hello
                +world
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(1, changes.size());
        assertEquals("ADDED", changes.get(0).changeType());
        assertEquals("new.txt", changes.get(0).filePath());
        assertEquals(0, changes.get(0).oldStart());
        assertEquals(0, changes.get(0).oldCount());
        assertEquals(1, changes.get(0).newStart());
        assertEquals(2, changes.get(0).newCount());
    }

    @Test
    void deleted_file() {
        String diff = """
                diff --git a/old.txt b/old.txt
                deleted file mode 100644
                --- a/old.txt
                +++ /dev/null
                @@ -1,2 +0,0 @@
                -goodbye
                -world
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(1, changes.size());
        assertEquals("DELETED", changes.get(0).changeType());
        assertEquals("old.txt", changes.get(0).filePath());
    }

    @Test
    void renamed_file() {
        String diff = """
                diff --git a/old-name.txt b/new-name.txt
                similarity index 95%
                rename from old-name.txt
                rename to new-name.txt
                --- a/old-name.txt
                +++ b/new-name.txt
                @@ -1,3 +1,3 @@
                 same
                -old
                +new
                 same
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(1, changes.size());
        var c = changes.get(0);
        assertEquals("RENAMED", c.changeType());
        assertEquals("new-name.txt", c.filePath());
        assertEquals("old-name.txt", c.oldFilePath());
    }

    @Test
    void binary_file() {
        String diff = """
                diff --git a/image.png b/image.png
                Binary files a/image.png and b/image.png differ
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(1, changes.size());
        var c = changes.get(0);
        assertTrue(c.binary());
        assertNull(c.diffContent());
        assertEquals("image.png", c.filePath());
    }

    @Test
    void skips_lockfiles() {
        String diff = """
                diff --git a/package-lock.json b/package-lock.json
                --- a/package-lock.json
                +++ b/package-lock.json
                @@ -1,3 +1,3 @@
                 {
                -  "version": "1.0"
                +  "version": "1.1"
                 }
                diff --git a/src/app.js b/src/app.js
                --- a/src/app.js
                +++ b/src/app.js
                @@ -1,2 +1,2 @@
                -old
                +new
                 end
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(1, changes.size());
        assertEquals("src/app.js", changes.get(0).filePath());
    }

    @Test
    void skips_build_output_directories() {
        String diff = """
                diff --git a/target/classes/App.class b/target/classes/App.class
                Binary files /dev/null and b/target/classes/App.class differ
                diff --git a/src/App.java b/src/App.java
                --- a/src/App.java
                +++ b/src/App.java
                @@ -1,2 +1,2 @@
                -old
                +new
                 end
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(1, changes.size());
        assertEquals("src/App.java", changes.get(0).filePath());
    }

    @Test
    void display_order_sequential() {
        String diff = """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -a
                +aa
                diff --git a/b.txt b/b.txt
                --- a/b.txt
                +++ b/b.txt
                @@ -1 +1 @@
                -b
                +bb
                diff --git a/c.txt b/c.txt
                --- a/c.txt
                +++ b/c.txt
                @@ -1 +1 @@
                -c
                +cc
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(3, changes.size());
        assertEquals(0, changes.get(0).displayOrder());
        assertEquals(1, changes.get(1).displayOrder());
        assertEquals(2, changes.get(2).displayOrder());
    }

    @Test
    void hunk_header_without_count_defaults_to_one() {
        String diff = """
                diff --git a/one.txt b/one.txt
                --- a/one.txt
                +++ b/one.txt
                @@ -1 +1 @@
                -old
                +new
                """;

        List<DiffParser.ParsedChange> changes = parser.parse(diff);

        assertEquals(1, changes.size());
        assertEquals(1, changes.get(0).oldCount());
        assertEquals(1, changes.get(0).newCount());
    }

    @Test
    void empty_diff_returns_empty_list() {
        assertEquals(0, parser.parse("").size());
        assertEquals(0, parser.parse(null).size());
    }

    private static String newFileDiff(String path, int lines, boolean noNewlineAtEnd) {
        StringBuilder sb = new StringBuilder()
                .append("diff --git a/").append(path).append(" b/").append(path).append('\n')
                .append("new file mode 100644\n--- /dev/null\n+++ b/").append(path).append('\n')
                .append("@@ -0,0 +1,").append(lines).append(" @@\n");
        for (int i = 1; i <= lines; i++) {
            // A new function every 25 lines, each after an empty line.
            sb.append('+').append(i % 25 == 0 ? "" : (i % 25 == 1 ? "function f" + i + "() {" : "  x();")).append('\n');
        }
        if (noNewlineAtEnd) sb.append("\\ No newline at end of file\n");
        return sb.toString();
    }

    @Test
    void long_new_file_is_split_into_chunks_with_their_own_headers() {
        List<DiffParser.ParsedChange> changes = parser.parse(newFileDiff("app.js", 200, true)
                + "diff --git a/b.txt b/b.txt\n--- a/b.txt\n+++ b/b.txt\n@@ -1 +1 @@\n-a\n+b\n");

        List<DiffParser.ParsedChange> chunks = changes.stream().filter(c -> c.filePath().equals("app.js")).toList();
        assertTrue(chunks.size() >= 3, "chunks: " + chunks.size());

        int expectedStart = 1;
        for (int i = 0; i < chunks.size(); i++) {
            DiffParser.ParsedChange c = chunks.get(i);
            assertEquals("ADDED", c.changeType());
            assertEquals(i, c.displayOrder());
            assertEquals(expectedStart, c.newStart());
            assertTrue(c.newCount() >= 30 && c.newCount() <= 80, "count " + c.newCount());
            assertTrue(c.diffContent().startsWith("@@ -0,0 +" + c.newStart() + "," + c.newCount() + " @@\n"));
            // header + one "+" line per counted line (+ the no-newline marker on the last chunk)
            long plusLines = c.diffContent().lines().filter(l -> l.startsWith("+")).count();
            assertEquals(c.newCount(), plusLines);
            if (i > 0) assertTrue(c.diffContent().lines().skip(1).findFirst().orElseThrow().startsWith("+function"));
            expectedStart += c.newCount();
        }
        assertEquals(201, expectedStart, "chunks cover every line exactly once");
        assertTrue(chunks.get(chunks.size() - 1).diffContent().endsWith("\\ No newline at end of file\n"));
        assertFalse(chunks.get(0).diffContent().contains("No newline"));

        // The file after it keeps going with the next display order.
        assertEquals(chunks.size(), changes.get(changes.size() - 1).displayOrder());
    }

    @Test
    void new_file_of_80_lines_stays_one_change() {
        List<DiffParser.ParsedChange> changes = parser.parse(newFileDiff("small.js", 80, false));
        assertEquals(1, changes.size());
        assertEquals(80, changes.get(0).newCount());
    }
}
