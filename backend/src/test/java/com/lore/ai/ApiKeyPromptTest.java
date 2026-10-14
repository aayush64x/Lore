package com.lore.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyPromptTest {

    private static final String FAKE_KEY = "sk-ant-test-PROMPT-1234567890";

    @Test
    void skipping_is_remembered(@TempDir Path home) throws Exception {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);

        ApiKeyNeverLeaksTest.FakeTerminal first = new ApiKeyNeverLeaksTest.FakeTerminal("");
        new ApiKeyPrompt(store, first).run("start");
        assertEquals(1, first.asked);
        assertTrue(first.printed().contains("won't ask again"));
        assertTrue(store.find().isEmpty());
        assertTrue(store.promptSkipped());
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(store.configFile())));

        ApiKeyNeverLeaksTest.FakeTerminal second = new ApiKeyNeverLeaksTest.FakeTerminal(FAKE_KEY);
        new ApiKeyPrompt(store, second).run("stop");
        assertEquals(0, second.asked);
    }

    @Test
    void lore_key_sets_key_after_skip_and_clears_skip_flag(@TempDir Path home) throws Exception {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        store.markPromptSkipped();

        ApiKeyNeverLeaksTest.FakeTerminal t = new ApiKeyNeverLeaksTest.FakeTerminal(FAKE_KEY);
        new ApiKeyPrompt(store, t).changeKey();

        assertEquals(1, t.asked);
        assertEquals(FAKE_KEY, store.find().orElseThrow());
        assertFalse(store.promptSkipped());
        assertFalse(t.printed().contains(FAKE_KEY));
    }

    @Test
    void lore_key_replaces_existing_key_and_cancel_keeps_it(@TempDir Path home) throws Exception {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        store.save("sk-ant-old");

        new ApiKeyPrompt(store, new ApiKeyNeverLeaksTest.FakeTerminal(FAKE_KEY)).changeKey();
        assertEquals(FAKE_KEY, store.find().orElseThrow());

        ApiKeyNeverLeaksTest.FakeTerminal cancel = new ApiKeyNeverLeaksTest.FakeTerminal("");
        new ApiKeyPrompt(store, cancel).changeKey();
        assertEquals(FAKE_KEY, store.find().orElseThrow());
        assertTrue(cancel.printed().contains("Nothing changed"));
        assertFalse(store.promptSkipped());
    }

    @Test
    void lore_key_refuses_without_a_terminal(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);
        ApiKeyNeverLeaksTest.FakeTerminal piped = new ApiKeyNeverLeaksTest.FakeTerminal(FAKE_KEY);
        piped.interactive = false;

        new ApiKeyPrompt(store, piped).changeKey();

        assertEquals(0, piped.asked);
        assertTrue(store.find().isEmpty());
        assertTrue(piped.printed().contains("in a terminal"));
    }

    @Test
    void lore_key_warns_when_env_var_overrides(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> ApiKeyStore.ENV_VAR.equals(name) ? "sk-ant-env" : null);
        ApiKeyNeverLeaksTest.FakeTerminal t = new ApiKeyNeverLeaksTest.FakeTerminal(FAKE_KEY);

        new ApiKeyPrompt(store, t).changeKey();

        assertTrue(t.printed().contains("is used instead of the saved key"));
        assertFalse(t.printed().contains("sk-ant-env"));
    }

    @Test
    void first_run_prompt_does_not_fire_for_lore_key_or_other_providers(@TempDir Path home) {
        ApiKeyStore store = new ApiKeyStore(home, name -> null);

        ApiKeyNeverLeaksTest.FakeTerminal key = new ApiKeyNeverLeaksTest.FakeTerminal(FAKE_KEY);
        new ApiKeyPrompt(store, key).run("key");
        assertEquals(0, key.asked);

        ApiKeyNeverLeaksTest.FakeTerminal bedrock = new ApiKeyNeverLeaksTest.FakeTerminal(FAKE_KEY);
        new ApiKeyPrompt(store, bedrock, "bedrock").run("start");
        assertEquals(0, bedrock.asked);
    }
}
