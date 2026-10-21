package com.lore.ai;

import java.util.Arrays;
import java.util.Optional;

/**
 * Who the user is, asked once at first run and changed with "lore config".
 * Saved in config.properties as profile.level and profile.goal (the enum names).
 */
public record UserProfile(Level level, Goal goal) {

    public static final UserProfile DEFAULT = new UserProfile(Level.KNOWS_SOME, Goal.UNDERSTAND_APP);

    /** Labels shown in the terminal, in menu order. */
    interface Choice {
        String label();
    }

    public enum Level implements Choice {
        NEW_TO_CODING("New to coding"),
        KNOWS_SOME("I know some coding"),
        DEVELOPER("I'm a developer");

        private final String label;
        Level(String label) { this.label = label; }
        @Override public String label() { return label; }
    }

    public enum Goal implements Choice {
        UNDERSTAND_APP("Understand what my app does"),
        LEARN_TO_CODE("Learn to code");

        private final String label;
        Goal(String label) { this.label = label; }
        @Override public String label() { return label; }
    }

    /** Saved with each explanation, so a profile change can be noticed. */
    public String key() {
        return level.name() + "/" + goal.name();
    }

    /** Parses a saved enum name; empty if missing or not a known value. */
    static <E extends Enum<E>> Optional<E> parse(Class<E> type, String saved) {
        if (saved == null) return Optional.empty();
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.name().equals(saved.strip()))
                .findFirst();
    }
}
