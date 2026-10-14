package com.lore.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.dto.SummaryResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Runs AnthropicAiService against a local stub server; nothing goes to the real API. */
class AnthropicAiServiceTest {

    private static final String FAKE_KEY = "sk-ant-test-FAKEKEY-0123456789";
    private static final ObjectMapper mapper = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> seenKey = new AtomicReference<>();
    private final AtomicReference<JsonNode> seenBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            seenKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            seenBody.set(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private AnthropicAiService service(Path home, String envKey) {
        ApiKeyStore store = new ApiKeyStore(home, name -> ApiKeyStore.ENV_VAR.equals(name) ? envKey : null);
        return new AnthropicAiService(store, "claude-opus-5-5",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static String message(String text) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "id", "msg_test", "type", "message", "role", "assistant", "model", "claude-opus-5-5",
                "content", List.of(Map.of("type", "text", "text", text)),
                "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 10, "output_tokens", 10)));
    }

    @Test
    void summarize_sends_structured_request_and_parses_answer(@TempDir Path home) throws Exception {
        responseBody = message("""
                {"session_summary": "Added an app.", "change_descriptions": [
                  {"display_order": 1, "file_path": "app.js", "description": "Logs hi."},
                  {"display_order": 0, "file_path": ".gitignore", "description": "Ignores node_modules."}]}""");

        SummaryResponse r = service(home, FAKE_KEY).summarize(List.of(
                new AiService.ChangeInput(0, ".gitignore", "ADDED", "+node_modules/\n", false),
                new AiService.ChangeInput(1, "app.js", "ADDED", "+console.log('hi');\n", false)), "add app");

        assertEquals("Added an app.", r.sessionSummary());
        assertEquals(new SummaryResponse.ChangeDescription("app.js", 1, "Logs hi."), r.changeDescriptions().get(0));
        assertEquals(new SummaryResponse.ChangeDescription(".gitignore", 0, "Ignores node_modules."), r.changeDescriptions().get(1));

        JsonNode body = seenBody.get();
        assertEquals(FAKE_KEY, seenKey.get());
        assertEquals("claude-opus-5-5", body.get("model").asText());
        assertEquals("json_schema", body.at("/output_config/format/type").asText());
        assertTrue(body.at("/output_config/format/schema/properties").has("change_descriptions"), body.toString());
    }

    @Test
    void falls_back_to_config_file_when_env_var_missing(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("config.properties"), "anthropic.api.key=" + FAKE_KEY + "\n");
        responseBody = message("{\"session_summary\": \"ok\", \"change_descriptions\": []}");

        service(home, null).summarize(List.of(), null);

        assertEquals(FAKE_KEY, seenKey.get());
    }

    @Test
    void env_var_wins_over_config_file(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("config.properties"), "anthropic.api.key=sk-ant-from-file\n");
        ApiKeyStore store = new ApiKeyStore(home, name -> FAKE_KEY);
        assertEquals(FAKE_KEY, store.find().orElseThrow());
    }

    @Test
    void no_key_means_ai_unavailable(@TempDir Path home) {
        AiUnavailableException e = assertThrows(AiUnavailableException.class,
                () -> service(home, null).summarize(List.of(), null));
        assertTrue(e.getMessage().contains("ANTHROPIC_API_KEY"));
    }

    @Test
    void api_error_becomes_ai_unavailable(@TempDir Path home) {
        status = 401;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";

        AiUnavailableException e = assertThrows(AiUnavailableException.class,
                () -> service(home, FAKE_KEY).explain("app.js", "+x\n"));
        assertTrue(e.getMessage().contains("401"), e.getMessage());
        assertFalse(e.getMessage().contains(FAKE_KEY));
    }

    @Test
    void production_base_url_is_anthropic() {
        assertEquals("https://api.anthropic.com", AnthropicAiService.API_BASE_URL);
    }
}
