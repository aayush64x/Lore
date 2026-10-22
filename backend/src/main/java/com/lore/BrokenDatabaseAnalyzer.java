package com.lore;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * When Lore can't start because its database file is damaged, Spring prints this short
 * description and what to do (restore the newest backup marked good) instead of a long
 * stack trace. Any other startup failure is left to Spring, shown in full.
 * Registered in META-INF/spring.factories.
 */
public class BrokenDatabaseAnalyzer implements FailureAnalyzer {

    /** H2's ErrorCode.FILE_CORRUPTED_1 and IO_EXCEPTION_1 (H2 is only on the runtime classpath). */
    static final int FILE_CORRUPTED = 90030;
    static final int IO_EXCEPTION = 90028;

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm", Locale.ENGLISH);

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        if (!isBrokenDatabase(failure)) return null;
        Path home = DbBackup.loreHome();
        return new FailureAnalysis(description(home), action(home, DbBackup.newestGood(home)), failure);
    }

    /** H2 says the file is corrupt or can't be read (not, say, locked by another program). */
    static boolean isBrokenDatabase(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql
                    && (sql.getErrorCode() == FILE_CORRUPTED || sql.getErrorCode() == IO_EXCEPTION)) {
                return true;
            }
            if (t.getClass().getName().equals("org.h2.mvstore.MVStoreException") && t.getMessage() != null
                    && (t.getMessage().contains("corrupt") || t.getMessage().contains("Reading from file"))) {
                return true;
            }
        }
        return false;
    }

    static String description(Path home) {
        return "Lore couldn't open its database, " + shown(home.resolve(DbBackup.DB_FILE))
                + ". The file looks damaged (this can happen if Lore was stopped while saving).";
    }

    static String action(Path home, Path goodBackup) {
        String db = shown(home.resolve(DbBackup.DB_FILE));
        String aside = shown(home.resolve("lore-damaged.mv.db"));
        if (goodBackup == null) {
            return "There's no backup marked good in " + shown(home.resolve("backups")) + ".\n"
                    + "To start again with an empty database (your sessions will be gone; your code is never touched):\n"
                    + "  1. Stop every lore command, including lore ui.\n"
                    + "  2. mv " + db + " " + aside + "\n"
                    + "  3. Run lore again.";
        }
        return "Your last good backup is " + shown(goodBackup) + when(goodBackup) + ".\n"
                + "To go back to it:\n"
                + "  1. Stop every lore command, including lore ui.\n"
                + "  2. Keep the damaged file aside:  mv " + db + " " + aside + "\n"
                + "  3. Put the backup in its place:  cp " + shown(goodBackup) + " " + db + "\n"
                + "  4. Run lore again. Only what you did after that backup is lost.";
    }

    /** " (from Oct 22, 2026 at 11:55)", read from the backup's name; "" if the name doesn't say. */
    private static String when(Path backup) {
        String name = backup.getFileName().toString();
        try {
            String stamp = name.substring("lore-".length(), name.length() - DbBackup.GOOD_SUFFIX.length());
            return " (from " + WHEN.format(LocalDateTime.parse(stamp, BACKUP_STAMP)) + ")";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The path with the home folder written as ~, as people type it. */
    static String shown(Path path) {
        String home = System.getProperty("user.home");
        String p = path.toString();
        return home != null && p.startsWith(home + "/") ? "~" + p.substring(home.length()) : p;
    }
}
