package com.lore.api;

import com.lore.ai.AiService;
import com.lore.ai.AiUnavailableException;
import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import com.lore.session.SessionService;
import com.lore.testutil.TestGit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetrySummaryTest {

    /** An AI that fails until working is set to true. */
    static class SwitchableAi implements AiService {
        volatile boolean working = false;

        @Override
        public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
            if (!working) throw new AiUnavailableException("AI is not set up.");
            return new SummaryResponse("Summary for: " + reason, changes.stream()
                    .map(c -> new SummaryResponse.ChangeDescription(c.filePath(), c.displayOrder(), "about " + c.filePath()))
                    .toList());
        }

        @Override
        public ExplanationResponse explain(String filePath, String diffContent, String reason, com.lore.ai.UserProfile profile) {
            throw new UnsupportedOperationException();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary SwitchableAi switchableAi() { return new SwitchableAi(); }
    }

    @Autowired MockMvc mvc;
    @Autowired SwitchableAi ai;
    @Autowired SessionService sessionService;
    @Autowired SessionRepository sessionRepository;

    @Test
    void retry_fails_then_succeeds_then_keeps_good_summary(@TempDir Path repo) throws Exception {
        ai.working = false;
        TestGit.git(repo, "init");
        sessionService.start(repo);
        TestGit.writeTwoFiles(repo);
        UUID id = sessionService.stop(repo, "add app").session().getId();
        String url = "/api/sessions/" + id + "/retry-summary";

        // AI still down: 503, still FAILED.
        mvc.perform(post(url))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI is not set up."))
                .andExpect(jsonPath("$.summaryStatus").value("FAILED"));

        // AI back: 200 with the full session, summary and per-change descriptions.
        ai.working = true;
        mvc.perform(post(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.summary").value("Summary for: add app"))
                .andExpect(jsonPath("$.changes", hasSize(2)))
                .andExpect(jsonPath("$.changes[0].description").value(startsWith("about ")))
                .andExpect(jsonPath("$.changes[1].description").value(startsWith("about ")));

        // AI down again: retry fails, but the good summary is kept.
        ai.working = false;
        mvc.perform(post(url))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.summaryStatus").value("COMPLETE"));
        Session saved = sessionRepository.findById(id).orElseThrow();
        assertEquals("Summary for: add app", saved.getSummary());
    }

    @Test
    void unknown_session_is_404() throws Exception {
        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/retry-summary"))
                .andExpect(status().isNotFound());
    }

    @Test
    void running_session_is_409(@TempDir Path repo) throws Exception {
        TestGit.git(repo, "init");
        UUID id = sessionService.start(repo).getId();
        mvc.perform(post("/api/sessions/" + id + "/retry-summary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }
}
