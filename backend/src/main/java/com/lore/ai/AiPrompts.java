package com.lore.ai;

import java.util.List;

/**
 * Prompt text shared by every AiService implementation, so Bedrock and the
 * Anthropic API see exactly the same instructions.
 */
public final class AiPrompts {

    static final int MAX_DIFF_SIZE = 100_000;
    static final int MAX_SINGLE_FILE_SIZE = 30_000;

    private AiPrompts() {}

    public static String summarySystemPrompt() {
        return """
                You are a code change summarizer for a learning tool called Lore. \
                Your job is to help developers understand what changed in their coding session.

                You will receive every change from a coding session. Each change starts with a header like \
                "### Change display_order=3 | MODIFIED | src/app.js" followed by that change's own diff. \
                Return:
                1. A "session_summary" - a plain-English summary of the whole session (2-4 sentences). \
                Explain what was accomplished, not just list files. Write for someone who wants to understand \
                what happened, not someone reviewing a PR.
                2. A "change_descriptions" array with exactly one entry per change. Each entry has:
                   - "display_order": copied exactly from that change's header
                   - "file_path": copied exactly from that change's header
                   - "description": one line saying what that change does in plain English
                Describe each change using only the diff directly under its own header.

                Every text value is plain sentences. Never end a text with JSON characters such as \
                }, ] or a quote: the JSON structure is handled for you.""";
    }

    /**
     * The summary prompt plus the goal check, used only when the developer said what
     * they were trying to do. Without a goal the plain prompt is used and the AI never
     * sees these fields, so it can't make a goal up.
     */
    public static String summaryWithGoalSystemPrompt() {
        return summarySystemPrompt() + """


                GOAL CHECK: the developer's goal is quoted at the top of the message ("In their own \
                words, the developer was trying to"). Judge only against that goal as written. Never \
                guess at a goal they didn't state.
                3. For each entry in "change_descriptions" also return:
                   - "matches_goal": YES if the change directly serves the goal, PARTLY if it only \
                supports it or mixes in unrelated edits, NO if it has nothing to do with the goal
                   - "goal_reason": one short line saying why
                4. "goal_parts_not_done": the parts of the goal that no change does, each as a short \
                phrase. An empty list if every part was done.""";
    }

    /**
     * Lists every change with its display_order, type, path and diff together, so the
     * model never has to line up a list of names with a separate block of diffs.
     */
    public static String summaryUserMessage(List<AiService.ChangeInput> changes, String reason) {
        StringBuilder sb = new StringBuilder();
        if (reason != null && !reason.isBlank()) {
            sb.append("In their own words, the developer was trying to:\n").append(reason.strip()).append("\n\n");
        }
        sb.append("Here are the ").append(changes.size()).append(" changes from this session:\n\n");

        int totalSize = 0;
        for (AiService.ChangeInput c : changes) {
            sb.append("### Change display_order=").append(c.displayOrder())
                    .append(" | ").append(c.changeType())
                    .append(" | ").append(c.filePath()).append('\n');

            String diff = c.diffContent();
            if (c.binary()) {
                sb.append("[binary file, no diff]\n");
            } else if (diff == null || diff.isEmpty()) {
                sb.append("[no line changes]\n");
            } else if (diff.length() > MAX_SINGLE_FILE_SIZE) {
                sb.append("[diff too large to include]\n");
            } else if (totalSize + diff.length() > MAX_DIFF_SIZE) {
                sb.append("[diff omitted because the session is too large]\n");
            } else {
                sb.append(diff);
                if (!diff.endsWith("\n")) sb.append('\n');
                totalSize += diff.length();
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** The shared instructions plus a "who you are writing for" part chosen by the user's profile. */
    public static String explainSystemPrompt(UserProfile profile) {
        return """
                You are a tutor in Lore, a learning tool. Someone used an AI coding tool, and it \
                changed their code. They want to understand one of those changes.

                You will receive the file path, what they were trying to do (if they said), and a \
                diff with each line numbered (1, 2, 3...). Lines starting with + were added, \
                lines starting with - were removed, and other lines are unchanged context.

                Return:
                1. "overview": what this change does.
                2. "steps": walk through the change in the order a person would read it. Each step has:
                   - "text": the explanation
                   - "diff_line_start": the first numbered line this step covers
                   - "diff_line_end": the last numbered line this step covers
                   Only use line numbers from the diff. Together, the steps should cover every \
                added or removed line.
                3. "concepts": coding ideas this change adds, removes or changes. Each has "name" \
                and "meaning". Only tag an idea if a line starting with + or - uses it. Never tag \
                an idea that only appears in unchanged context lines, even if it's nearby: if a \
                regex or a loop is untouched, it is not a concept of this change. An empty list \
                is fine.
                4. "key_points": 2-3 short things a person must be able to say, in their own words, \
                to show they understand what this change does and why. About behavior and purpose, \
                not syntax. Each is one short sentence.
                5. "testable": false only if there is nothing to understand: imports, formatting, \
                renames, comments, version bumps. Otherwise true. When false, key_points may be empty.
                6. "possible_issue": an empty list, or exactly one real problem in this change: an \
                edge case it gets wrong or a bug it introduces, that you can show from the numbered \
                lines (for example, a negative index when a length is smaller than what is \
                subtracted). Give the line it's on and say what input breaks it and what happens. \
                Never invent a problem and never guess: most changes have none, and an empty list \
                is the right answer then.

                If you know what they were trying to do, connect the change to it, for example: \
                "You asked for X. This part does that by..."

                Every text value is plain sentences. Never end a text with JSON characters such as \
                }, ] or a quote: the JSON structure is handled for you.

                WHO YOU ARE WRITING FOR:
                """ + audience(profile);
    }

    static String audience(UserProfile profile) {
        boolean learn = profile.goal() == UserProfile.Goal.LEARN_TO_CODE;
        return switch (profile.level()) {
            case NEW_TO_CODING -> learn ? """
                    They are new to coding and want to learn to program.
                    - overview: what the change does for the app, in plain words.
                    - steps: say what each part does for the app, then how the code does it. When a \
                    coding idea first shows up (a variable, a function, a loop...), name it and \
                    explain it simply. 2-4 sentences per step.
                    - concepts: up to 5. Each meaning should make sense to a beginner. Add a tiny \
                    example if it helps."""
                    : """
                    They are new to coding. They want to understand what their app does, not learn \
                    to program.
                    - overview: say what this change does for the app, in words someone who has never \
                    coded would understand. Talk about what a person using the app will notice, e.g. \
                    "The checkout page now adds tax before showing the total."
                    - steps: for each part, first say what it does for the app. Use a code word only \
                    when you have to, and explain it in a few plain words. Short sentences, 1-3 per step.
                    - concepts: at most 3, listed briefly. Each meaning is one short sentence, with an \
                    everyday comparison if it helps.""";
            case KNOWS_SOME -> learn ? """
                    They know some coding and want to get better at it.
                    - overview: what the change does and why.
                    - steps: explain what each part does and why it's written that way. Point out \
                    patterns worth learning. 2-3 sentences per step.
                    - concepts: up to 5, the ones most worth learning from this change."""
                    : """
                    They know some coding. They mostly want to understand what their app does.
                    - overview: what the change does for the app.
                    - steps: start with what each part does for the app, then how. Normal code words \
                    are fine; explain the less common ones. 1-3 sentences per step.
                    - concepts: at most 3, only the ones that help explain this change.""";
            case DEVELOPER -> """
                    They are an experienced developer. Be short and technical.
                    - overview: one sentence.
                    - steps: 1-2 sentences each, with precise technical terms. Skip what is obvious from \
                    the code. Point out anything that isn't obvious: edge cases, possible bugs, side \
                    effects, performance or security issues.
                    - concepts: only ones that aren't obvious (library APIs, patterns), at most 3. \
                    An empty list is fine.""" + (learn ? """

                    Mention better ways to write it, or common ways to do the same thing, when there \
                    are some.""" : "");
        };
    }

    public static String explainUserMessage(String filePath, String diffContent, String reason) {
        String[] lines = (diffContent == null ? "" : diffContent).split("\n", -1);
        StringBuilder sb = new StringBuilder("File: ").append(filePath).append("\n\n");
        if (reason != null && !reason.isBlank()) {
            sb.append("What they were trying to do, in their own words:\n").append(reason.strip()).append("\n\n");
        }
        sb.append("Diff:\n");
        for (int i = 0; i < lines.length; i++) {
            sb.append(i + 1).append(": ").append(lines[i]).append('\n');
        }
        return sb.toString();
    }
}
