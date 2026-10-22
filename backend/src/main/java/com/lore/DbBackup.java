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
 * Once Lore has opened the database without errors, that start's backup is marked good
 * (renamed to lore-<timestamp>-ok.mv.db): it is a copy of a database that opened. The
 * newest good backup is never deleted, so starts on a broken database, which back up
 * the broken file, can't push the last good copy out.
 *
 * Never stops Lore from starting: if the copy fails it prints one warning line.
 */
public final class DbBackup {

    static final int KEEP = 3;
    static final String DB_FILE = "lore.mv.db";
    static final String GOOD_SUFFIX = "-ok.mv.db";
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

    /** Returns the backup it made (for markGood), or null. */
    public static Path run() {
        return run(loreHome(), LocalDateTime.now(), System.err);
    }

    /**
     * Call once the database has opened fine: renames this start's backup to -ok.mv.db.
     * Does nothing for null; a failed rename leaves the backup as it was.
     */
    public static Path markGood(Path backup) {
        if (backup == null) return null;
        String name = backup.getFileName().toString();
        if (name.endsWith(GOOD_SUFFIX) || !Files.isRegularFile(backup)) return backup;
        Path good = backup.resolveSibling(name.substring(0, name.length() - ".mv.db".length()) + GOOD_SUFFIX);
        try {
            return Files.move(backup, good);
        } catch (IOException | RuntimeException e) {
            return backup;
        }
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

    /**
     * Deletes all but the newest KEEP backups, except the newest good one, which is always
     * kept. The timestamp in the name sorts by age.
     */
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
        Path newestGood = null;
        for (Path p : backups) {
            if (p.getFileName().toString().endsWith(GOOD_SUFFIX)) newestGood = p;
        }
        for (int i = 0; i < backups.size() - KEEP; i++) {
            if (!backups.get(i).equals(newestGood)) Files.deleteIfExists(backups.get(i));
        }
    }
}
