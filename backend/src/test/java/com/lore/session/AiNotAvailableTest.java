package com.lore.session;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.testutil.TestGit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "lore.ai.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiNotAvailableTest {

    @Autowired SessionService sessionService;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ExplanationRepository explanationRepository;
    @Autowired MockMvc mvc;

    @Test
    void summary_is_failed_and_no_placeholder_text_is_saved(@TempDir Path repo) throws Exception {
        TestGit.git(repo, "init");
        sessionService.start(repo);
        TestGit.writeTwoFiles(repo);

        Session stopped = sessionService.stop(repo, "add app").session();

        Session saved = sessionRepository.findById(stopped.getId()).orElseThrow();
        assertEquals(Session.SummaryStatus.FAILED, saved.getSummaryStatus());
        assertNull(saved.getSummary());
        List<Change> changes = changeRepository.findBySessionIdOrderByDisplayOrder(saved.getId());
        assertEquals(2, changes.size());
        changes.forEach(c -> assertNull(c.getDescription(), c.getFilePath()));

        mvc.perform(post("/api/changes/" + changes.get(0).getId() + "/explain"))
                .andExpect(status().isServiceUnavailable());
        Explanation explanation = explanationRepository.findByChangeId(changes.get(0).getId()).orElseThrow();
        assertEquals(Explanation.ExplanationStatus.FAILED, explanation.getStatus());
        assertNull(explanation.getOverview());
    }
}
