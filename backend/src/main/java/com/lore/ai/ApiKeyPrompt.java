package com.lore.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.Console;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Set;

/**
 * First run: if no Anthropic API key is found, ask for it once (input hidden) and save it
 * to config.properties, readable only by the owner. Runs before the CLI command itself.
 * Only asks in a real terminal, so piped input (e.g. to lore stop) is never consumed.
 * If the user skips, that is remembered and they aren't asked again; "lore key" sets
 * or changes the key later.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyPrompt implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyPrompt.class);
    private static final Set<String> SKIP_ARGS = Set.of("-h", "--help", "-V", "--version", "help", "key");

    /** Terminal I/O, swappable in tests. */
    interface Terminal {
        boolean isInteractive();
        char[] readHidden(String prompt);
        PrintStream out();
    }

    private final ApiKeyStore keyStore;
    private final Terminal terminal;
    private final String provider;

    @Autowired
    public ApiKeyPrompt(ApiKeyStore keyStore, @Value("${lore.ai.provider:anthropic}") String provider) {
        this(keyStore, new SystemTerminal(), provider);
    }

    ApiKeyPrompt(ApiKeyStore keyStore, Terminal terminal) {
        this(keyStore, terminal, "anthropic");
    }

    ApiKeyPrompt(ApiKeyStore keyStore, Terminal terminal, String provider) {
        this.keyStore = keyStore;
        this.terminal = terminal;
        this.provider = provider;
    }

    @Override
    public void run(String... args) {
        if (!"anthropic".equals(provider)) return;
        if (Arrays.stream(args).anyMatch(SKIP_ARGS::contains)) return;
        if (keyStore.find().isPresent() || keyStore.promptSkipped() || !terminal.isInteractive()) return;
        askAndSave(true);
    }

    /** "lore key": set or replace the saved key. */
    public void changeKey() {
        if (!terminal.isInteractive()) {
            // Never accept the key as an argument or from a pipe: it would end up in shell history.
            terminal.out().println("Run lore key in a terminal to enter your key, or set " + ApiKeyStore.ENV_VAR + ".");
            return;
        }
        askAndSave(false);
        if (keyStore.envKeyPresent()) {
            terminal.out().println("Note: " + ApiKeyStore.ENV_VAR + " is set in your environment, "
                    + "and it is used instead of the saved key.");
        }
    }

    private void askAndSave(boolean firstRun) {
        PrintStream out = terminal.out();
        if (firstRun) {
            out.println("Lore uses Claude to summarize and explain your changes, so it needs an Anthropic API key.");
        }
        out.println("Get one at https://console.anthropic.com/settings/keys");
        out.println("It will be saved to " + keyStore.configFile() + " (only you can read it)");
        out.println("and only ever sent to api.anthropic.com.");

        char[] entered = terminal.readHidden(firstRun
                ? "Paste your API key (hidden), or press Enter to skip: "
                : "Paste your API key (hidden), or press Enter to cancel: ");
        String key = entered == null ? "" : new String(entered).strip();
        if (entered != null) Arrays.fill(entered, '\0');

        try {
            if (key.isEmpty()) {
                if (firstRun) {
                    keyStore.markPromptSkipped();
                    out.println("Skipped. Sessions are still saved, but without AI summaries.");
                    out.println("Lore won't ask again. Run lore key any time to add a key.");
                } else {
                    out.println("No key entered. Nothing changed.");
                }
                return;
            }
            if (!key.startsWith("sk-ant-")) {
                out.println("Note: Anthropic API keys usually start with sk-ant-. Saving it anyway.");
            }
            keyStore.save(key);
            out.println("Key saved to " + keyStore.configFile() + ".");
        } catch (IOException | RuntimeException e) {
            // Never include the key; the exception type and path are enough to debug.
            log.warn("Could not write {} ({})", keyStore.configFile(), e.getClass().getSimpleName());
            out.println("Could not write " + keyStore.configFile() + ". Set "
                    + ApiKeyStore.ENV_VAR + " instead.");
        }
    }

    static final class SystemTerminal implements Terminal {
        @Override
        public boolean isInteractive() {
            Console console = System.console();
            if (console == null) return false;
            // Java 22+ returns a Console even when input is piped; ask it if it's a real terminal.
            try {
                return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
            } catch (ReflectiveOperationException e) {
                return true; // Java 21: a non-null console means a terminal
            }
        }

        @Override
        public char[] readHidden(String prompt) {
            return System.console().readPassword("%s", prompt);
        }

        @Override
        public PrintStream out() {
            return System.out;
        }
    }
}
