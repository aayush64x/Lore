package com.lore.testutil;

import com.lore.ai.UserProfile;

import com.lore.ai.AiService;
import com.lore.ai.AiUnavailableException;
import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.GradeResponse;
import com.lore.ai.dto.SummaryResponse;
import com.lore.cost.AiCallRecorder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A controllable AI for tests. Register it as a @Primary bean, then set mode per test.
 * Never talks to a network.
 */
public class FakeAi implements AiService {

    public enum Mode { WORKING, NOT_SET_UP, CRASH }

    public volatile Mode mode = Mode.WORKING;
    public volatile int summarizeCalls;
    /** How many changes each summarize call got, in the order the calls came in. */
    public final List<Integer> summarizeSizes = new java.util.concurrent.CopyOnWriteArrayList<>();
    public volatile int combineCalls;
    public volatile List<SummaryResponse> lastCombineParts;
    /** Makes combineSummaries fail like CRASH, while summarize keeps working. */
    public volatile boolean crashCombine;
    public volatile int explainCalls;
    public volatile String lastExplainReason;
    public volatile UserProfile lastExplainProfile;
    /** Goal check answer: parts of the goal not done. A file named "unrelated…" gets "no", "partly…" gets "partly". */
    public volatile List<String> goalGaps = List.of("Add a test");
    /** Explanation extras: a real issue to report (default none) and whether the change is testable. */
    public volatile ExplanationResponse.Issue explainIssue;
    public volatile boolean explainTestable = true;
    /** Grades to hand out, in order ("pass" / "partly" / "fail"); "pass" once empty. */
    public final Deque<String> gradeVerdicts = new ArrayDeque<>();
    public volatile int gradeCalls;
    public volatile String lastGradeDiff;
    public volatile List<String> lastGradeKeyPoints;
    public volatile String lastGradeAnswer;
    /** How many of the next answers (summary or explanation) end a text with stray "}]}". */
    public volatile int garbledAnswers;
    /**
     * When set, every call reports token counts to it like the real service does:
     * model "fake-<job>", 1000 input, 100 output, 10 cache read, 1 cache write.
     */
    public volatile AiCallRecorder recorder;

    private void reportTokens(String job) {
        if (recorder != null) recorder.record("fake-" + job, 1000, 100, 10, 1);
    }

    public void reset(Mode mode) {
        this.mode = mode;
        summarizeCalls = 0;
        summarizeSizes.clear();
        combineCalls = 0;
        lastCombineParts = null;
        crashCombine = false;
        explainCalls = 0;
        garbledAnswers = 0;
        goalGaps = List.of("Add a test");
        conceptNames = List.of("functions", "Made-up concept", "Loops");
        explainIssue = null;
        explainTestable = true;
        gradeVerdicts.clear();
        gradeCalls = 0;
        lastGradeDiff = null;
        lastGradeKeyPoints = null;
        lastGradeAnswer = null;
    }

    /** "}]}" while garbledAnswers lasts, then nothing. */
    private String tail() {
        if (garbledAnswers <= 0) return "";
        garbledAnswers--;
        return "}]}";
    }

    /**
     * The concept names the summary sends for a file, as an AI might write them: a file
     * named "other…" gets only an off-list suggestion; any other file gets conceptNames.
     */
    public volatile List<String> conceptNames = List.of("functions", "Made-up concept", "Loops");

    private List<String> conceptsFor(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.startsWith("other") ? List.of("Other: WebSockets") : conceptNames;
    }

    /** synchronized: a big session's parts are summarized at the same time. */
    @Override
    public synchronized SummaryResponse summarize(List<ChangeInput> changes, String reason) {
        summarizeCalls++;
        summarizeSizes.add(changes.size());
        reportTokens("summary");
        fail();
        String summary = "Summary for: " + reason + tail();
        // Like the real service: the goal check only when a goal was given.
        if (reason == null || reason.isBlank()) {
            return new SummaryResponse(summary, changes.stream()
                    .map(c -> new SummaryResponse.ChangeDescription(c.filePath(), c.displayOrder(), "about " + c.filePath(),
                            null, null, conceptsFor(c.filePath())))
                    .toList());
        }
        return new SummaryResponse(summary, changes.stream()
                .map(c -> new SummaryResponse.ChangeDescription(c.filePath(), c.displayOrder(), "about " + c.filePath(),
                        goalMatch(c.filePath()), "because of " + c.filePath(), conceptsFor(c.filePath())))
                .toList(), goalGaps);
    }

    @Override
    public SummaryResponse combineSummaries(List<SummaryResponse> parts, String reason) {
        combineCalls++;
        lastCombineParts = parts;
        reportTokens("combine");
        fail();
        if (crashCombine) throw new IllegalStateException("boom");
        String summary = "Combined " + parts.size() + " parts" + tail();
        if (reason == null || reason.isBlank()) return new SummaryResponse(summary, List.of());
        return new SummaryResponse(summary, List.of(), goalGaps);
    }

    @Override
    public ExplanationResponse explain(String filePath, String diffContent, String reason, UserProfile profile) {
        explainCalls++;
        reportTokens("explain");
        lastExplainReason = reason;
        lastExplainProfile = profile;
        fail();
        return new ExplanationResponse("Explains " + filePath,
                List.of(new ExplanationResponse.Step("First line" + tail(), 1, 2)),
                List.of(new ExplanationResponse.Concept("logging", "printing text")),
                explainTestable ? List.of("Point one", "Point two") : List.of(),
                explainTestable, explainIssue);
    }

    @Override
    public GradeResponse grade(String diffContent, List<String> keyPoints, String answer) {
        gradeCalls++;
        reportTokens("grade");
        lastGradeDiff = diffContent;
        lastGradeKeyPoints = keyPoints;
        lastGradeAnswer = answer;
        fail();
        String verdict = gradeVerdicts.isEmpty() ? "pass" : gradeVerdicts.poll();
        return new GradeResponse(verdict, List.of("the first point"),
                List.of("Look at the second point" + tail()), "fake-grader");
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
