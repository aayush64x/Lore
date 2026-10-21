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
 * First-run setup, before "lore start" or "lore ui": asks for the Anthropic API key
 * (input hidden) if none is found, then the user's coding level and goal if they
 * aren't saved. Everything goes to config.properties, readable only by the owner.
 * Only asks in a real terminal, so piped input is never consumed.
 * A skipped key is remembered and not asked again; "lore key" sets the key later,
 * and "lore config" changes any setting.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyPrompt implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyPrompt.class);
    private static final Set<String> SKIP_ARGS = Set.of("-h", "--help", "-V", "--version", "help");
    /** Setup only runs before these commands. */
    private static final Set<String> SETUP_COMMANDS = Set.of("start", "ui");

    /** Terminal I/O, swappable in tests. */
    interface Terminal {
        boolean isInteractive();
        char[] readHidden(String prompt);
        /** One visible line; null if input has ended. */
        String readLine(String prompt);
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
        if (args.length == 0 || !SETUP_COMMANDS.contains(args[0])) return;
        if (Arrays.stream(args).anyMatch(SKIP_ARGS::contains)) return;
        if (!terminal.isInteractive()) return;

        boolean needKey = usesAnthropic() && keyStore.find().isEmpty() && !keyStore.promptSkipped();
        boolean needProfile = keyStore.profile().isEmpty();
        if (!needKey && !needProfile) return;

        PrintStream out = terminal.out();
        out.println("Welcome to Lore. A quick setup before you start.");
        out.println();
        if (needKey) {
            askAndSave(true);
            out.println();
        }
        if (needProfile) {
            saveProfile(askProfile(UserProfile.DEFAULT));
        }
        out.println("Change these any time with lore config.");
        out.println();
    }

    /** "lore config": show the current settings and change any of them. */
    public void configure() {
        PrintStream out = terminal.out();
        if (!terminal.isInteractive()) {
            out.println("Run lore config in a terminal to change your settings.");
            return;
        }
        UserProfile current = keyStore.profile().orElse(UserProfile.DEFAULT);

        out.println("Your Lore settings (" + keyStore.configFile() + "):");
        if (usesAnthropic()) {
            out.println("  API key:      " + keyStatus());
        }
        out.println("  Coding level: " + current.level().label());
        out.println("  Goal:         " + current.goal().label());
        out.println();
        out.println("Press Enter on any question to keep what you have.");
        out.println();

        if (usesAnthropic()) {
            changeKey();
            out.println();
        }
        UserProfile chosen = askProfile(current);
        if (chosen.equals(current) && keyStore.profile().isPresent()) {
            out.println("Profile unchanged.");
        } else {
            saveProfile(chosen);
        }
    }

    private boolean usesAnthropic() {
        return "anthropic".equals(provider);
    }

    private String keyStatus() {
        if (keyStore.envKeyPresent()) return "from " + ApiKeyStore.ENV_VAR;
        return keyStore.find().isPresent() ? "saved" : "not set";
    }

    private UserProfile askProfile(UserProfile current) {
        UserProfile.Level level = ask("How much coding do you know?", UserProfile.Level.values(), current.level());
        terminal.out().println();
        UserProfile.Goal goal = ask("What do you want from Lore?", UserProfile.Goal.values(), current.goal());
        terminal.out().println();
        return new UserProfile(level, goal);
    }

    /** Numbered menu. Enter (or end of input) keeps the current choice. */
    private <C extends UserProfile.Choice> C ask(String question, C[] choices, C current) {
        PrintStream out = terminal.out();
        out.println(question);
        for (int i = 0; i < choices.length; i++) {
            out.println("  " + (i + 1) + ". " + choices[i].label());
        }
        int currentNumber = Arrays.asList(choices).indexOf(current) + 1;
        for (int attempt = 0; attempt < 3; attempt++) {
            String line = terminal.readLine("Type a number (Enter = " + currentNumber + "): ");
            if (line == null || line.isBlank()) return current;
            try {
                int n = Integer.parseInt(line.strip());
                if (n >= 1 && n <= choices.length) return choices[n - 1];
            } catch (NumberFormatException ignored) {
                // fall through to the hint below
            }
            out.println("Please type a number from 1 to " + choices.length + ".");
        }
        out.println("Keeping " + current.label() + ".");
        return current;
    }

    private void saveProfile(UserProfile profile) {
        try {
            keyStore.saveProfile(profile);
            terminal.out().println("Saved: " + profile.level().label() + ", " + profile.goal().label() + ".");
        } catch (IOException | RuntimeException e) {
            log.warn("Could not write {} ({})", keyStore.configFile(), e.getClass().getSimpleName());
            terminal.out().println("Could not write " + keyStore.configFile() + ". Lore will ask again next time.");
        }
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
        public String readLine(String prompt) {
            return System.console().readLine("%s", prompt);
        }

        @Override
        public PrintStream out() {
            return System.out;
        }
    }
}
