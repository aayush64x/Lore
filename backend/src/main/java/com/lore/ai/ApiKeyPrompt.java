package com.lore.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "lore.ai.provider", havingValue = "anthropic", matchIfMissing = true)
public class ApiKeyPrompt implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyPrompt.class);
    private static final Set<String> SKIP_ARGS = Set.of("-h", "--help", "-V", "--version", "help");

    /** Terminal I/O, swappable in tests. */
    interface Terminal {
        boolean isInteractive();
        char[] readHidden(String prompt);
        PrintStream out();
    }

    private final ApiKeyStore keyStore;
    private final Terminal terminal;

    @Autowired
    public ApiKeyPrompt(ApiKeyStore keyStore) {
        this(keyStore, new SystemTerminal());
    }

    ApiKeyPrompt(ApiKeyStore keyStore, Terminal terminal) {
        this.keyStore = keyStore;
        this.terminal = terminal;
    }

    @Override
    public void run(String... args) {
        if (Arrays.stream(args).anyMatch(SKIP_ARGS::contains)) return;
        if (keyStore.find().isPresent() || !terminal.isInteractive()) return;

        PrintStream out = terminal.out();
        out.println("Lore uses Claude to summarize and explain your changes, so it needs an Anthropic API key.");
        out.println("Get one at https://console.anthropic.com/settings/keys");
        out.println("It will be saved to " + keyStore.configFile() + " (only you can read it)");
        out.println("and only ever sent to api.anthropic.com.");

        char[] entered = terminal.readHidden("Paste your API key (hidden), or press Enter to skip: ");
        String key = entered == null ? "" : new String(entered).strip();
        if (entered != null) Arrays.fill(entered, '\0');

        if (key.isEmpty()) {
            out.println("Skipped. Sessions are still saved, but without AI summaries.");
            out.println("Set " + ApiKeyStore.ENV_VAR + " or run lore again to add a key.");
            return;
        }
        if (!key.startsWith("sk-ant-")) {
            out.println("Note: Anthropic API keys usually start with sk-ant-. Saving it anyway.");
        }
        try {
            keyStore.save(key);
            out.println("Key saved to " + keyStore.configFile() + ".");
        } catch (IOException | RuntimeException e) {
            // Never include the key; the exception type and path are enough to debug.
            log.warn("Could not save API key to {} ({})", keyStore.configFile(), e.getClass().getSimpleName());
            out.println("Could not save the key to " + keyStore.configFile() + ". Set "
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
