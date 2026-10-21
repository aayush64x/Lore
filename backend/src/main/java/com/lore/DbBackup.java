package com.lore;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies the database to {lore.home}/backups/lore-<timestamp>.mv.db each time Lore
 * starts, before anything opens it, and keeps the newest 3. One bad shutdown
 * (like a crash while saving) can then cost at most one run's work.
 *
 * Never stops Lore from starting: if the copy fails it prints one warning line.
 */
public final class DbBackup {

    static final int KEEP = 3;
    static final String DB_FILE = "lore.mv.db";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private DbBackup() {}

    /**
     * Where Lore keeps its data. Same rule as lore.home in application.yml:
     * the lore.home system property, then LORE_HOME, then ~/.lore.
     */
    static Path loreHome() {
        String prop = System.getProperty("lore.home");
        if (prop != null && !prop.isBlank()) return Paths.get(prop);
        String env = System.getenv("LORE_HOME");
        if (env != null && !env.isBlank()) return Paths.get(env);
        return Paths.get(System.getProperty("user.home"), ".lore");
    }

    public static void run() {
        run(loreHome(), LocalDateTime.now(), System.err);
    }

    /** Returns the backup it made, or null if there was nothing to back up or the copy failed. */
    static Path run(Path loreHome, LocalDateTime now, PrintStream warn) {
        Path db = loreHome.resolve(DB_FILE);
        if (!Files.isRegularFile(db)) return null;
        try {
            Path dir = loreHome.resolve("backups");
            Files.createDirectories(dir);
            Path backup = dir.resolve("lore-" + STAMP.format(now) + ".mv.db");
            Files.copy(db, backup);
            prune(dir);
            return backup;
        } catch (IOException | RuntimeException e) {
            warn.println("Warning: couldn't back up the Lore database (" + e.getMessage() + "). Continuing anyway.");
            return null;
        }
    }

    /** Deletes all but the newest KEEP backups. The timestamp in the name sorts by age. */
    private static void prune(Path dir) throws IOException {
        List<Path> backups;
        try (Stream<Path> files = Files.list(dir)) {
            backups = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("lore-") && name.endsWith(".mv.db");
                    })
                    .sorted()
                    .toList();
        }
        for (int i = 0; i < backups.size() - KEEP; i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }
}
