package com.lore.cost;

import com.lore.ai.ApiKeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionCostTest {

    private final UUID session = UUID.randomUUID();
    private final AiCallRepository repository = mock(AiCallRepository.class);

    private SessionCost cost(Path home, String provider, AiCall... calls) {
        when(repository.findBySessionId(session)).thenReturn(List.of(calls));
        return new SessionCost(repository, new Prices(new ApiKeyStore(home)), provider);
    }

    private AiCall call(AiCall.Job job, String model, long in, long out, long cacheRead, long cacheWrite) {
        return new AiCall(session, job, model, in, out, cacheRead, cacheWrite);
    }

    private static SessionCost.JobCost job(SessionCost.Cost c, String name) {
        return c.jobs().stream().filter(j -> j.job().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void cost_of_known_models_uses_each_kind_of_token() {
        SessionCost.Cost c = cost(Path.of("/nonexistent"), "anthropic",
                // 10,000 x $2 + 2,000 x $10 + 1,000 x $0.10 + 500 x $2.50, per million = $0.041350
                call(AiCall.Job.EXPLAIN, "claude-sonnet-5-5", 10_000, 2_000, 1_000, 500),
                // 1,000,000 x $0.10 = $0.10
                call(AiCall.Job.SUMMARY, "claude-haiku-5-5", 1_000_000, 0, 0, 0),
                // 2,000 x $0.10 + 100 x $0.50 + 3,000 x $0.01 + 0 = $0.000280
                call(AiCall.Job.GRADING, "claude-haiku-5-5", 2_000, 100, 3_000, 0)).of(session).orElseThrow();

        assertEquals(new BigDecimal("0.041350"), job(c, "explain").dollars());
        assertEquals(new BigDecimal("0.100000"), job(c, "summary").dollars());
        assertEquals(new BigDecimal("0.000280"), job(c, "grading").dollars());
        assertEquals(new BigDecimal("0.141630"), c.dollars());
        assertEquals(List.of("summary", "explain", "grading"), c.jobs().stream().map(SessionCost.JobCost::job).toList());
        assertEquals(1, job(c, "explain").calls());
    }

    @Test
    void each_job_has_tokens_in_and_out_and_its_models() {
        SessionCost.Cost c = cost(Path.of("/nonexistent"), "anthropic",
                call(AiCall.Job.EXPLAIN, "claude-sonnet-5-5", 10_000, 2_000, 1_000, 500),
                call(AiCall.Job.EXPLAIN, "claude-opus-5-5", 3_000, 700, 0, 0),
                call(AiCall.Job.EXPLAIN, "claude-sonnet-5-5", 1, 1, 0, 0)).of(session).orElseThrow();

        SessionCost.JobCost explain = job(c, "explain");
        assertEquals(3, explain.calls());
        assertEquals(10_000 + 1_000 + 500 + 3_000 + 1, explain.tokensIn(), "cache reads and writes are tokens in");
        assertEquals(2_000 + 700 + 1, explain.tokensOut());
        assertEquals(List.of("claude-opus-5-5", "claude-sonnet-5-5"), explain.models());
        assertEquals(List.of(), job(c, "grading").models());
    }

    @Test
    void a_model_with_no_price_shows_tokens_not_dollars() {
        SessionCost.Cost c = cost(Path.of("/nonexistent"), "anthropic",
                call(AiCall.Job.SUMMARY, "claude-haiku-5-5", 1_000, 100, 0, 0),
                call(AiCall.Job.EXPLAIN, "some-new-model", 5_000, 500, 50, 5)).of(session).orElseThrow();

        assertNull(c.dollars(), "never a guessed total");
        assertEquals(1_100 + 5_555, c.tokens());
        assertNull(job(c, "explain").dollars());
        assertEquals(5_555, job(c, "explain").tokens());
        assertNotNull(job(c, "summary").dollars(), "a priced job still has its dollars");
    }

    @Test
    void a_job_with_no_calls_is_listed_with_nothing() {
        SessionCost.Cost c = cost(Path.of("/nonexistent"), "anthropic",
                call(AiCall.Job.SUMMARY, "claude-haiku-5-5", 1_000, 100, 0, 0)).of(session).orElseThrow();

        assertEquals(0, job(c, "explain").calls());
        assertEquals(0, job(c, "explain").tokens());
        assertEquals(BigDecimal.ZERO.setScale(6), job(c, "explain").dollars());
    }

    @Test
    void an_old_session_with_no_rows_has_no_cost() {
        assertTrue(cost(Path.of("/nonexistent"), "anthropic").of(session).isEmpty());
    }

    @Test
    void bedrock_and_none_never_show_a_cost() {
        AiCall row = call(AiCall.Job.SUMMARY, "claude-haiku-5-5", 1_000, 100, 0, 0);
        assertTrue(cost(Path.of("/nonexistent"), "bedrock", row).of(session).isEmpty());
        assertTrue(cost(Path.of("/nonexistent"), "none", row).of(session).isEmpty());
    }

    @Test
    void a_price_can_be_changed_in_config_properties(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("config.properties"), "price.claude-haiku-5-5.input=1.00\n");
        SessionCost.Cost c = cost(home, "anthropic",
                call(AiCall.Job.SUMMARY, "claude-haiku-5-5", 1_000_000, 0, 0, 0)).of(session).orElseThrow();

        assertEquals(new BigDecimal("1.000000"), c.dollars());
    }

    @Test
    void a_new_model_is_priced_only_with_all_four_lines(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("config.properties"),
                "price.claude-next.input=1\nprice.claude-next.output=2\nprice.claude-next.cache-read=0.1\n");
        Prices prices = new Prices(new ApiKeyStore(home));
        assertTrue(prices.forModel("claude-next").isEmpty(), "three of four lines is not a price");

        Files.writeString(home.resolve("config.properties"),
                "price.claude-next.input=1\nprice.claude-next.output=2\nprice.claude-next.cache-read=0.1\n"
                        + "price.claude-next.cache-write=1.25\n");
        assertEquals(new BigDecimal("2"), prices.forModel("claude-next").orElseThrow().output());
    }

    @Test
    void a_price_line_that_isnt_a_number_means_no_price(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("config.properties"), "price.claude-sonnet-5-5.output=ten\n");
        assertTrue(new Prices(new ApiKeyStore(home)).forModel("claude-sonnet-5-5").isEmpty());
    }

    @Test
    void every_default_model_in_application_yml_has_a_price() throws Exception {
        String yml;
        try (InputStream in = SessionCostTest.class.getResourceAsStream("/application.yml")) {
            yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> models = yml.lines().map(String::strip)
                .filter(l -> l.matches("(explain|summary|grading)-model: .+"))
                .map(l -> l.substring(l.indexOf(':') + 1).strip())
                .toList();

        assertEquals(3, models.size(), "explain, summary and grading models: " + models);
        Prices prices = new Prices(new ApiKeyStore(Path.of("/nonexistent")));
        for (String model : models) {
            assertTrue(Prices.DEFAULTS.containsKey(model), "no default price for " + model);
            assertTrue(prices.forModel(model).isPresent(), model);
        }
    }
}
