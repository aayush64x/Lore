package com.lore;

import org.h2.mvstore.MVStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.diagnostics.FailureAnalyzer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class BrokenDatabaseAnalyzerTest {

    /** How Spring hands it over: the H2 error a few causes deep. */
    private static RuntimeException wrapped(Throwable cause) {
        return new IllegalStateException("Error creating bean", new RuntimeException("Unable to obtain connection", cause));
    }

    @Test
    void a_corrupt_or_unreadable_file_is_a_broken_database() {
        assertThat(BrokenDatabaseAnalyzer.isBrokenDatabase(wrapped(new SQLException("File corrupted", "90030", 90030)))).isTrue();
        assertThat(BrokenDatabaseAnalyzer.isBrokenDatabase(wrapped(new SQLException("IO Exception", "90028", 90028)))).isTrue();
        assertThat(BrokenDatabaseAnalyzer.isBrokenDatabase(wrapped(new MVStoreException(6, "Store header is corrupt: x")))).isTrue();
        assertThat(BrokenDatabaseAnalyzer.isBrokenDatabase(wrapped(
                new MVStoreException(1, "Reading from file x failed at 19637040 (length 9342976)")))).isTrue();
    }

    @Test
    void anything_else_is_left_to_spring() {
        assertThat(BrokenDatabaseAnalyzer.isBrokenDatabase(wrapped(new SQLException("Database may be already in use", "90020", 90020)))).isFalse();
        assertThat(BrokenDatabaseAnalyzer.isBrokenDatabase(wrapped(new MVStoreException(7, "The file is locked: x")))).isFalse();
        assertThat(BrokenDatabaseAnalyzer.isBrokenDatabase(new IllegalStateException("Port 8080 was already in use"))).isFalse();
        assertThat(new BrokenDatabaseAnalyzer().analyze(new IllegalStateException("something else"))).isNull();
    }

    @Test
    void with_a_good_backup_it_says_which_one_and_how_to_restore_it(@TempDir Path home) {
        Path backup = home.resolve("backups/lore-20261022-115534-198-ok.mv.db");

        String action = BrokenDatabaseAnalyzer.action(home, backup);

        assertThat(action).contains("Your last good backup is " + backup + " (from Oct 22, 2026 at 11:55).");
        assertThat(action).contains("Stop every lore command, including lore ui.");
        assertThat(action).contains("mv " + home.resolve("lore.mv.db") + " " + home.resolve("lore-damaged.mv.db"));
        assertThat(action).contains("cp " + backup + " " + home.resolve("lore.mv.db"));
        assertThat(BrokenDatabaseAnalyzer.description(home)).contains(home.resolve("lore.mv.db").toString()).contains("damaged");
    }

    @Test
    void with_no_good_backup_it_explains_starting_over(@TempDir Path home) {
        String action = BrokenDatabaseAnalyzer.action(home, null);

        assertThat(action).contains("There's no backup marked good");
        assertThat(action).contains("your sessions will be gone; your code is never touched");
        assertThat(action).doesNotContain("cp ");
    }

    @Test
    void paths_in_the_home_folder_are_written_with_a_tilde() {
        String home = System.getProperty("user.home");
        assertThat(BrokenDatabaseAnalyzer.shown(Path.of(home, ".lore", "lore.mv.db"))).isEqualTo("~/.lore/lore.mv.db");
        assertThat(BrokenDatabaseAnalyzer.shown(Path.of("/tmp/x/lore.mv.db"))).isEqualTo("/tmp/x/lore.mv.db");
    }

    @Test
    void the_newest_good_backup_is_found_and_unmarked_ones_are_skipped(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("backups"));
        for (String name : new String[]{"lore-20261020-090000-000-ok.mv.db", "lore-20261021-090000-000-ok.mv.db",
                "lore-20261022-090000-000.mv.db", "notes.txt"}) {
            Files.writeString(home.resolve("backups").resolve(name), "x");
        }

        assertThat(DbBackup.newestGood(home).getFileName().toString()).isEqualTo("lore-20261021-090000-000-ok.mv.db");
        assertThat(DbBackup.newestGood(home.resolve("nowhere"))).isNull();
    }

    @Test
    void it_is_registered_with_spring() throws Exception {
        // Spring reads FailureAnalyzers from META-INF/spring.factories (several jars have one).
        boolean registered = false;
        var files = getClass().getClassLoader().getResources("META-INF/spring.factories");
        while (files.hasMoreElements()) {
            try (var in = files.nextElement().openStream()) {
                String text = new String(in.readAllBytes());
                registered |= text.contains(FailureAnalyzer.class.getName() + "=" + BrokenDatabaseAnalyzer.class.getName());
            }
        }
        assertThat(registered).isTrue();
    }
}
