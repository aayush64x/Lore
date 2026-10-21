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
}
