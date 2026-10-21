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

    public void reset(Mode mode) {
        this.mode = mode;
        summarizeCalls = 0;
        explainCalls = 0;
    }

    @Override
    public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
        summarizeCalls++;
        fail();
        return new SummaryResponse("Summary for: " + reason, changes.stream()
                .map(c -> new SummaryResponse.ChangeDescription(c.filePath(), c.displayOrder(), "about " + c.filePath()))
                .toList());
    }

    @Override
    public ExplanationResponse explain(String filePath, String diffContent, String reason, UserProfile profile) {
        explainCalls++;
        lastExplainReason = reason;
        lastExplainProfile = profile;
        fail();
        return new ExplanationResponse("Explains " + filePath,
                List.of(new ExplanationResponse.Step("First line", 1, 2)),
                List.of(new ExplanationResponse.Concept("logging", "printing text")));
    }

    private void fail() {
        switch (mode) {
            case NOT_SET_UP -> throw new AiUnavailableException("AI is not set up.");
            case CRASH -> throw new IllegalStateException("boom");
            case WORKING -> { }
        }
    }
}
