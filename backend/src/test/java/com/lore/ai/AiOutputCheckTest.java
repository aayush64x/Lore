package com.lore.ai;

import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiOutputCheckTest {

    @Test
    void text_ending_in_leaked_json_is_damaged() {
        assertTrue(AiOutputCheck.isDamaged("it keeps the comment matching the code.}]}"));
        assertTrue(AiOutputCheck.isDamaged("going over `maxLength`.}]} "));
        assertTrue(AiOutputCheck.isDamaged("done\"}"));
        assertTrue(AiOutputCheck.isDamaged("done\"]"));
        assertTrue(AiOutputCheck.isDamaged("done}]"));
    }

    @Test
    void normal_text_is_not_damaged() {
        assertFalse(AiOutputCheck.isDamaged("Returns an empty object {}"));
        assertFalse(AiOutputCheck.isDamaged("The closing `}` ends the function."));
        assertFalse(AiOutputCheck.isDamaged("It says \"hello\""));
        assertFalse(AiOutputCheck.isDamaged("Uses list[0]"));
        assertFalse(AiOutputCheck.isDamaged(""));
        assertFalse(AiOutputCheck.isDamaged(null));
    }

    @Test
    void problems_names_each_damaged_field() {
        ExplanationResponse r = new ExplanationResponse("Fine.",
                List.of(new ExplanationResponse.Step("ok", 1, 1), new ExplanationResponse.Step("bad.}]}", 2, 2)),
                List.of(new ExplanationResponse.Concept("x", "y\"}")));
        assertEquals(List.of("steps[1].text", "concepts[0].meaning"), AiOutputCheck.problems(r));

        SummaryResponse s = new SummaryResponse("All good.}]}",
                List.of(new SummaryResponse.ChangeDescription("a.js", 0, "fine")));
        assertEquals(List.of("session_summary"), AiOutputCheck.problems(s));
    }

    @Test
    void goal_check_text_is_checked_too() {
        SummaryResponse s = new SummaryResponse("Fine.",
                List.of(new SummaryResponse.ChangeDescription("a.js", 0, "fine", "no", "Styling only.}]}")),
                List.of("ok", "tests\"]"));
        assertEquals(List.of("change_descriptions[0].goal_reason", "goal_parts_not_done[1]"), AiOutputCheck.problems(s));
    }

    @Test
    void a_damaged_answer_is_asked_for_once_more() {
        AtomicInteger calls = new AtomicInteger();
        String result = AiOutputCheck.askWithRetry(
                () -> calls.incrementAndGet() == 1 ? "bad.}]}" : "good.",
                text -> AiOutputCheck.isDamaged(text) ? List.of("text") : List.of());
        assertEquals("good.", result);
        assertEquals(2, calls.get());
    }

    @Test
    void damaged_twice_throws_instead_of_returning_it() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(AiUnavailableException.class, () -> AiOutputCheck.askWithRetry(
                () -> { calls.incrementAndGet(); return "bad.}]}"; },
                text -> AiOutputCheck.isDamaged(text) ? List.of("text") : List.of()));
        assertEquals(2, calls.get());
    }

    @Test
    void a_good_answer_is_asked_for_only_once() {
        AtomicInteger calls = new AtomicInteger();
        AiOutputCheck.askWithRetry(() -> { calls.incrementAndGet(); return "good."; }, text -> List.of());
        assertEquals(1, calls.get());
    }
}
