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
                Describe each change using only the diff directly under its own header.""";
    }

    /**
     * Lists every change with its display_order, type, path and diff together, so the
     * model never has to line up a list of names with a separate block of diffs.
     */
    public static String summaryUserMessage(List<AiService.ChangeInput> changes, String reason) {
        StringBuilder sb = new StringBuilder();
        if (reason != null && !reason.isBlank()) {
            sb.append("The developer said they were trying to: ").append(reason).append("\n\n");
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

    public static String explainSystemPrompt() {
        return """
                You are a code tutor for a learning tool called Lore. \
                A developer made a code change and wants to understand it step by step.

                You will receive a diff with each line numbered sequentially (1, 2, 3...). \
                Return:
                1. An "overview" — one sentence saying what this change does overall.
                2. A "steps" array — walk through the change in logical order. Each step has:
                   - "text": a plain-English explanation of what these lines do and why
                   - "diff_line_start": the first numbered line this step covers
                   - "diff_line_end": the last numbered line this step covers
                3. A "concepts" array — programming concepts used in this change. Each has:
                   - "name": the concept name (e.g. "dependency injection", "array destructuring")
                   - "meaning": a one-sentence explanation a beginner would understand

                Write for someone who can read code but doesn't fully understand it yet. \
                Be concrete — say what the code does, not just that it exists.""";
    }

    public static String explainUserMessage(String filePath, String diffContent) {
        String[] lines = (diffContent == null ? "" : diffContent).split("\n", -1);
        StringBuilder sb = new StringBuilder("File: ").append(filePath).append("\n\n");
        for (int i = 0; i < lines.length; i++) {
            sb.append(i + 1).append(": ").append(lines[i]).append('\n');
        }
        return sb.toString();
    }
}
