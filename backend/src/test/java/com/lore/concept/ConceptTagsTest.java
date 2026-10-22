package com.lore.concept;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConceptTagsTest {

    private static final List<String> NAMES = List.of("Loops", "Functions", "Error handling", "Testing", "State");

    private static ConceptTags.Tag on(String name) { return new ConceptTags.Tag(name, false); }
    private static ConceptTags.Tag other(String name) { return new ConceptTags.Tag(name, true); }

    @Test
    void names_on_the_list_are_kept_in_the_lists_spelling() {
        assertEquals(List.of(on("Loops"), on("Error handling")),
                ConceptTags.clean(List.of("loops", " ERROR handling "), NAMES));
    }

    @Test
    void at_most_three_and_no_repeats() {
        assertEquals(List.of(on("Loops"), on("Functions"), on("Testing")),
                ConceptTags.clean(List.of("Loops", "loops", "Functions", "Testing", "State"), NAMES));
    }

    @Test
    void names_not_on_the_list_are_dropped_when_a_listed_one_fits() {
        assertEquals(List.of(on("Functions")),
                ConceptTags.clean(List.of("Recursion", "Functions", "Other: Closures"), NAMES));
    }

    @Test
    void other_is_kept_apart_only_when_nothing_on_the_list_fits() {
        assertEquals(List.of(other("WebSockets")), ConceptTags.clean(List.of("Other: WebSockets"), NAMES));
        assertEquals(List.of(other("WebSockets")), ConceptTags.clean(List.of("other:WebSockets", "Other: Regex"), NAMES),
                "only one Other");
        assertEquals(List.of(other("Recursion")), ConceptTags.clean(List.of("Recursion"), NAMES),
                "an off-list name with no Other: prefix is still only an Other");
    }

    @Test
    void other_naming_a_listed_concept_is_that_concept() {
        assertEquals(List.of(on("Loops")), ConceptTags.clean(List.of("Other: loops"), NAMES));
    }

    @Test
    void empty_or_missing_gives_no_tags() {
        assertEquals(List.of(), ConceptTags.clean(null, NAMES));
        assertEquals(List.of(), ConceptTags.clean(List.of(), NAMES));
        assertEquals(List.of(), ConceptTags.clean(List.of(" ", "Other:  "), NAMES));
    }

    @Test
    void a_long_other_name_is_cut_to_fit() {
        String name = ConceptTags.clean(List.of("Other: " + "x".repeat(200)), NAMES).get(0).name();
        assertEquals(ConceptTags.MAX_OTHER_LENGTH, name.length());
    }
}
