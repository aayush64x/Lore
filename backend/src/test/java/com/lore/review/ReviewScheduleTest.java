package com.lore.review;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import com.lore.session.SessionService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** When understood changes come back for review: 1, 3, 7, then 14 days. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewScheduleTest {

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi fakeAi() { return new FakeAi(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeAi ai;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ExplanationRepository explanationRepository;
    @Autowired ReviewItemRepository reviewRepository;
    @Autowired ReviewSchedule schedule;
    @Autowired SessionService sessionService;
    @Autowired LoreClock clock;

    Session session;

    @BeforeEach
    void setUp() {
        ai.reset(FakeAi.Mode.WORKING);
        session = new Session(UUID.randomUUID(), "/tmp/review-test-" + UUID.randomUUID(), "refs/lore/sessions/x");
        session.setStatus(Session.SessionStatus.COMPLETE);
        session.setStoppedAt(Instant.now());
        sessionRepository.save(session);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private Change change() {
        Change c = new Change(UUID.randomUUID(), session, "a.js", Change.ChangeType.MODIFIED);
        c.setDiffContent("@@ -1 +1 @@\n-old\n+new");
        return changeRepository.save(c);
    }

    private ReviewItem item(Change c) {
        return reviewRepository.findByChangeId(c.getId()).orElseThrow();
    }

    /** Days from the clock's now to the item's due date, rounded. */
    private long daysUntilDue(ReviewItem item) {
        return Math.round(Duration.between(clock.now(), item.getDueAt()).toHours() / 24.0);
    }

    @Test
    void marking_a_change_understood_puts_it_in_the_queue_for_tomorrow() throws Exception {
        Change c = change();

        mvc.perform(put("/api/changes/" + c.getId() + "/understood")).andExpect(status().isOk());

        ReviewItem item = item(c);
        assertEquals(0, item.getStep());
        assertEquals(1, daysUntilDue(item));
        assertFalse(schedule.isDue(item), "not due yet today");
    }

    @Test
    void unmarking_it_takes_it_out_of_the_queue() throws Exception {
        Change c = change();
        mvc.perform(put("/api/changes/" + c.getId() + "/understood"));
        mvc.perform(put("/api/changes/" + c.getId() + "/understood"));

        assertTrue(reviewRepository.findByChangeId(c.getId()).isEmpty());
    }

    @Test
    void passing_a_teach_back_puts_it_in_the_queue() throws Exception {
        Change c = change();
        Explanation e = new Explanation(UUID.randomUUID(), c);
        e.setOverview("x");
        e.setSteps("[]");
        e.setConcepts("[]");
        e.setKeyPoints("[\"it changes old to new\"]");
        e.setTestable(true);
        e.setStatus(Explanation.ExplanationStatus.COMPLETE);
        explanationRepository.save(e);
        ai.gradeVerdicts.add("pass");

        mvc.perform(post("/api/changes/" + c.getId() + "/teach-back")
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"It changes old to new.\"}"))
                .andExpect(status().isOk());

        assertEquals(1, daysUntilDue(item(c)));
    }

    @Test
    void each_pass_moves_to_the_next_gap_and_the_14_day_pass_finishes_it() throws Exception {
        Change c = change();
        mvc.perform(put("/api/changes/" + c.getId() + "/understood"));

        long[] next = {3, 7, 14};
        for (long days : next) {
            ReviewItem item = schedule.record(item(c), ReviewSchedule.Result.PASS);
            assertEquals(days, daysUntilDue(item));
            assertNull(item.getDoneAt());
        }
        ReviewItem done = schedule.record(item(c), ReviewSchedule.Result.PASS);
        assertNotNull(done.getDoneAt(), "passing the 14-day review finishes it");

        clock.pretendDaysPassed(100);
        assertFalse(schedule.isDue(item(c)), "a finished item never comes back");
    }

    @Test
    void partly_repeats_the_same_gap_and_fail_starts_again_at_one_day() throws Exception {
        Change c = change();
        mvc.perform(put("/api/changes/" + c.getId() + "/understood"));
        schedule.record(item(c), ReviewSchedule.Result.PASS);   // now on the 3-day gap

        ReviewItem partly = schedule.record(item(c), ReviewSchedule.Result.PARTLY);
        assertEquals(1, partly.getStep());
        assertEquals(3, daysUntilDue(partly));

        ReviewItem failed = schedule.record(item(c), ReviewSchedule.Result.FAIL);
        assertEquals(0, failed.getStep());
        assertEquals(1, daysUntilDue(failed));
    }

    @Test
    void pretending_days_passed_makes_it_due_and_reset_puts_the_clock_back() throws Exception {
        Change c = change();
        mvc.perform(put("/api/changes/" + c.getId() + "/understood"));
        assertFalse(schedule.isDue(item(c)));

        clock.pretendDaysPassed(1);
        assertEquals(1, clock.daysAhead());
        assertTrue(schedule.isDue(item(c)));

        clock.reset();
        assertEquals(0, clock.daysAhead());
        assertFalse(schedule.isDue(item(c)));
    }

    @Test
    void deleting_the_session_deletes_its_review_items() throws Exception {
        Change c = change();
        mvc.perform(put("/api/changes/" + c.getId() + "/understood"));

        sessionService.delete(session.getId());

        assertTrue(reviewRepository.findByChangeId(c.getId()).isEmpty());
    }
}
