package com.lore.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Finds the Anthropic API key: the ANTHROPIC_API_KEY environment variable first,
 * then anthropic.api.key in {lore.home}/config.properties. The same file may also
 * hold anthropic.model=... to override the default Claude model, and the user's profile
 * (profile.level, profile.goal). Saving writes that file so only the owner can read it.
 * Nothing here ever logs or prints the key.
 */
@Component
public class ApiKeyStore {

    public static final String ENV_VAR = "ANTHROPIC_API_KEY";
    static final String PROPERTY = "anthropic.api.key";
    static final String MODEL_PROPERTY = "anthropic.model";
    /** Set to "skipped" when the user skipped the first-run key prompt, so it isn't shown again. */
    static final String PROMPT_PROPERTY = "anthropic.api.key.prompt";
    static final String LEVEL_PROPERTY = "profile.level";
    static final String GOAL_PROPERTY = "profile.goal";

    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_DIR = PosixFilePermissions.fromString("rwx------");

    private final Path configFile;
    private final UnaryOperator<String> env;

    @Autowired
    public ApiKeyStore(@Value("${lore.home}") Path loreHome) {
        this(loreHome, System::getenv);
    }

    ApiKeyStore(Path loreHome, UnaryOperator<String> env) {
        this.configFile = loreHome.resolve("config.properties");
        this.env = env;
    }

    public Path configFile() {
        return configFile;
    }

    public Optional<String> find() {
        String fromEnv = env.apply(ENV_VAR);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(fromEnv.strip());
        }
        return Optional.ofNullable(load().getProperty(PROPERTY))
                .map(String::strip)
                .filter(k -> !k.isEmpty());
    }

    /** The anthropic.model line from config.properties, if the user set one. */
    public Optional<String> modelOverride() {
        return Optional.ofNullable(load().getProperty(MODEL_PROPERTY))
                .map(String::strip)
                .filter(m -> !m.isEmpty());
    }

    /** True if ANTHROPIC_API_KEY is set; it takes priority over the saved key. */
    public boolean envKeyPresent() {
        String fromEnv = env.apply(ENV_VAR);
        return fromEnv != null && !fromEnv.isBlank();
    }

    public boolean promptSkipped() {
        return "skipped".equals(load().getProperty(PROMPT_PROPERTY));
    }

    /** Remembers that the user skipped the first-run prompt. */
    public void markPromptSkipped() throws IOException {
        Properties props = load();
        props.setProperty(PROMPT_PROPERTY, "skipped");
        write(props);
    }

    /** The saved profile; empty until both level and goal have been chosen. */
    public Optional<UserProfile> profile() {
        Properties props = load();
        var level = UserProfile.parse(UserProfile.Level.class, props.getProperty(LEVEL_PROPERTY));
        var goal = UserProfile.parse(UserProfile.Goal.class, props.getProperty(GOAL_PROPERTY));
        if (level.isEmpty() || goal.isEmpty()) return Optional.empty();
        return Optional.of(new UserProfile(level.get(), goal.get()));
    }

    /** Saves the profile, keeping the key and any other settings. */
    public void saveProfile(UserProfile profile) throws IOException {
        Properties props = load();
        props.setProperty(LEVEL_PROPERTY, profile.level().name());
        props.setProperty(GOAL_PROPERTY, profile.goal().name());
        write(props);
    }

    /** Saves the key to config.properties (owner read/write only), keeping any other settings. */
    public void save(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("API key is empty.");
        }
        Properties props = load();
        props.setProperty(PROPERTY, key.strip());
        props.remove(PROMPT_PROPERTY);
        write(props);
    }

    private void write(Properties props) throws IOException {
        Path dir = configFile.getParent();
        Files.createDirectories(dir);
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        if (posix) {
            Files.setPosixFilePermissions(dir, OWNER_DIR);
        }

        // Write to a private temp file first, so the key is never in a file others can read.
        Path tmp = posix
                ? Files.createTempFile(dir, "config", ".tmp", PosixFilePermissions.asFileAttribute(OWNER_FILE))
                : Files.createTempFile(dir, "config", ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                props.store(w, "Lore settings. Keep this file private.\n"
                        + "To change the Claude model, add a line like: anthropic.model=claude-opus-5-5");
            }
            Files.move(tmp, configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            if (posix) {
                Files.setPosixFilePermissions(configFile, OWNER_FILE);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private Properties load() {
        Properties props = new Properties();
        if (Files.isRegularFile(configFile)) {
            try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                // Unreadable config is treated as "no key"; the path is safe to show, the contents are not.
                throw new AiUnavailableException("Could not read " + configFile);
            }
        }
        return props;
    }

    @Override
    public String toString() {
        return "ApiKeyStore[" + configFile + "]";
    }
}
