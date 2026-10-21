package com.lore.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opening or refreshing a page in the browser should load the React app, not a 404. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void sessionPageLoadsApp() throws Exception {
        mvc.perform(get("/sessions/abc"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void filePageLoadsApp() throws Exception {
        mvc.perform(get("/sessions/abc/file").param("path", "src/App.tsx"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
