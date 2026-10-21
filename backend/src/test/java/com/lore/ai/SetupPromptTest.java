package com.lore.ai;

import com.lore.ai.ApiKeyNeverLeaksTest.FakeTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** First-run setup (key, coding level, goal) and "lore config". */
class SetupPromptTest {

    private static final String FAKE_KEY = "sk-ant-test-SETUP-1234567890";

    private static FakeTerminal terminal(String key, String... lines) {
        FakeTerminal t = new FakeTerminal(key);
        t.lines.addAll(java.util.List.of(lines));
        return t;
    }

    @Test
    void first_run_asks_key_level_and_goal_and_saves_them(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        FakeTerminal t = terminal(FAKE_KEY, "1", "2");

        new ApiKeyPrompt(store, t).run("ui");

        assertEquals(1, t.asked);
        assertEquals(2, t.linesAsked);
        assertEquals(FAKE_KEY, store.find().orElseThrow());
        assertEquals(new UserProfile(UserProfile.Level.NEW_TO_CODING, UserProfile.Goal.LEARN_TO_CODE),
                store.profile().orElseThrow());
        assertTrue(t.printed().contains("lore config"));
        assertFalse(t.printed().contains(FAKE_KEY));
    }

    @Test
    void second_run_asks_nothing(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        new ApiKeyPrompt(store, terminal(FAKE_KEY, "3", "1")).run("start");

        FakeTerminal again = terminal(FAKE_KEY);
        new ApiKeyPrompt(store, again).run("start");

        assertEquals(0, again.asked);
        assertEquals(0, again.linesAsked);
        assertEquals("", again.printed());
    }

    @Test
    void existing_key_without_profile_asks_only_profile(@TempDir Path home) throws Exception {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        store.save(FAKE_KEY);
        FakeTerminal t = terminal(FAKE_KEY, "3", "1");

        new ApiKeyPrompt(store, t).run("start");

        assertEquals(0, t.asked);
        assertEquals(2, t.linesAsked);
        assertEquals(UserProfile.Level.DEVELOPER, store.profile().orElseThrow().level());
        assertEquals(FAKE_KEY, store.find().orElseThrow());
    }

    @Test
    void enter_picks_the_default(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        new ApiKeyPrompt(store, terminal("")).run("start");

        assertEquals(UserProfile.DEFAULT, store.profile().orElseThrow());
    }

    @Test
    void bad_answers_ask_again(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        FakeTerminal t = terminal("", "9", "abc", "3", "2");

        new ApiKeyPrompt(store, t).run("start");

        assertEquals(UserProfile.Level.DEVELOPER, store.profile().orElseThrow().level());
        assertEquals(UserProfile.Goal.LEARN_TO_CODE, store.profile().orElseThrow().goal());
        assertTrue(t.printed().contains("Please type a number from 1 to 3."));
    }

    @Test
    void setup_only_runs_before_start_and_ui_in_a_terminal(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        for (String[] args : new String[][] { {}, {"stop"}, {"config"}, {"key"}, {"start", "--help"} }) {
            FakeTerminal t = terminal(FAKE_KEY);
            new ApiKeyPrompt(store, t).run(args);
            assertEquals(0, t.asked + t.linesAsked, String.join(" ", args));
        }

        FakeTerminal piped = terminal(FAKE_KEY);
        piped.interactive = false;
        new ApiKeyPrompt(store, piped).run("ui");
        assertEquals(0, piped.asked + piped.linesAsked);
        assertTrue(store.profile().isEmpty());
    }

    @Test
    void other_providers_skip_the_key_but_still_ask_profile(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        FakeTerminal t = terminal(FAKE_KEY, "2", "1");

        new ApiKeyPrompt(store, t, "bedrock").run("start");

        assertEquals(0, t.asked);
        assertTrue(store.profile().isPresent());
    }

    @Test
    void config_changes_level_and_keeps_the_rest(@TempDir Path home) throws Exception {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        store.save(FAKE_KEY);
        store.saveProfile(new UserProfile(UserProfile.Level.KNOWS_SOME, UserProfile.Goal.LEARN_TO_CODE));
        FakeTerminal t = terminal("", "1", "");

        new ApiKeyPrompt(store, t).configure();

        assertTrue(t.printed().contains("API key:      saved"));
        assertTrue(t.printed().contains("Coding level: I know some coding"));
        assertEquals(FAKE_KEY, store.find().orElseThrow());
        assertEquals(new UserProfile(UserProfile.Level.NEW_TO_CODING, UserProfile.Goal.LEARN_TO_CODE),
                store.profile().orElseThrow());
    }

    @Test
    void config_with_only_enter_changes_nothing(@TempDir Path home) throws Exception {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        store.save(FAKE_KEY);
        store.saveProfile(UserProfile.DEFAULT);
        FakeTerminal t = terminal("");

        new ApiKeyPrompt(store, t).configure();

        assertTrue(t.printed().contains("Profile unchanged."));
        assertEquals(FAKE_KEY, store.find().orElseThrow());
        assertEquals(UserProfile.DEFAULT, store.profile().orElseThrow());
    }

    @Test
    void config_refuses_without_a_terminal(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        FakeTerminal piped = terminal(FAKE_KEY, "1", "1");
        piped.interactive = false;

        new ApiKeyPrompt(store, piped).configure();

        assertEquals(0, piped.asked + piped.linesAsked);
        assertTrue(piped.printed().contains("in a terminal"));
    }

    @Test
    void unknown_saved_values_count_as_no_profile(@TempDir Path home) throws Exception {
        java.nio.file.Files.writeString(home.resolve("config.properties"),
                "profile.level=WIZARD\nprofile.goal=UNDERSTAND_APP\n");
        assertTrue(new ApiKeyStore(home, name -> null).profile().isEmpty());
    }
}
