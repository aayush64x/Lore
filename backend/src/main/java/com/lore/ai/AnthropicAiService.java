package com.lore.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.GradeResponse;
import com.lore.ai.dto.SummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Default AI provider: Claude through the Anthropic API. The key comes from
 * {@link ApiKeyStore} on every call, so a key saved after startup is picked up.
 * The key is only ever sent to api.anthropic.com, and never appears in log
 * lines or exception messages produced here.
 */
@Service
@ConditionalOnProperty(name = "lore.ai.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicAiService implements AiService {

    static final String API_BASE_URL = "https://api.anthropic.com";
    private static final long MAX_TOKENS = 16_000L;
    /** A grade is a verdict and a few short lines. */
    private static final long GRADE_MAX_TOKENS = 400L;

    private final ApiKeyStore keyStore;
    /** Defaults from lore.anthropic.*-model; the matching anthropic.*-model lines in config.properties override them. */
    private final String defaultExplainModel;
    private final String defaultSummaryModel;
    private final String defaultGradingModel;
    private final String baseUrl;

    private AnthropicClient client;
    private String clientKey;

    @Autowired
    public AnthropicAiService(ApiKeyStore keyStore,
                              @Value("${lore.anthropic.explain-model}") String defaultExplainModel,
                              @Value("${lore.anthropic.summary-model}") String defaultSummaryModel,
                              @Value("${lore.anthropic.grading-model}") String defaultGradingModel) {
        this(keyStore, defaultExplainModel, defaultSummaryModel, defaultGradingModel, API_BASE_URL);
    }

    /** Tests only: points the client at a local stub server instead of api.anthropic.com. */
    AnthropicAiService(ApiKeyStore keyStore, String defaultExplainModel, String defaultSummaryModel,
                       String defaultGradingModel, String baseUrl) {
        this.keyStore = keyStore;
        this.defaultExplainModel = defaultExplainModel;
        this.defaultSummaryModel = defaultSummaryModel;
        this.defaultGradingModel = defaultGradingModel;
        this.baseUrl = baseUrl;
    }

    // --- Structured output shapes (JSON schema is derived from these records) ---

    record SummaryOutput(
            @JsonProperty("session_summary")
            @JsonPropertyDescription("Plain-English summary of the whole session, 2-4 sentences")
            String sessionSummary,
            @JsonProperty("change_descriptions")
            @JsonPropertyDescription("Exactly one entry per change")
            List<ChangeDescriptionOutput> changeDescriptions) {}

    record ChangeDescriptionOutput(
            @JsonProperty("display_order")
            @JsonPropertyDescription("display_order copied from the change's header")
            int displayOrder,
            @JsonProperty("file_path")
            @JsonPropertyDescription("file path copied from the change's header")
            String filePath,
            @JsonPropertyDescription("One line saying what this change does")
            String description) {}

    /** The schema lists these names as they are, so the prompt uses them too. */
    enum GoalMatch { YES, PARTLY, NO }

    /** The summary plus the goal check, asked for only when the developer gave a goal. */
    record GoalSummaryOutput(
            @JsonProperty("session_summary")
            @JsonPropertyDescription("Plain-English summary of the whole session, 2-4 sentences")
            String sessionSummary,
            @JsonProperty("change_descriptions")
            @JsonPropertyDescription("Exactly one entry per change")
            List<GoalChangeOutput> changeDescriptions,
            @JsonProperty("goal_parts_not_done")
            @JsonPropertyDescription("Parts of the stated goal that no change does; empty if all were done")
            List<String> goalPartsNotDone) {}

    record GoalChangeOutput(
            @JsonProperty("display_order")
            @JsonPropertyDescription("display_order copied from the change's header")
            int displayOrder,
            @JsonProperty("file_path")
            @JsonPropertyDescription("file path copied from the change's header")
            String filePath,
            @JsonPropertyDescription("One line saying what this change does")
            String description,
            @JsonProperty("matches_goal")
            @JsonPropertyDescription("Whether this change serves the stated goal")
            GoalMatch matchesGoal,
            @JsonProperty("goal_reason")
            @JsonPropertyDescription("One short line saying why")
            String goalReason) {}

    /** One summary joined from the summaries of a big session's parts. */
    record CombinedOutput(
            @JsonProperty("session_summary")
            @JsonPropertyDescription("Plain-English summary of the whole session, 2-4 sentences")
            String sessionSummary) {}

    record CombinedGoalOutput(
            @JsonProperty("session_summary")
            @JsonPropertyDescription("Plain-English summary of the whole session, 2-4 sentences")
            String sessionSummary,
            @JsonProperty("goal_parts_not_done")
            @JsonPropertyDescription("Parts of the stated goal that the whole session doesn't do; empty if all were done")
            List<String> goalPartsNotDone) {}

    record ExplanationOutput(
            @JsonPropertyDescription("One sentence saying what this change does overall")
            String overview,
            List<StepOutput> steps,
            List<ConceptOutput> concepts,
            @JsonProperty("key_points")
            @JsonPropertyDescription("2-3 short things a person must say to show they understand this change")
            List<String> keyPoints,
            @JsonPropertyDescription("False only if there is nothing to understand: imports, formatting, renames, comments")
            boolean testable,
            @JsonProperty("possible_issue")
            @JsonPropertyDescription("Empty, or exactly one real edge case or bug in this change. Never invent one.")
            List<IssueOutput> possibleIssue) {}

    record IssueOutput(
            @JsonProperty("diff_line")
            @JsonPropertyDescription("The numbered diff line the problem is on")
            int diffLine,
            @JsonPropertyDescription("What input breaks it and what happens, in 1-2 sentences")
            String text) {}

    record StepOutput(
            @JsonPropertyDescription("Plain-English explanation of what these lines do and why")
            String text,
            @JsonProperty("diff_line_start") int diffLineStart,
            @JsonProperty("diff_line_end") int diffLineEnd) {}

    record ConceptOutput(
            String name,
            @JsonPropertyDescription("One-sentence explanation a beginner would understand")
            String meaning) {}

    enum Verdict { PASS, PARTLY, FAIL }

    record GradeOutput(
            @JsonPropertyDescription("PASS if every key point is covered in meaning, PARTLY if some are, FAIL if none")
            Verdict verdict,
            @JsonProperty("got_right")
            @JsonPropertyDescription("Each key point the student covered, in a few words")
            List<String> gotRight,
            @JsonPropertyDescription("One hint per key point not covered; points where to look, never the answer")
            List<String> missing) {}

    @Override
    public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
        if (reason != null && !reason.isBlank()) return summarizeWithGoal(changes, reason);

        SummaryOutput out = summaryCall(SummaryOutput.class,
                AiPrompts.summarySystemPrompt(),
                AiPrompts.summaryUserMessage(changes, reason));

        List<SummaryResponse.ChangeDescription> descriptions = out.changeDescriptions() == null
                ? List.of()
                : out.changeDescriptions().stream()
                        .filter(Objects::nonNull)
                        .map(d -> new SummaryResponse.ChangeDescription(d.filePath(), d.displayOrder(), d.description()))
                        .toList();
        return new SummaryResponse(out.sessionSummary(), descriptions);
    }

    private SummaryResponse summarizeWithGoal(List<ChangeInput> changes, String reason) {
        GoalSummaryOutput out = summaryCall(GoalSummaryOutput.class,
                AiPrompts.summaryWithGoalSystemPrompt(),
                AiPrompts.summaryUserMessage(changes, reason));

        List<SummaryResponse.ChangeDescription> descriptions = out.changeDescriptions() == null
                ? List.of()
                : out.changeDescriptions().stream()
                        .filter(Objects::nonNull)
                        .map(d -> new SummaryResponse.ChangeDescription(d.filePath(), d.displayOrder(), d.description(),
                                d.matchesGoal() == null ? null : d.matchesGoal().name().toLowerCase(),
                                d.goalReason()))
                        .toList();
        List<String> gaps = out.goalPartsNotDone() == null ? List.of()
                : out.goalPartsNotDone().stream().filter(g -> g != null && !g.isBlank()).map(String::strip).toList();
        return new SummaryResponse(out.sessionSummary(), descriptions, gaps);
    }

    @Override
    public SummaryResponse combineSummaries(List<SummaryResponse> parts, String reason) {
        String message = AiPrompts.combineUserMessage(parts, reason);
        if (reason == null || reason.isBlank()) {
            CombinedOutput out = summaryCall(CombinedOutput.class, AiPrompts.combineSystemPrompt(), message);
            return new SummaryResponse(out.sessionSummary(), List.of());
        }
        CombinedGoalOutput out = summaryCall(CombinedGoalOutput.class, AiPrompts.combineWithGoalSystemPrompt(), message);
        return new SummaryResponse(out.sessionSummary(), List.of(), clean(out.goalPartsNotDone()));
    }

    @Override
    public ExplanationResponse explain(String filePath, String diffContent, String reason, UserProfile profile) {
        ExplanationOutput out = explainCall(ExplanationOutput.class,
                AiPrompts.explainSystemPrompt(profile),
                AiPrompts.explainUserMessage(filePath, diffContent, reason));

        List<ExplanationResponse.Step> steps = out.steps() == null ? List.of()
                : out.steps().stream().filter(Objects::nonNull)
                        .map(s -> new ExplanationResponse.Step(s.text(), s.diffLineStart(), s.diffLineEnd()))
                        .toList();
        List<ExplanationResponse.Concept> concepts = out.concepts() == null ? List.of()
                : out.concepts().stream().filter(Objects::nonNull)
                        .map(c -> new ExplanationResponse.Concept(c.name(), c.meaning()))
                        .toList();
        List<String> keyPoints = out.keyPoints() == null ? List.of()
                : out.keyPoints().stream().filter(k -> k != null && !k.isBlank()).map(String::strip).toList();
        ExplanationResponse.Issue issue = out.possibleIssue() == null ? null
                : out.possibleIssue().stream()
                        .filter(i -> i != null && i.text() != null && !i.text().isBlank())
                        .findFirst()
                        .map(i -> new ExplanationResponse.Issue(i.diffLine(), i.text().strip()))
                        .orElse(null);
        return new ExplanationResponse(out.overview(), steps, concepts, keyPoints, out.testable(), issue);
    }

    /**
     * Grades with the cheaper grading model. Haiku only accepts the default temperature, so
     * thinking is off and effort is low instead: short, steady grades that fit in 400 tokens.
     * The instructions are one cached system block (they never change between requests).
     */
    @Override
    public GradeResponse grade(String diffContent, List<String> keyPoints, String answer) {
        String model = keyStore.gradingModelOverride().orElse(defaultGradingModel);
        StructuredMessageCreateParams<GradeOutput> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(GRADE_MAX_TOKENS)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(AiPrompts.gradeSystemPrompt())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .thinking(ThinkingConfigDisabled.builder().build())
                .outputConfig(GradeOutput.class)
                .outputConfig(StructuredOutputConfig.<GradeOutput>builder()
                        .format(GradeOutput.class)
                        .effort(OutputConfig.Effort.LOW)
                        .build())
                .addUserMessage(AiPrompts.gradeUserMessage(diffContent, keyPoints, answer))
                .build();

        GradeOutput out = send(params);
        if (out.verdict() == null) throw new AiUnavailableException("Claude returned no grade.");
        return new GradeResponse(out.verdict().name().toLowerCase(),
                clean(out.gotRight()), clean(out.missing()), model);
    }

    private static List<String> clean(List<String> items) {
        return items == null ? List.of()
                : items.stream().filter(t -> t != null && !t.isBlank()).map(String::strip).toList();
    }

    /** Explain: the teaching job, on the explain model with its default thinking. */
    private <T> T explainCall(Class<T> outputType, String system, String userMessage) {
        return send(MessageCreateParams.builder()
                .model(keyStore.explainModelOverride().orElse(defaultExplainModel))
                .maxTokens(MAX_TOKENS)
                .system(system)
                .outputConfig(outputType)
                .addUserMessage(userMessage)
                .build());
    }

    /**
     * Summaries: the cheap summary model with thinking off. One-line answers don't need it,
     * and thinking tokens would come out of the same budget as the answer.
     */
    private <T> T summaryCall(Class<T> outputType, String system, String userMessage) {
        return send(MessageCreateParams.builder()
                .model(keyStore.summaryModelOverride().orElse(defaultSummaryModel))
                .maxTokens(MAX_TOKENS)
                .system(system)
                .thinking(ThinkingConfigDisabled.builder().build())
                .outputConfig(outputType)
                .addUserMessage(userMessage)
                .build());
    }

    /** Sends the request and reads the structured answer; every failure becomes AiUnavailableException. */
    private <T> T send(StructuredMessageCreateParams<T> params) {
        StructuredMessage<T> response;
        try {
            response = client().messages().create(params);
        } catch (AnthropicServiceException e) {
            // Status and error type only: SDK messages can echo request details.
            String type = e.errorType().map(Object::toString).orElse("unknown error");
            throw new AiUnavailableException(
                    "Anthropic API request failed (HTTP " + e.statusCode() + ", " + type + ")."
                            + (e.statusCode() == 401 ? " Check your API key." : ""));
        } catch (AnthropicException e) {
            throw new AiUnavailableException("Could not reach the Anthropic API ("
                    + e.getClass().getSimpleName() + ").");
        }

        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new AiUnavailableException("Claude declined to answer this request.");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new AiUnavailableException("Claude's answer was cut off (too long).");
        }

        try {
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new AiUnavailableException("Claude returned no answer."));
        } catch (AiUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Could not read Claude's answer ("
                    + e.getClass().getSimpleName() + ").");
        }
    }

    private synchronized AnthropicClient client() {
        String key = keyStore.find().orElseThrow(() -> new AiUnavailableException(
                "No Anthropic API key. Run lore in a terminal to enter one, or set "
                        + ApiKeyStore.ENV_VAR + "."));
        if (client == null || !key.equals(clientKey)) {
            if (client != null) client.close();
            client = AnthropicOkHttpClient.builder()
                    .apiKey(key)
                    .baseUrl(baseUrl)
                    .timeout(Duration.ofMinutes(5))
                    .build();
            clientKey = key;
        }
        return client;
    }

    @Override
    public String toString() {
        return "AnthropicAiService[defaultExplainModel=" + defaultExplainModel + "]";
    }
}
