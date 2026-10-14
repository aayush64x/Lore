package com.lore.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "lore.ai.provider", havingValue = "bedrock")
public class BedrockAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(BedrockAiService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final BedrockRuntimeClient client;
    private final String modelId;

    public BedrockAiService(@Value("${lore.aws.region}") String region,
                            @Value("${lore.aws.model-id}") String modelId) {
        this.modelId = modelId;
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
        String systemPrompt = AiPrompts.summarySystemPrompt();
        String userMessage = AiPrompts.summaryUserMessage(changes, reason);

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("save_summary")
                .description("Save the session summary and per-change descriptions")
                .inputSchema(ToolInputSchema.builder()
                        .json(summaryToolSchema())
                        .build())
                .build();

        ConverseResponse response = client.converse(ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(userMessage))
                        .build())
                .toolConfig(ToolConfiguration.builder()
                        .tools(Tool.builder().toolSpec(toolSpec).build())
                        .toolChoice(ToolChoice.fromAny(AnyToolChoice.builder().build()))
                        .build())
                .build());

        return parseToolUseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private SummaryResponse parseToolUseResponse(ConverseResponse response) {
        for (ContentBlock block : response.output().message().content()) {
            if (block.type() == ContentBlock.Type.TOOL_USE) {
                try {
                    Map<String, Object> input = mapper.readValue(
                            block.toolUse().input().toString(),
                            new TypeReference<>() {});

                    String sessionSummary = (String) input.get("session_summary");

                    List<Map<String, Object>> rawDescriptions =
                            (List<Map<String, Object>>) input.get("change_descriptions");

                    List<SummaryResponse.ChangeDescription> descriptions = new ArrayList<>();
                    if (rawDescriptions != null) {
                        for (Map<String, Object> rd : rawDescriptions) {
                            // Skip entries without a display_order rather than defaulting to 0,
                            // which would attach them to whichever change happens to be first.
                            if (!(rd.get("display_order") instanceof Number n)) continue;
                            descriptions.add(new SummaryResponse.ChangeDescription(
                                    (String) rd.get("file_path"), n.intValue(), (String) rd.get("description")));
                        }
                    }

                    return new SummaryResponse(sessionSummary, descriptions);
                } catch (Exception e) {
                    log.error("Failed to parse AI summary response", e);
                    throw new RuntimeException("Failed to parse AI summary response", e);
                }
            }
        }

        throw new RuntimeException("No tool use block in AI response");
    }

    @Override
    public ExplanationResponse explain(String filePath, String diffContent) {
        String systemPrompt = AiPrompts.explainSystemPrompt();
        String userMessage = AiPrompts.explainUserMessage(filePath, diffContent);

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("save_explanation")
                .description("Save the step-by-step explanation of a code change")
                .inputSchema(ToolInputSchema.builder()
                        .json(explanationToolSchema())
                        .build())
                .build();

        ConverseResponse response = client.converse(ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(userMessage))
                        .build())
                .toolConfig(ToolConfiguration.builder()
                        .tools(Tool.builder().toolSpec(toolSpec).build())
                        .toolChoice(ToolChoice.fromAny(AnyToolChoice.builder().build()))
                        .build())
                .build());

        return parseExplainResponse(response);
    }

    @SuppressWarnings("unchecked")
    private ExplanationResponse parseExplainResponse(ConverseResponse response) {
        for (ContentBlock block : response.output().message().content()) {
            if (block.type() == ContentBlock.Type.TOOL_USE) {
                try {
                    Map<String, Object> input = mapper.readValue(
                            block.toolUse().input().toString(),
                            new TypeReference<>() {});

                    String overview = (String) input.get("overview");

                    List<Map<String, Object>> rawSteps =
                            (List<Map<String, Object>>) input.get("steps");
                    List<ExplanationResponse.Step> steps = new ArrayList<>();
                    if (rawSteps != null) {
                        for (Map<String, Object> rs : rawSteps) {
                            steps.add(new ExplanationResponse.Step(
                                    (String) rs.get("text"),
                                    rs.get("diff_line_start") instanceof Number n ? n.intValue() : 0,
                                    rs.get("diff_line_end") instanceof Number n ? n.intValue() : 0));
                        }
                    }

                    List<Map<String, Object>> rawConcepts =
                            (List<Map<String, Object>>) input.get("concepts");
                    List<ExplanationResponse.Concept> concepts = new ArrayList<>();
                    if (rawConcepts != null) {
                        for (Map<String, Object> rc : rawConcepts) {
                            concepts.add(new ExplanationResponse.Concept(
                                    (String) rc.get("name"),
                                    (String) rc.get("meaning")));
                        }
                    }

                    return new ExplanationResponse(overview, steps, concepts);
                } catch (Exception e) {
                    log.error("Failed to parse AI explanation response", e);
                    throw new RuntimeException("Failed to parse AI explanation response", e);
                }
            }
        }
        throw new RuntimeException("No tool use block in AI response");
    }

    private software.amazon.awssdk.core.document.Document explanationToolSchema() {
        return software.amazon.awssdk.core.document.Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", software.amazon.awssdk.core.document.Document.mapBuilder()
                        .putDocument("overview", software.amazon.awssdk.core.document.Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "One sentence saying what this change does overall")
                                .build())
                        .putDocument("steps", software.amazon.awssdk.core.document.Document.mapBuilder()
                                .putString("type", "array")
                                .putDocument("items", software.amazon.awssdk.core.document.Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                .putDocument("text", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                        .putString("type", "string")
                                                        .putString("description", "Plain-English explanation of what these lines do")
                                                        .build())
                                                .putDocument("diff_line_start", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                        .putString("type", "integer")
                                                        .putString("description", "First numbered diff line this step covers")
                                                        .build())
                                                .putDocument("diff_line_end", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                        .putString("type", "integer")
                                                        .putString("description", "Last numbered diff line this step covers")
                                                        .build())
                                                .build())
                                        .putList("required", List.of(
                                                software.amazon.awssdk.core.document.Document.fromString("text"),
                                                software.amazon.awssdk.core.document.Document.fromString("diff_line_start"),
                                                software.amazon.awssdk.core.document.Document.fromString("diff_line_end")))
                                        .build())
                                .build())
                        .putDocument("concepts", software.amazon.awssdk.core.document.Document.mapBuilder()
                                .putString("type", "array")
                                .putDocument("items", software.amazon.awssdk.core.document.Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                .putDocument("name", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                        .putString("type", "string")
                                                        .putString("description", "The concept name")
                                                        .build())
                                                .putDocument("meaning", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                        .putString("type", "string")
                                                        .putString("description", "One-sentence explanation a beginner would understand")
                                                        .build())
                                                .build())
                                        .putList("required", List.of(
                                                software.amazon.awssdk.core.document.Document.fromString("name"),
                                                software.amazon.awssdk.core.document.Document.fromString("meaning")))
                                        .build())
                                .build())
                        .build())
                .putList("required", List.of(
                        software.amazon.awssdk.core.document.Document.fromString("overview"),
                        software.amazon.awssdk.core.document.Document.fromString("steps"),
                        software.amazon.awssdk.core.document.Document.fromString("concepts")))
                .build();
    }

    private software.amazon.awssdk.core.document.Document summaryToolSchema() {
        return software.amazon.awssdk.core.document.Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", software.amazon.awssdk.core.document.Document.mapBuilder()
                        .putDocument("session_summary", software.amazon.awssdk.core.document.Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "A plain-English summary of the whole session (2-4 sentences)")
                                .build())
                        .putDocument("change_descriptions", software.amazon.awssdk.core.document.Document.mapBuilder()
                                .putString("type", "array")
                                .putDocument("items", software.amazon.awssdk.core.document.Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                .putDocument("display_order", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                        .putString("type", "integer")
                                                        .putString("description", "The display_order of the change this description is for")
                                                        .build())
                                                .putDocument("file_path", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                        .putString("type", "string")
                                                        .putString("description", "The file path of the change")
                                                        .build())
                                                .putDocument("description", software.amazon.awssdk.core.document.Document.mapBuilder()
                                                        .putString("type", "string")
                                                        .putString("description", "A one-line plain-English description of what this change does")
                                                        .build())
                                                .build())
                                        .putList("required", List.of(
                                                software.amazon.awssdk.core.document.Document.fromString("display_order"),
                                                software.amazon.awssdk.core.document.Document.fromString("file_path"),
                                                software.amazon.awssdk.core.document.Document.fromString("description")))
                                        .build())
                                .build())
                        .build())
                .putList("required", List.of(
                        software.amazon.awssdk.core.document.Document.fromString("session_summary"),
                        software.amazon.awssdk.core.document.Document.fromString("change_descriptions")))
                .build();
    }
}
