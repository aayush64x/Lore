package com.lore.api;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.concept.ChangeConcept;
import com.lore.concept.ChangeConceptRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PUT /api/changes/{id}/note: the reader's own note on a change, shown on the change and on the Concepts page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChangeNoteTest {

    @Autowired MockMvc mvc;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ChangeConceptRepository conceptRepository;

    Session session;
    Change change;

    @BeforeEach
    void setUp() {
        session = new Session(UUID.randomUUID(), "/tmp/note-" + UUID.randomUUID(), "refs/lore/sessions/x");
        session.setStatus(Session.SessionStatus.COMPLETE);
        session.setStoppedAt(Instant.now());
        sessionRepository.save(session);
        change = new Change(UUID.randomUUID(), session, "todo.js", Change.ChangeType.MODIFIED);
        changeRepository.save(change);
        conceptRepository.save(new ChangeConcept(change, "Functions", false));
    }

    private ResultActions save(String json) throws Exception {
        return mvc.perform(put("/api/changes/" + change.getId() + "/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void a_note_is_saved_trimmed_and_shown_on_the_change_and_the_concepts_page() throws Exception {
        save("{\"note\":\"  filter() makes a new list, it doesn't change the old one.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("filter() makes a new list, it doesn't change the old one."));

        assertEquals("filter() makes a new list, it doesn't change the old one.",
                changeRepository.findById(change.getId()).orElseThrow().getNote());
        mvc.perform(get("/api/sessions/" + session.getId()))
                .andExpect(jsonPath("$.changes[0].note").value("filter() makes a new list, it doesn't change the old one."));
        mvc.perform(get("/api/concepts/graph").param("project", session.getProjectPath()))
                .andExpect(jsonPath("$.nodes[0].changes[0].note").value("filter() makes a new list, it doesn't change the old one."));
    }

    @Test
    void a_blank_note_clears_it() throws Exception {
        save("{\"note\":\"Keep this\"}").andExpect(status().isOk());
        save("{\"note\":\"   \"}").andExpect(status().isOk()).andExpect(jsonPath("$.note").value(nullValue()));
        assertNull(changeRepository.findById(change.getId()).orElseThrow().getNote());
        mvc.perform(get("/api/sessions/" + session.getId())).andExpect(jsonPath("$.changes[0].note").value(nullValue()));
    }

    @Test
    void a_note_that_is_too_long_is_refused_and_the_old_one_kept() throws Exception {
        save("{\"note\":\"Keep this\"}").andExpect(status().isOk());
        save("{\"note\":\"" + "a".repeat(5001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Keep it under 5000 characters."));
        assertEquals("Keep this", changeRepository.findById(change.getId()).orElseThrow().getNote());
    }

    @Test
    void an_unknown_change_is_404() throws Exception {
        mvc.perform(put("/api/changes/" + UUID.randomUUID() + "/note")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
