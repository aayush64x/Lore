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
}
