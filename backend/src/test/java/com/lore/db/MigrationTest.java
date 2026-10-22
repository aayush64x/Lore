package com.lore.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migrations run against data that already exists, which the other tests never see
 * (they start from an empty database). Each test stops at one version, adds rows the way
 * an older Lore would have, then runs the next migration.
 */
class MigrationTest {

    private static String newDb() {
        return "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private static void migrateTo(String url, String version) {
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target(version).load().migrate();
    }

    @Test
    void v7_marks_changes_already_understood_as_self_and_leaves_the_rest_alone() throws Exception {
        String url = newDb();
        migrateTo(url, "6");
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute("INSERT INTO sessions (id, project_path, start_ref, status, started_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', '/p', 'r', 'COMPLETE', CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO changes (id, session_id, file_path, change_type, understood) VALUES "
                    + "('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-000000000001', 'done.js', 'MODIFIED', TRUE), "
                    + "('00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-000000000001', 'todo.js', 'MODIFIED', FALSE)");
        }

        migrateTo(url, "7");

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT file_path, understood, understood_by FROM changes ORDER BY file_path")) {
            assertTrue(r.next());
            assertEquals("done.js", r.getString(1));
            assertTrue(r.getBoolean(2));
            assertEquals("SELF", r.getString(3));
            assertTrue(r.next());
            assertEquals("todo.js", r.getString(1));
            assertFalse(r.getBoolean(2));
            assertNull(r.getString(3));
        }
    }

    @Test
    void v8_counts_every_existing_attempt_as_a_real_try() throws Exception {
        String url = newDb();
        migrateTo(url, "7");
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute("INSERT INTO sessions (id, project_path, start_ref, status, started_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', '/p', 'r', 'COMPLETE', CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO changes (id, session_id, file_path, change_type) VALUES "
                    + "('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-000000000001', 'a.js', 'MODIFIED')");
            s.execute("INSERT INTO teach_back_attempts (id, change_id, answer, verdict, created_at) VALUES "
                    + "('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-00000000000a', 'x', 'FAIL', CURRENT_TIMESTAMP)");
        }

        migrateTo(url, "8");

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT practice FROM teach_back_attempts")) {
            assertTrue(r.next());
            assertFalse(r.getBoolean(1));
        }
    }

    @Test
    void v5_and_v6_leave_older_rows_unchecked() throws Exception {
        String url = newDb();
        migrateTo(url, "4");
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute("INSERT INTO sessions (id, project_path, start_ref, status, started_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', '/p', 'r', 'COMPLETE', CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO changes (id, session_id, file_path, change_type) VALUES "
                    + "('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-000000000001', 'a.js', 'MODIFIED')");
            s.execute("INSERT INTO explanations (id, change_id, overview, status, created_at) VALUES "
                    + "('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-00000000000a', 'Old.', 'COMPLETE', CURRENT_TIMESTAMP)");
        }

        migrateTo(url, "7");

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            ResultSet r = s.executeQuery("SELECT goal_gaps FROM sessions");
            assertTrue(r.next());
            assertNull(r.getString(1), "old sessions were never goal-checked");
            r = s.executeQuery("SELECT goal_match FROM changes");
            assertTrue(r.next());
            assertNull(r.getString(1));
            r = s.executeQuery("SELECT key_points, testable, possible_issue, overview FROM explanations");
            assertTrue(r.next());
            assertNull(r.getString(1), "no backfill of key points");
            assertNull(r.getObject(2));
            assertNull(r.getString(3));
            assertEquals("Old.", r.getString(4));
        }
    }

    @Test
    void v9_adds_an_empty_cost_table_and_keeps_old_sessions_as_they_were() throws Exception {
        String url = newDb();
        migrateTo(url, "8");
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute("INSERT INTO sessions (id, project_path, start_ref, status, started_at, reason, summary) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', '/p', 'r', 'COMPLETE', CURRENT_TIMESTAMP, 'old goal', 'Old summary.')");
            s.execute("INSERT INTO changes (id, session_id, file_path, change_type, understood) VALUES "
                    + "('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-000000000001', 'a.js', 'MODIFIED', TRUE)");
            s.execute("INSERT INTO teach_back_attempts (id, change_id, answer, verdict, created_at) VALUES "
                    + "('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-00000000000a', 'x', 'PASS', CURRENT_TIMESTAMP)");
        }

        migrateTo(url, "9");

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT reason, summary FROM sessions")) {
                assertTrue(r.next());
                assertEquals("old goal", r.getString(1));
                assertEquals("Old summary.", r.getString(2));
                assertFalse(r.next());
            }
            try (ResultSet r = s.executeQuery(
                    "SELECT (SELECT COUNT(*) FROM changes), (SELECT COUNT(*) FROM teach_back_attempts), (SELECT COUNT(*) FROM ai_calls)")) {
                assertTrue(r.next());
                assertEquals(1, r.getInt(1));
                assertEquals(1, r.getInt(2));
                assertEquals(0, r.getInt(3), "old sessions have no cost rows, so their cost stays hidden");
            }
            // New rows can point at an old session.
            s.execute("INSERT INTO ai_calls (id, session_id, job, model, input_tokens, output_tokens, "
                    + "cache_read_tokens, cache_write_tokens, created_at) VALUES ('00000000-0000-0000-0000-0000000000c1', "
                    + "'00000000-0000-0000-0000-000000000001', 'EXPLAIN', 'claude-sonnet-5-5', 1, 1, 0, 0, CURRENT_TIMESTAMP)");
        }
    }

    @Test
    void v10_adds_an_empty_concept_table_and_old_changes_have_no_tags() throws Exception {
        String url = newDb();
        migrateTo(url, "9");
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute("INSERT INTO sessions (id, project_path, start_ref, status, started_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', '/p', 'r', 'COMPLETE', CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO changes (id, session_id, file_path, change_type) VALUES "
                    + "('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-000000000001', 'a.js', 'MODIFIED')");
        }

        migrateTo(url, "10");

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT (SELECT COUNT(*) FROM changes), (SELECT COUNT(*) FROM change_concepts)")) {
                assertTrue(r.next());
                assertEquals(1, r.getInt(1));
                assertEquals(0, r.getInt(2));
            }
            s.execute("INSERT INTO change_concepts (id, change_id, name, other) VALUES ('00000000-0000-0000-0000-0000000000c1', "
                    + "'00000000-0000-0000-0000-00000000000a', 'Loops', FALSE)");
        }
    }

    @Test
    void v11_puts_changes_already_understood_in_the_review_queue_due_tomorrow() throws Exception {
        String url = newDb();
        migrateTo(url, "10");
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute("INSERT INTO sessions (id, project_path, start_ref, status, started_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', '/p', 'r', 'COMPLETE', CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO changes (id, session_id, file_path, change_type, understood, understood_by) VALUES "
                    + "('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-000000000001', 'done.js', 'MODIFIED', TRUE, 'SELF'), "
                    + "('00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-000000000001', 'todo.js', 'MODIFIED', FALSE, NULL)");
        }

        migrateTo(url, "11");

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT change_id, step, DATEDIFF('HOUR', CURRENT_TIMESTAMP, due_at), done_at "
                     + "FROM review_items")) {
            assertTrue(r.next());
            assertEquals("00000000-0000-0000-0000-00000000000a", r.getString(1));
            assertEquals(0, r.getInt(2));
            assertTrue(r.getInt(3) >= 23 && r.getInt(3) <= 24, "due in about a day: " + r.getInt(3) + "h");
            assertNull(r.getTimestamp(4));
            assertFalse(r.next(), "only the understood change");
        }
    }
}
