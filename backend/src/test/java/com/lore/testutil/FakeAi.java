package com.lore.testutil;

import com.lore.ai.UserProfile;

import com.lore.ai.AiService;
import com.lore.ai.AiUnavailableException;
import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;

import java.util.List;

/**
 * A controllable AI for tests. Register it as a @Primary bean, then set mode per test.
 * Never talks to a network.
 */
public class FakeAi implements AiService {

    public enum Mode { WORKING, NOT_SET_UP, CRASH }

    public volatile Mode mode = Mode.WORKING;
    public volatile int summarizeCalls;
    public volatile int explainCalls;
    public volatile String lastExplainReason;
    public volatile UserProfile lastExplainProfile;
    /** Goal check answer: parts of the goal not done. A file named "unrelated…" gets "no", "partly…" gets "partly". */
    public volatile List<String> goalGaps = List.of("Add a test");
    /** How many of the next answers (summary or explanation) end a text with stray "}]}". */
    public volatile int garbledAnswers;

    public void reset(Mode mode) {
        this.mode = mode;
        summarizeCalls = 0;
        explainCalls = 0;
        garbledAnswers = 0;
        goalGaps = List.of("Add a test");
    }

    /** "}]}" while garbledAnswers lasts, then nothing. */
    private String tail() {
        if (garbledAnswers <= 0) return "";
        garbledAnswers--;
        return "}]}";
    }

    @Override
    public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
        summarizeCalls++;
        fail();
        String summary = "Summary for: " + reason + tail();
        // Like the real service: the goal check only when a goal was given.
        if (reason == null || reason.isBlank()) {
            return new SummaryResponse(summary, changes.stream()
                    .map(c -> new SummaryResponse.ChangeDescription(c.filePath(), c.displayOrder(), "about " + c.filePath()))
                    .toList());
        }
        return new SummaryResponse(summary, changes.stream()
                .map(c -> new SummaryResponse.ChangeDescription(c.filePath(), c.displayOrder(), "about " + c.filePath(),
                        goalMatch(c.filePath()), "because of " + c.filePath()))
                .toList(), goalGaps);
    }

    @Override
    public ExplanationResponse explain(String filePath, String diffContent, String reason, UserProfile profile) {
        explainCalls++;
        lastExplainReason = reason;
        lastExplainProfile = profile;
        fail();
        return new ExplanationResponse("Explains " + filePath,
                List.of(new ExplanationResponse.Step("First line" + tail(), 1, 2)),
                List.of(new ExplanationResponse.Concept("logging", "printing text")));
    }

    private static String goalMatch(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.startsWith("unrelated")) return "no";
        if (name.startsWith("partly")) return "partly";
        return "yes";
    }

    private void fail() {
        switch (mode) {
            case NOT_SET_UP -> throw new AiUnavailableException("AI is not set up.");
            case CRASH -> throw new IllegalStateException("boom");
            case WORKING -> { }
        }
    }
}
