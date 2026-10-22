package com.lore.concept;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConceptListTest {

    @Test
    void the_built_in_list_has_every_concept_with_its_group(@TempDir Path home) {
        List<ConceptList.Concept> concepts = new ConceptList(home).concepts();

        assertEquals(39, concepts.size());
        assertEquals(new ConceptList.Concept("HTML structure", "Web basics",
                "The tags that build a web page, like headings, paragraphs and buttons."), concepts.get(0));
        assertEquals("React", concepts.stream().filter(c -> c.name().equals("Side effects (useEffect)")).findFirst().orElseThrow().group());
        assertEquals("Dependencies & packages", concepts.get(38).name());
        assertEquals("General", concepts.get(38).group());
        assertEquals("Programming fundamentals",
                concepts.stream().filter(c -> c.name().equals("Regular expressions")).findFirst().orElseThrow().group());
        assertTrue(concepts.stream().allMatch(c -> c.meaning() != null && !c.meaning().isBlank()),
                "every built-in concept has a meaning");
        assertTrue(concepts.stream().noneMatch(c -> c.name().startsWith("#")), "comments are never concepts");
    }

    @Test
    void a_list_in_lore_home_replaces_the_built_in_one(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("concepts.txt"), """
                # My notes, not a heading (a heading comes right before a concept)

                # Basics
                Loops
                loops
                  Functions\s\s

                # Extra
                Generics
                """);

        assertEquals(List.of(
                new ConceptList.Concept("Loops", "Basics", null),
                new ConceptList.Concept("Functions", "Basics", null),
                new ConceptList.Concept("Generics", "Extra", null)), new ConceptList(home).concepts());
    }

    @Test
    void a_meaning_after_a_bar_is_read_and_a_line_without_one_still_works(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("concepts.txt"), """
                # Basics
                Loops | Doing the same steps again.
                Functions
                Arrays |
                Maps|Values stored under names | with a bar inside
                """);

        assertEquals(List.of(
                new ConceptList.Concept("Loops", "Basics", "Doing the same steps again."),
                new ConceptList.Concept("Functions", "Basics", null),
                new ConceptList.Concept("Arrays", "Basics", null),
                new ConceptList.Concept("Maps", "Basics", "Values stored under names | with a bar inside")),
                new ConceptList(home).concepts());
        assertEquals(List.of("Loops", "Functions", "Arrays", "Maps"), new ConceptList(home).names(),
                "the AI only ever sees the names");
    }

    @Test
    void find_ignores_case_and_extra_spaces_and_returns_the_lists_spelling() {
        List<String> names = List.of("Async code (promises/await)", "Loops");
        assertEquals("Async code (promises/await)", ConceptList.find("  async   CODE (promises/await) ", names).orElseThrow());
        assertTrue(ConceptList.find("Async code", names).isEmpty(), "close is not a match");
    }
}
