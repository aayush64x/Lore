package com.lore.review;

import com.lore.ai.ApiKeyStore;
import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.concept.ChangeConcept;
import com.lore.concept.ChangeConceptRepository;
import com.lore.cost.AiCall;
import com.lore.cost.AiCallRecorder;
import com.lore.cost.AiCallRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import com.lore.testutil.FakeAi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The review queue API: what's due, answering a review, and the demo clock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewApiTest {

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi fakeAi(AiCallRecorder recorder) {
            FakeAi ai = new FakeAi();
            ai.recorder = recorder;
            return ai;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeAi ai;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ExplanationRepository explanationRepository;
    @Autowired ChangeConceptRepository conceptRepository;
    @Autowired ReviewItemRepository reviewRepository;
    @Autowired AiCallRepository aiCallRepository;
    @Autowired LoreClock clock;
    @Autowired ApiKeyStore settings;

    String project;
    Session session;

    @BeforeEach
    void setUp() {
        ai.reset(FakeAi.Mode.WORKING);
        project = "/tmp/review-api-" + UUID.randomUUID();
        session = new Session(UUID.randomUUID(), project, "refs/lore/sessions/x");
        session.setReason("Build the to-do page");
        session.setStatus(Session.SessionStatus.COMPLETE);
        session.setStoppedAt(Instant.now());
        sessionRepository.save(session);
    }

    @AfterEach
    void cleanUp() throws Exception {
        clock.reset();
        Files.deleteIfExists(settings.configFile());
    }

    /** A change marked understood (so it's in the queue, due tomorrow). */
    private Change understood(String path, String description, String... concepts) throws Exception {
        Change c = new Change(UUID.randomUUID(), session, path, Change.ChangeType.ADDED);
        c.setDiffContent("@@ -0,0 +1 @@\n+line");
        c.setDescription(description);
        c.setNewStart(1);
        changeRepository.save(c);
        for (String name : concepts) conceptRepository.save(new ChangeConcept(c, name, false));
        mvc.perform(put("/api/changes/" + c.getId() + "/understood")).andExpect(status().isOk());
        return c;
    }

    private ResultActions answer(Change c, String answer) throws Exception {
        return mvc.perform(post("/api/review/" + c.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"" + answer + "\"}"));
    }

    @Test
    void nothing_is_due_until_a_day_has_passed() throws Exception {
        understood("todo.html", "Adds a to-do list.", "DOM & events");

        mvc.perform(get("/api/review").param("project", project))
                .andExpect(jsonPath("$.dueCount").value(0))
                .andExpect(jsonPath("$.upcomingCount").value(1))
                .andExpect(jsonPath("$.demo.enabled").value(false));
        mvc.perform(get("/api/review/count").param("project", project)).andExpect(jsonPath("$.due").value(0));
    }

    @Test
    void a_due_item_has_its_change_session_and_concepts() throws Exception {
        Change c = understood("todo.html", "Adds a to-do list.", "DOM & events", "Browser storage (localStorage)");
        understood("other.css", "Styles it.");
        clock.pretendDaysPassed(1);

        mvc.perform(get("/api/review").param("project", project))
                .andExpect(jsonPath("$.question").value("In your own words, what does this change do, and why?"))
                .andExpect(jsonPath("$.dueCount").value(2))
                .andExpect(jsonPath("$.due[?(@.changeId == '" + c.getId() + "')].filePath", contains("todo.html")))
                .andExpect(jsonPath("$.due[?(@.changeId == '" + c.getId() + "')].sessionReason", contains("Build the to-do page")))
                .andExpect(jsonPath("$.due[?(@.changeId == '" + c.getId() + "')].description", contains("Adds a to-do list.")))
                .andExpect(jsonPath("$.due[?(@.changeId == '" + c.getId() + "')].gapDays", contains(1)))
                .andExpect(jsonPath("$.due[?(@.changeId == '" + c.getId() + "')].concepts[*].name",
                        contains("Browser storage (localStorage)", "DOM & events")));
        mvc.perform(get("/api/review/count").param("project", project)).andExpect(jsonPath("$.due").value(2));
        // Another project's queue is separate.
        mvc.perform(get("/api/review/count").param("project", project + "-other")).andExpect(jsonPath("$.due").value(0));
    }

    @Test
    void a_pass_moves_it_to_three_days_and_counts_as_grading_cost() throws Exception {
        Change c = understood("todo.html", "Adds a to-do list.");
        clock.pretendDaysPassed(1);
        ai.gradeVerdicts.add("pass");

        answer(c, "It adds a to-do list.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("pass"))
                .andExpect(jsonPath("$.gapDays").value(3))
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.gotRight", hasSize(1)));

        assertEquals(1, reviewRepository.findByChangeId(c.getId()).orElseThrow().getStep());
        mvc.perform(get("/api/review/count").param("project", project)).andExpect(jsonPath("$.due").value(0));
        assertEquals(1, aiCallRepository.findBySessionId(session.getId()).stream()
                .filter(a -> a.getJob() == AiCall.Job.GRADING).count());
    }

    @Test
    void a_fail_goes_back_to_one_day() throws Exception {
        Change c = understood("todo.html", "Adds a to-do list.");
        clock.pretendDaysPassed(1);
        ai.gradeVerdicts.addAll(List.of("pass", "fail"));
        answer(c, "first").andExpect(jsonPath("$.gapDays").value(3));
        clock.pretendDaysPassed(3);

        answer(c, "second")
                .andExpect(jsonPath("$.verdict").value("fail"))
                .andExpect(jsonPath("$.gapDays").value(1));
    }

    @Test
    void graded_against_key_points_or_the_summary_if_never_explained() throws Exception {
        Change explained = understood("a.js", "Adds a.");
        Explanation e = new Explanation(UUID.randomUUID(), explained);
        e.setOverview("x");
        e.setSteps("[]");
        e.setConcepts("[]");
        e.setKeyPoints("[\"point one\",\"point two\"]");
        e.setStatus(Explanation.ExplanationStatus.COMPLETE);
        explanationRepository.save(e);
        Change plain = understood("b.js", "Adds b.");
        clock.pretendDaysPassed(1);

        answer(explained, "x");
        assertEquals(List.of("point one", "point two"), ai.lastGradeKeyPoints);
        answer(plain, "x");
        assertEquals(List.of("Adds b."), ai.lastGradeKeyPoints);
        assertEquals("@@ -0,0 +1 @@\n+line", ai.lastGradeDiff);
    }

    @Test
    void not_due_unknown_and_empty_answers_are_refused() throws Exception {
        Change c = understood("todo.html", "Adds a to-do list.");

        answer(c, "too early").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This review isn't due yet."));
        mvc.perform(post("/api/review/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"x\"}"))
                .andExpect(status().isNotFound());
        clock.pretendDaysPassed(1);
        answer(c, "  ").andExpect(status().isBadRequest());
        assertEquals(0, ai.gradeCalls);
    }

    @Test
    void if_grading_fails_the_review_stays_due() throws Exception {
        Change c = understood("todo.html", "Adds a to-do list.");
        clock.pretendDaysPassed(1);
        ai.reset(FakeAi.Mode.NOT_SET_UP);

        answer(c, "x").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("AI is not set up."));

        assertEquals(0, reviewRepository.findByChangeId(c.getId()).orElseThrow().getStep());
        mvc.perform(get("/api/review/count").param("project", project)).andExpect(jsonPath("$.due").value(1));
    }

    @Test
    void the_demo_clock_only_works_with_the_setting_on() throws Exception {
        understood("todo.html", "Adds a to-do list.");
        mvc.perform(post("/api/demo/days-passed").param("days", "1")).andExpect(status().isNotFound());

        Files.createDirectories(settings.configFile().getParent());
        Files.writeString(settings.configFile(), "demo.time-travel=true\n");
        mvc.perform(post("/api/demo/days-passed").param("days", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.daysAhead").value(1));
        mvc.perform(get("/api/review").param("project", project))
                .andExpect(jsonPath("$.demo.enabled").value(true))
                .andExpect(jsonPath("$.demo.daysAhead").value(1))
                .andExpect(jsonPath("$.dueCount").value(1));
        mvc.perform(post("/api/demo/days-passed").param("days", "0")).andExpect(status().isBadRequest());

        mvc.perform(post("/api/demo/reset")).andExpect(jsonPath("$.daysAhead").value(0));
        mvc.perform(get("/api/review/count").param("project", project)).andExpect(jsonPath("$.due").value(0));
    }
}
