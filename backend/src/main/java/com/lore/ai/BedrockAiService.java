package com.lore.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "lore.ai.enabled", havingValue = "true")
public class BedrockAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(BedrockAiService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final int MAX_DIFF_SIZE = 100_000;
    private static final int MAX_SINGLE_FILE_SIZE = 30_000;

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
        String diffBlock = buildDiffBlock(changes);
        String systemPrompt = buildSummarySystemPrompt();
        String userMessage = buildSummaryUserMessage(changes, reason, diffBlock);

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

        return parseToolUseResponse(response, changes);
    }

    private String buildSummarySystemPrompt() {
        return """
                You are a code change summarizer for a learning tool called Lore. \
                Your job is to help developers understand what changed in their coding session.

                When given a set of diffs from a coding session, you must call the save_summary tool with:
                1. A "session_summary" - a plain-English summary of the whole session (2-4 sentences). \
                Explain what was accomplished, not just list files. Write for someone who wants to understand \
                what happened, not someone reviewing a PR.
                2. A "change_descriptions" array with a one-line description for each change. \
                Each description should say what the change does in plain English. \
                Use the display_order from the input to match descriptions to changes.""";
    }

    private String buildSummaryUserMessage(List<ChangeInput> changes, String reason, String diffBlock) {
        StringBuilder sb = new StringBuilder();
        if (reason != null && !reason.isBlank()) {
            sb.append("The developer said they were trying to: ").append(reason).append("\n\n");
        }
        sb.append("Here are the changes from this session:\n\n");

        for (int i = 0; i < changes.size(); i++) {
            ChangeInput c = changes.get(i);
            sb.append("--- Change ").append(i).append(": ")
                    .append(c.changeType()).append(" ").append(c.filePath());
            if (c.binary()) {
                sb.append(" [binary file]");
            }
            sb.append(" ---\n");
        }

        sb.append("\n").append(diffBlock);
        return sb.toString();
    }

    private String buildDiffBlock(List<ChangeInput> changes) {
        StringBuilder sb = new StringBuilder();
        int totalSize = 0;

        for (ChangeInput c : changes) {
            if (c.binary() || c.diffContent() == null) continue;

            if (c.diffContent().length() > MAX_SINGLE_FILE_SIZE) {
                sb.append("--- ").append(c.filePath()).append(" [too large to summarize] ---\n");
                continue;
            }

            if (totalSize + c.diffContent().length() > MAX_DIFF_SIZE) {
                sb.append("--- [remaining changes omitted due to size] ---\n");
                break;
            }

            sb.append(c.diffContent()).append("\n");
            totalSize += c.diffContent().length();
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private SummaryResponse parseToolUseResponse(ConverseResponse response, List<ChangeInput> changes) {
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
                            int order = rd.get("display_order") instanceof Number n ? n.intValue() : 0;
                            String filePath = (String) rd.getOrDefault("file_path",
                                    order < changes.size() ? changes.get(order).filePath() : "unknown");
                            String desc = (String) rd.getOrDefault("description", "No description available");
                            descriptions.add(new SummaryResponse.ChangeDescription(filePath, order, desc));
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
