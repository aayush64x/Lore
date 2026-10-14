package com.lore.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.lore.ai.dto.ExplanationResponse;
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

    private final ApiKeyStore keyStore;
    /** Default from lore.anthropic.model; anthropic.model in config.properties overrides it. */
    private final String defaultModel;
    private final String baseUrl;

    private AnthropicClient client;
    private String clientKey;

    @Autowired
    public AnthropicAiService(ApiKeyStore keyStore, @Value("${lore.anthropic.model}") String defaultModel) {
        this(keyStore, defaultModel, API_BASE_URL);
    }

    /** Tests only: points the client at a local stub server instead of api.anthropic.com. */
    AnthropicAiService(ApiKeyStore keyStore, String defaultModel, String baseUrl) {
        this.keyStore = keyStore;
        this.defaultModel = defaultModel;
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

    record ExplanationOutput(
            @JsonPropertyDescription("One sentence saying what this change does overall")
            String overview,
            List<StepOutput> steps,
            List<ConceptOutput> concepts) {}

    record StepOutput(
            @JsonPropertyDescription("Plain-English explanation of what these lines do and why")
            String text,
            @JsonProperty("diff_line_start") int diffLineStart,
            @JsonProperty("diff_line_end") int diffLineEnd) {}

    record ConceptOutput(
            String name,
            @JsonPropertyDescription("One-sentence explanation a beginner would understand")
            String meaning) {}

    @Override
    public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
        SummaryOutput out = call(SummaryOutput.class,
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

    @Override
    public ExplanationResponse explain(String filePath, String diffContent) {
        ExplanationOutput out = call(ExplanationOutput.class,
                AiPrompts.explainSystemPrompt(),
                AiPrompts.explainUserMessage(filePath, diffContent));

        List<ExplanationResponse.Step> steps = out.steps() == null ? List.of()
                : out.steps().stream().filter(Objects::nonNull)
                        .map(s -> new ExplanationResponse.Step(s.text(), s.diffLineStart(), s.diffLineEnd()))
                        .toList();
        List<ExplanationResponse.Concept> concepts = out.concepts() == null ? List.of()
                : out.concepts().stream().filter(Objects::nonNull)
                        .map(c -> new ExplanationResponse.Concept(c.name(), c.meaning()))
                        .toList();
        return new ExplanationResponse(out.overview(), steps, concepts);
    }

    private <T> T call(Class<T> outputType, String system, String userMessage) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(keyStore.modelOverride().orElse(defaultModel))
                .maxTokens(MAX_TOKENS)
                .system(system)
                .outputConfig(outputType)
                .addUserMessage(userMessage)
                .build();

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
                "No Anthropic API key. Set " + ApiKeyStore.ENV_VAR + " or add anthropic.api.key to "
                        + keyStore.configFile() + "."));
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
        return "AnthropicAiService[defaultModel=" + defaultModel + "]";
    }
}
