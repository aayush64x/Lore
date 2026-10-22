package com.lore.cost;

import com.lore.session.Session;
import com.lore.session.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** What the page gets, with the Anthropic provider (the only one that shows a cost). */
@SpringBootTest(properties = "lore.ai.provider=anthropic")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CostEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired SessionRepository sessionRepository;
    @Autowired AiCallRepository aiCallRepository;

    private Session session() {
        Session s = new Session(UUID.randomUUID(), "/tmp/cost-test-" + UUID.randomUUID(), "refs/lore/sessions/x");
        s.setStatus(Session.SessionStatus.COMPLETE);
        s.setStoppedAt(Instant.now());
        return sessionRepository.save(s);
    }

    @Test
    void the_session_and_the_cost_endpoint_show_the_estimate_and_its_parts() throws Exception {
        Session s = session();
        aiCallRepository.save(new AiCall(s.getId(), AiCall.Job.SUMMARY, "claude-haiku-5-5", 20_000, 3_000, 0, 0));
        aiCallRepository.save(new AiCall(s.getId(), AiCall.Job.EXPLAIN, "claude-sonnet-5-5", 4_000, 1_500, 0, 0));

        for (String url : new String[]{"/api/sessions/" + s.getId(), "/api/sessions/" + s.getId() + "/cost"}) {
            mvc.perform(get(url))
                    .andExpect(status().isOk())
                    // summary 20,000 x $0.10 + 3,000 x $0.50 = $0.0035; explain 4,000 x $2 + 1,500 x $10 = $0.023
                    .andExpect(jsonPath("$.cost.dollars").value(0.0265))
                    .andExpect(jsonPath("$.cost.tokens").value(28_500))
                    .andExpect(jsonPath("$.cost.jobs[*].job", contains("summary", "explain", "grading")))
                    .andExpect(jsonPath("$.cost.jobs[0].dollars").value(0.0035))
                    .andExpect(jsonPath("$.cost.jobs[1].dollars").value(0.023))
                    .andExpect(jsonPath("$.cost.jobs[1].calls").value(1))
                    .andExpect(jsonPath("$.cost.jobs[1].tokensIn").value(4_000))
                    .andExpect(jsonPath("$.cost.jobs[1].tokensOut").value(1_500))
                    .andExpect(jsonPath("$.cost.jobs[1].models", contains("claude-sonnet-5-5")))
                    .andExpect(jsonPath("$.cost.jobs[2].calls").value(0));
        }
    }

    @Test
    void an_old_session_with_no_rows_shows_no_cost() throws Exception {
        Session s = session();

        mvc.perform(get("/api/sessions/" + s.getId())).andExpect(jsonPath("$.cost").value(nullValue()));
        mvc.perform(get("/api/sessions/" + s.getId() + "/cost")).andExpect(jsonPath("$.cost").value(nullValue()));
    }
}
