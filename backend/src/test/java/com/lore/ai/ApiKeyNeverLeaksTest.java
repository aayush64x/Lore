package com.lore.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The API key must never appear in anything Lore prints, logs, or puts in an exception.
 * Uses a fake key and local stub servers only.
 */
@ExtendWith(OutputCaptureExtension.class)
class ApiKeyNeverLeaksTest {

    private static final String FAKE_KEY = "sk-ant-test-LEAKCHECK-9f8e7d6c5b4a";

    private static ApiKeyStore storeWithEnvKey(Path home) {
        return new ApiKeyStore(home, name -> ApiKeyStore.ENV_VAR.equals(name) ? FAKE_KEY : null);
    }

    private static String everything(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw + " " + t;
    }

    private static void assertNoKey(String text) {
        assertFalse(text.contains(FAKE_KEY), "API key leaked into: " + text);
        assertFalse(text.contains("LEAKCHECK"), "Part of the API key leaked into: " + text);
    }

    @Test
    void api_errors_do_not_contain_the_key(@TempDir Path home, CapturedOutput output) throws Exception {
        for (int status : List.of(400, 401, 403, 429, 500, 529)) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                // A hostile server that echoes the key back in its error body.
                String key = exchange.getRequestHeaders().getFirst("x-api-key");
                byte[] body = ("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"bad key "
                        + key + "\"}}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("content-type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            try {
                AnthropicAiService ai = new AnthropicAiService(storeWithEnvKey(home), "claude-sonnet-5-5", "claude-haiku-5-5", "claude-haiku-5-5",
                        "http://127.0.0.1:" + server.getAddress().getPort());
                AiUnavailableException e = assertThrows(AiUnavailableException.class,
                        () -> ai.summarize(List.of(), "reason"));
                assertNoKey(everything(e));
                assertNull(e.getCause(), "SDK exceptions (which may echo the request) must not be attached");
                assertNoKey(ai.toString());
            } finally {
                server.stop(0);
            }
        }
        assertNoKey(output.getAll());
    }

    @Test
    void connection_errors_do_not_contain_the_key(@TempDir Path home, CapturedOutput output) throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        AnthropicAiService ai = new AnthropicAiService(storeWithEnvKey(home), "claude-sonnet-5-5", "claude-haiku-5-5", "claude-haiku-5-5",
                "http://127.0.0.1:" + closedPort);
        AiUnavailableException e = assertThrows(AiUnavailableException.class, () -> ai.explain("a.js", "+x", null, UserProfile.DEFAULT));
        assertNoKey(everything(e));
        assertNoKey(output.getAll());
    }

    @Test
    void key_store_never_shows_the_key(@TempDir Path home) throws Exception {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        store.save(FAKE_KEY);
        assertNoKey(store.toString());
        assertEquals(FAKE_KEY, store.find().orElseThrow());
    }

    @Test
    void first_run_prompt_saves_key_privately_without_printing_it(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path home = tmp.resolve(".lore");
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        FakeTerminal terminal = new FakeTerminal(FAKE_KEY);

        new ApiKeyPrompt(store, terminal).run("start");

        assertEquals(1, terminal.asked);
        assertEquals(FAKE_KEY, store.find().orElseThrow());
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(store.configFile())));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(home)));
        assertTrue(terminal.printed().contains("Key saved"));
        assertNoKey(terminal.printed());
        assertNoKey(output.getAll());

        // Second run: key exists, so no prompt.
        new ApiKeyPrompt(store, terminal).run("stop");
        assertEquals(1, terminal.asked);
    }

    @Test
    void saving_keeps_other_settings(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("config.properties"), "anthropic.model=claude-opus-5-5\n");
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        store.save(FAKE_KEY);
        assertEquals("claude-opus-5-5", store.explainModelOverride().orElseThrow());
        assertEquals(FAKE_KEY, store.find().orElseThrow());
    }

    @Test
    void no_prompt_when_not_a_terminal_or_skipped(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);

        FakeTerminal piped = new FakeTerminal(FAKE_KEY);
        piped.interactive = false;
        new ApiKeyPrompt(store, piped).run("stop");
        assertEquals(0, piped.asked);

        FakeTerminal help = new FakeTerminal(FAKE_KEY);
        new ApiKeyPrompt(store, help).run("--help");
        assertEquals(0, help.asked);

    }

    static class FakeTerminal implements ApiKeyPrompt.Terminal {
        final String answer;
        boolean interactive = true;
        int asked;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        FakeTerminal(String answer) { this.answer = answer; }

        @Override public boolean isInteractive() { return interactive; }
        @Override public char[] readHidden(String prompt) { asked++; return answer.toCharArray(); }
        /** Answers for visible questions, in order; Enter ("") once they run out. */
        final java.util.Deque<String> lines = new java.util.ArrayDeque<>();
        int linesAsked;
        @Override public String readLine(String prompt) { linesAsked++; return lines.isEmpty() ? "" : lines.poll(); }
        @Override public PrintStream out() { return new PrintStream(buffer, true, StandardCharsets.UTF_8); }
        String printed() { return buffer.toString(StandardCharsets.UTF_8); }
    }
}
