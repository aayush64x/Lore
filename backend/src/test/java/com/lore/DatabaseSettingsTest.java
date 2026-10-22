package com.lore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Tests run on an in-memory database, so this reads the real settings from application.yml. */
class DatabaseSettingsTest {

    /** The production URL with ${lore.home} filled in. */
    private static String datasourceUrl(Path loreHome) throws Exception {
        try (InputStream in = DatabaseSettingsTest.class.getResourceAsStream("/application.yml")) {
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return yml.lines()
                    .map(String::strip)
                    .filter(l -> l.startsWith("url: jdbc:h2:file:"))
                    .findFirst()
                    .orElseThrow()
                    .substring("url: ".length())
                    .replace("${lore.home}", loreHome.toString());
        }
    }

    @Test
    void h2_never_compacts_while_closing(@TempDir Path home) throws Exception {
        // A compaction cut off halfway left a file the database couldn't open again.
        assertTrue(datasourceUrl(home).contains(";MAX_COMPACT_TIME=0"), datasourceUrl(home));
    }

    @Test
    void the_real_url_opens_a_file_database_and_keeps_what_was_written(@TempDir Path home) throws Exception {
        // H2 refuses some setting combinations (like AUTO_SERVER with DB_CLOSE_ON_EXIT=FALSE)
        // only when it opens the file, which the in-memory test database never does.
        String url = datasourceUrl(home);
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE t (n INT)");
            s.execute("INSERT INTO t SELECT X FROM SYSTEM_RANGE(1, 1000)");
            s.execute("UPDATE t SET n = n + 1");
        }
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             ResultSet r = c.createStatement().executeQuery("SELECT COUNT(*), SUM(n) FROM t")) {
            r.next();
            assertEquals(1000, r.getInt(1));
            assertEquals(500500 + 1000, r.getLong(2)); // 1 + 2 + ... + 1000, then +1 each
        }
    }
}
