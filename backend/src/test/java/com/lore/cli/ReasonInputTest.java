package com.lore.cli;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReasonInputTest {

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    private String read(String typed, Optional<ReasonInput.Editor> editor) throws Exception {
        return new ReasonInput(new BufferedReader(new StringReader(typed)),
                new PrintStream(printed, true, StandardCharsets.UTF_8), editor).read();
    }

    private String read(String typed) throws Exception {
        return read(typed, Optional.empty());
    }

    private String output() {
        return printed.toString(StandardCharsets.UTF_8);
    }

    @Test
    void enter_right_away_skips() throws Exception {
        assertNull(read("\n"));
        assertNull(read(""));
    }

    @Test
    void one_line_then_two_enters() throws Exception {
        assertEquals("add a login page", read("add a login page\n\n\nleftover\n"));
    }

    @Test
    void many_lines_keep_single_empty_lines_as_paragraphs() throws Exception {
        assertEquals("Add login\n\nI asked for a form.\nIt should check the password.",
                read("Add login\n\nI asked for a form.\nIt should check the password.\n\n\n"));
    }

    @Test
    void piped_input_ends_at_end_of_input() throws Exception {
        assertEquals("line one\nline two", read("line one\nline two"));
    }

    @Test
    void very_long_text_is_cut_to_fit() throws Exception {
        String reason = read("x".repeat(ReasonInput.MAX_LENGTH + 50) + "\n\n\n");
        assertEquals(ReasonInput.MAX_LENGTH, reason.length());
        assertTrue(output().contains("only the first"));
    }

    @Test
    void e_opens_the_editor_and_drops_comment_lines() throws Exception {
        ReasonInput.Editor editor = file -> {
            try {
                assertTrue(Files.readString(file).contains("# Lines starting with # are ignored."));
                Files.writeString(file, "Fix the cart\n\nTotals were wrong.\n" + Files.readString(file));
                return true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        assertEquals("Fix the cart\n\nTotals were wrong.", read("e\n", Optional.of(editor)));
        assertTrue(output().contains("type e"));
    }

    @Test
    void empty_editor_counts_as_skipped() throws Exception {
        assertNull(read("e\n", Optional.of(file -> true)));
        assertTrue(output().contains("Nothing was written"));
    }

    @Test
    void editor_that_fails_falls_back_to_typing() throws Exception {
        assertEquals("typed instead", read("e\ntyped instead\n\n\n", Optional.of(file -> false)));
        assertTrue(output().contains("Couldn't open an editor"));
    }

    @Test
    void without_an_editor_e_is_just_text() throws Exception {
        assertEquals("e", read("e\n\n\n"));
        assertFalse(output().contains("type e"));
    }

    @Test
    void editor_choice_follows_visual_then_editor_then_notepad_or_nano() {
        assertEquals(List.of("code", "--wait"),
                ReasonInput.editorCommand(Map.of("VISUAL", "code --wait", "EDITOR", "vim"), "Mac OS X", true).orElseThrow());
        assertEquals(List.of("vim"), ReasonInput.editorCommand(Map.of("EDITOR", "vim"), "Linux", true).orElseThrow());
        assertEquals(List.of("notepad"), ReasonInput.editorCommand(Map.of(), "Windows 11", false).orElseThrow());
        assertEquals(List.of("nano"), ReasonInput.editorCommand(Map.of(), "Mac OS X", true).orElseThrow());
        assertTrue(ReasonInput.editorCommand(Map.of(), "Linux", false).isEmpty(), "never falls back to vi");
    }

    @Test
    void title_is_the_first_line() {
        assertEquals("Add login", ReasonInput.title("Add login\n\nMore details here."));
        assertEquals("one line", ReasonInput.title("one line"));
    }
}
