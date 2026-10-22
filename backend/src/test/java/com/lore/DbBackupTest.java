package com.lore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbBackupTest {

    @TempDir
    Path home;

    private final ByteArrayOutputStream warnings = new ByteArrayOutputStream();
    private final PrintStream warn = new PrintStream(warnings);
    private final LocalDateTime t0 = LocalDateTime.of(2026, 10, 21, 9, 0, 0);

    private List<String> backups() throws Exception {
        Path dir = home.resolve("backups");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void no_database_yet_does_nothing() throws Exception {
        assertThat(DbBackup.run(home, t0, warn)).isNull();
        assertThat(backups()).isEmpty();
        assertThat(warnings.toString()).isEmpty();
    }

    @Test
    void copies_the_database_with_a_timestamp() throws Exception {
        Files.writeString(home.resolve("lore.mv.db"), "data");

        Path backup = DbBackup.run(home, t0, warn);

        assertThat(backup.getFileName().toString()).isEqualTo("lore-20261021-090000-000.mv.db");
        assertThat(Files.readString(backup)).isEqualTo("data");
        assertThat(warnings.toString()).isEmpty();
    }

    @Test
    void keeps_only_the_newest_three() throws Exception {
        Files.writeString(home.resolve("lore.mv.db"), "data");
        // Something else in the folder is left alone.
        Files.createDirectories(home.resolve("backups"));
        Files.writeString(home.resolve("backups/notes.txt"), "keep me");

        for (int i = 0; i < 5; i++) {
            DbBackup.run(home, t0.plusMinutes(i), warn);
        }

        assertThat(backups()).containsExactly(
                "lore-20261021-090200-000.mv.db",
                "lore-20261021-090300-000.mv.db",
                "lore-20261021-090400-000.mv.db",
                "notes.txt");
    }

    @Test
    void a_failed_copy_warns_once_and_does_not_throw() throws Exception {
        Files.writeString(home.resolve("lore.mv.db"), "data");
        // A file where the backups folder should be, so the folder can't be made.
        Files.writeString(home.resolve("backups"), "not a folder");

        assertThat(DbBackup.run(home, t0, warn)).isNull();

        String out = warnings.toString();
        assertThat(out).startsWith("Warning: couldn't back up the Lore database");
        assertThat(out.lines().count()).isEqualTo(1);
    }

    @Test
    void mark_good_renames_the_backup() throws Exception {
        Files.writeString(home.resolve("lore.mv.db"), "data");

        Path good = DbBackup.markGood(DbBackup.run(home, t0, warn));

        assertThat(good.getFileName().toString()).isEqualTo("lore-20261021-090000-000-ok.mv.db");
        assertThat(Files.readString(good)).isEqualTo("data");
        assertThat(backups()).containsExactly("lore-20261021-090000-000-ok.mv.db");
        assertThat(DbBackup.markGood(null)).isNull();
    }

    @Test
    void starts_on_a_broken_database_never_push_out_the_last_good_backup() throws Exception {
        Files.writeString(home.resolve("lore.mv.db"), "good data");
        DbBackup.markGood(DbBackup.run(home, t0, warn));           // opened fine
        Files.writeString(home.resolve("lore.mv.db"), "broken");
        for (int i = 1; i <= 5; i++) {
            DbBackup.run(home, t0.plusMinutes(i), warn);           // each start fails: never marked good
        }

        assertThat(backups()).containsExactly(
                "lore-20261021-090000-000-ok.mv.db",
                "lore-20261021-090300-000.mv.db",
                "lore-20261021-090400-000.mv.db",
                "lore-20261021-090500-000.mv.db");
        assertThat(Files.readString(home.resolve("backups/lore-20261021-090000-000-ok.mv.db"))).isEqualTo("good data");
    }

    @Test
    void older_good_backups_still_rotate_out_once_newer_ones_are_good() throws Exception {
        Files.writeString(home.resolve("lore.mv.db"), "data");
        for (int i = 0; i < 5; i++) {
            DbBackup.markGood(DbBackup.run(home, t0.plusMinutes(i), warn));
        }

        assertThat(backups()).containsExactly(
                "lore-20261021-090200-000-ok.mv.db",
                "lore-20261021-090300-000-ok.mv.db",
                "lore-20261021-090400-000-ok.mv.db");
    }
}
