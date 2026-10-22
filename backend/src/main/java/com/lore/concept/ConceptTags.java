package com.lore.concept;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns the concept names the AI sent for one change into tags it is allowed to have:
 * up to 3 names from the list (in the list's spelling), or, only if none of its names is
 * on the list, one "Other" tag, kept apart so it can be reviewed and added to the list.
 */
public final class ConceptTags {

    static final int MAX = 3;
    static final int MAX_OTHER_LENGTH = 60;
    private static final String OTHER = "other:";

    public record Tag(String name, boolean other) {}

    private ConceptTags() {}

    public static List<Tag> clean(List<String> fromAi, List<String> names) {
        if (fromAi == null) return List.of();
        List<Tag> onList = new ArrayList<>();
        String other = null;
        for (String raw : fromAi) {
            if (raw == null || raw.isBlank()) continue;
            String name = raw.strip();
            boolean markedOther = name.toLowerCase().startsWith(OTHER);
            if (markedOther) name = name.substring(OTHER.length()).strip();
            // "Other: Loops" is still the list's Loops.
            Optional<String> match = ConceptList.find(name, names);
            if (match.isPresent()) {
                Tag tag = new Tag(match.get(), false);
                if (!onList.contains(tag) && onList.size() < MAX) onList.add(tag);
            } else if (other == null && !name.isEmpty()) {
                // "Other: x", or a name that isn't on the list: only used if nothing else fits.
                other = name.length() > MAX_OTHER_LENGTH ? name.substring(0, MAX_OTHER_LENGTH).strip() : name;
            }
        }
        if (!onList.isEmpty()) return onList;
        return other == null ? List.of() : List.of(new Tag(other, true));
    }
}
