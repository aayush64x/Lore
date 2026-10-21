package com.lore.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Asks "What were you trying to do?" at lore stop. The answer can be any length:
 * type lines and finish with two empty lines, or type "e" to write it in a text editor.
 * The first line becomes the session's title.
 */
class ReasonInput {

    /** Fits the sessions.reason column. */
    static final int MAX_LENGTH = 20_000;

    private static final String EDITOR_HELP = """

            # What were you trying to do? Write as much as you like above these lines.
            # You can paste the prompt you gave your AI coding tool.
            # The first line becomes the session's title.
            # Lines starting with # are ignored.
            # Save and close the editor when you're done.
            # In nano: press Ctrl+O then Enter to save, then Ctrl+X to close.
            """;

    /** Opens a file in an editor and waits for it to close. Swappable in tests. */
    interface Editor {
        /** Returns false if the editor couldn't be opened or failed. */
        boolean edit(Path file);
    }

    private final BufferedReader in;
    private final PrintStream out;
    private final Optional<Editor> editor;

    ReasonInput(BufferedReader in, PrintStream out, Optional<Editor> editor) {
        this.in = in;
        this.out = out;
        this.editor = editor;
    }

    /** The reason as typed (trimmed, capped at MAX_LENGTH), or null if skipped. */
    String read() throws IOException {
        out.println("What were you trying to do? Write as much as you like.");
        out.println("  When you're done, press Enter 2 more times.");
        if (editor.isPresent()) {
            out.println("  Or type e and press Enter to write it in a text editor.");
        }
        out.println("  Press Enter now to skip.");
        out.print("> ");
        out.flush();

        String first = in.readLine();
        if (first == null || first.isBlank()) return null;
        if (editor.isPresent() && first.strip().equalsIgnoreCase("e")) {
            return fromEditor();
        }
        return cap(typedLines(first));
    }

    /** Reads until two empty lines in a row (or end of input). One empty line stays as a paragraph break. */
    private String typedLines(String first) throws IOException {
        List<String> lines = new ArrayList<>(List.of(first));
        while (true) {
            String line = in.readLine();
            if (line == null) break;
            boolean previousBlank = lines.get(lines.size() - 1).isBlank();
            if (line.isBlank() && previousBlank) break;
            lines.add(line);
        }
        return String.join("\n", lines).strip();
    }

    private String fromEditor() throws IOException {
        Path file = Files.createTempFile("lore-reason", ".md");
        try {
            Files.writeString(file, EDITOR_HELP, StandardCharsets.UTF_8);
            out.println("Opening your editor. Save and close it to continue...");
            if (!editor.get().edit(file)) {
                out.println("Couldn't open an editor. Type it here instead.");
                out.print("> ");
                out.flush();
                String first = in.readLine();
                return first == null || first.isBlank() ? null : cap(typedLines(first));
            }
            String text = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(l -> !l.startsWith("#"))
                    .collect(Collectors.joining("\n"))
                    .strip();
            if (text.isEmpty()) {
                out.println("Nothing was written in the editor.");
                return null;
            }
            return cap(text);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private String cap(String text) {
        if (text.isEmpty()) return null;
        if (text.length() <= MAX_LENGTH) return text;
        out.println("(That's very long, so only the first " + MAX_LENGTH + " characters are saved.)");
        return text.substring(0, MAX_LENGTH);
    }

    /** The first line, used as the session's title in the terminal. */
    static String title(String reason) {
        return reason.lines().findFirst().orElse(reason).strip();
    }

    /**
     * The editor to use: $VISUAL, then $EDITOR (like git), then Notepad on Windows,
     * then nano. Never falls back to vi/vim, which confuses people who haven't used it.
     */
    static Optional<List<String>> editorCommand(Map<String, String> env, String osName, boolean nanoInstalled) {
        for (String var : List.of("VISUAL", "EDITOR")) {
            String value = env.get(var);
            if (value != null && !value.isBlank()) {
                return Optional.of(List.of(value.strip().split("\\s+")));
            }
        }
        if (osName.toLowerCase().startsWith("windows")) return Optional.of(List.of("notepad"));
        if (nanoInstalled) return Optional.of(List.of("nano"));
        return Optional.empty();
    }

    /** True if a program with this name is on the PATH. */
    static boolean onPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir, program))) return true;
        }
        return false;
    }

    /** Runs the editor in this terminal and waits for it to close. */
    static Editor systemEditor(List<String> command) {
        return file -> {
            List<String> full = new ArrayList<>(command);
            full.add(file.toString());
            try {
                return new ProcessBuilder(full).inheritIO().start().waitFor() == 0;
            } catch (IOException e) {
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
    }
}
