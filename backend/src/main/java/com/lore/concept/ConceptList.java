package com.lore.concept;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The concepts a change can be tagged with, from concepts.txt: {lore.home}/concepts.txt if
 * the user made one, else the copy built into Lore. Read again on every use, so an edit
 * counts from the next summary on. A line is "Name | one-sentence meaning", or just "Name".
 * Lines starting with # are comments, except the one right above a group of concepts,
 * which names the group.
 */
@Component
public class ConceptList {

    static final String FILE = "concepts.txt";

    /** meaning is the sentence after "|" on the concept's line; null if there is none. */
    public record Concept(String name, String group, String meaning) {}

    private final Path userFile;

    public ConceptList(@Value("${lore.home}") Path loreHome) {
        this.userFile = loreHome.resolve(FILE);
    }

    public List<Concept> concepts() {
        return parse(read());
    }

    public List<String> names() {
        return concepts().stream().map(Concept::name).toList();
    }

    /** The list's own spelling of a name, matched ignoring case and extra spaces. */
    public static Optional<String> find(String name, List<String> names) {
        String key = key(name);
        return names.stream().filter(n -> key(n).equals(key)).findFirst();
    }

    static String key(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String read() {
        try {
            if (Files.isRegularFile(userFile)) return Files.readString(userFile);
            try (InputStream in = ConceptList.class.getResourceAsStream("/" + FILE)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + FILE, e);
        }
    }

    static List<Concept> parse(String text) {
        List<String> lines = text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        List<Concept> out = new ArrayList<>();
        String group = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("#")) {
                // A heading only if a concept comes right after it; otherwise just a comment.
                if (i + 1 < lines.size() && !lines.get(i + 1).startsWith("#")) group = line.substring(1).strip();
            } else {
                // "Name | meaning"; a line with no "|" is just a name.
                int bar = line.indexOf('|');
                String name = (bar < 0 ? line : line.substring(0, bar)).strip();
                String meaning = bar < 0 ? null : line.substring(bar + 1).strip();
                if (!name.isEmpty() && out.stream().noneMatch(c -> key(c.name()).equals(key(name)))) {
                    out.add(new Concept(name, group, meaning == null || meaning.isEmpty() ? null : meaning));
                }
            }
        }
        return out;
    }
}
