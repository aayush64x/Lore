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

        assertEquals(38, concepts.size());
        assertEquals(new ConceptList.Concept("HTML structure", "Web basics"), concepts.get(0));
        assertEquals(new ConceptList.Concept("Side effects (useEffect)", "React"),
                concepts.stream().filter(c -> c.name().startsWith("Side effects")).findFirst().orElseThrow());
        assertEquals(new ConceptList.Concept("Dependencies & packages", "General"), concepts.get(37));
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
                new ConceptList.Concept("Loops", "Basics"),
                new ConceptList.Concept("Functions", "Basics"),
                new ConceptList.Concept("Generics", "Extra")), new ConceptList(home).concepts());
    }

    @Test
    void find_ignores_case_and_extra_spaces_and_returns_the_lists_spelling() {
        List<String> names = List.of("Async code (promises/await)", "Loops");
        assertEquals("Async code (promises/await)", ConceptList.find("  async   CODE (promises/await) ", names).orElseThrow());
        assertTrue(ConceptList.find("Async code", names).isEmpty(), "close is not a match");
    }
}
