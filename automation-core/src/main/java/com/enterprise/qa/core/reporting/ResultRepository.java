package com.enterprise.qa.core.reporting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

/**
 * JDBC access to the quality-metrics database. Uses portable SQL (upserts via ON CONFLICT)
 * so the same code works on SQLite and PostgreSQL.
 */
public class ResultRepository implements AutoCloseable {

    private final Connection connection;

    public ResultRepository(String jdbcUrl) throws SQLException {
        prepareSqliteFile(jdbcUrl);
        this.connection = DriverManager.getConnection(jdbcUrl);
        if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
        }
        applySchema();
    }

    public long upsertProject(ProjectInfo project) throws SQLException {
        String sql = """
                INSERT INTO project (project_key, name, type, description, created_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (project_key) DO UPDATE SET name = excluded.name, type = excluded.type,
                    description = excluded.description
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, project.key());
            ps.setString(2, project.name());
            ps.setString(3, project.type());
            ps.setString(4, project.description());
            ps.setString(5, now());
            ps.executeUpdate();
        }
        return queryId("SELECT id FROM project WHERE project_key = ?", project.key());
    }

    /**
     * Inserts or refreshes a test case. {@code is_automated} only ever flips from 0 to 1 here:
     * once a case has been executed by automation it stays automated even if a catalog row lags behind.
     */
    public long upsertTestCase(long projectId, TestCaseInfo tc) throws SQLException {
        String sql = """
                INSERT INTO test_case (project_id, tc_key, title, is_regression, is_sanity, is_new_feature,
                                       is_automated, priority, testlink_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (project_id, tc_key) DO UPDATE SET
                    title = excluded.title,
                    is_regression = excluded.is_regression,
                    is_sanity = excluded.is_sanity,
                    is_new_feature = excluded.is_new_feature,
                    is_automated = CASE WHEN excluded.is_automated = 1 THEN 1 ELSE test_case.is_automated END,
                    priority = COALESCE(excluded.priority, test_case.priority),
                    testlink_id = COALESCE(excluded.testlink_id, test_case.testlink_id),
                    updated_at = excluded.updated_at
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setString(2, tc.key());
            ps.setString(3, tc.title());
            ps.setInt(4, tc.regression() || tc.sanity() ? 1 : 0);
            ps.setInt(5, tc.sanity() ? 1 : 0);
            ps.setInt(6, tc.newFeature() ? 1 : 0);
            ps.setInt(7, tc.automated() ? 1 : 0);
            ps.setString(8, tc.priority());
            ps.setString(9, tc.testlinkId());
            ps.setString(10, now());
            ps.executeUpdate();
        }
        return queryId("SELECT id FROM test_case WHERE project_id = ? AND tc_key = ?", projectId, tc.key());
    }

    public long createRun(long projectId, RunInfo run) throws SQLException {
        String sql = """
                INSERT INTO test_run (project_id, status, started_at, environment, trigger_source, build_number,
                                      tag_filter, engine, executed_by)
                VALUES (?, 'RUNNING', ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, projectId);
            ps.setString(2, run.startedAt().toString());
            ps.setString(3, run.environment());
            ps.setString(4, run.triggerSource());
            ps.setString(5, run.buildNumber());
            ps.setString(6, run.tagFilter());
            ps.setString(7, run.engine());
            ps.setString(8, run.executedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void insertResult(long runId, Long testCaseId, String scenario, String featureFile, String status,
                             long durationMs, String error) throws SQLException {
        String sql = """
                INSERT INTO test_result (run_id, test_case_id, scenario_name, feature_file, status, duration_ms,
                                         error_message, executed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, runId);
            if (testCaseId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setLong(2, testCaseId);
            }
            ps.setString(3, scenario);
            ps.setString(4, featureFile);
            ps.setString(5, status);
            ps.setLong(6, durationMs);
            ps.setString(7, error);
            ps.setString(8, now());
            ps.executeUpdate();
        }
    }

    /** Closes a run, deriving its totals from the stored results so the numbers can never drift. */
    public void finishRun(long runId, Instant finishedAt, long durationMs) throws SQLException {
        String sql = """
                UPDATE test_run SET status = 'COMPLETED', finished_at = ?, duration_ms = ?,
                    total   = (SELECT COUNT(*) FROM test_result WHERE run_id = ?),
                    passed  = (SELECT COUNT(*) FROM test_result WHERE run_id = ? AND status = 'PASSED'),
                    failed  = (SELECT COUNT(*) FROM test_result WHERE run_id = ? AND status = 'FAILED'),
                    pending = (SELECT COUNT(*) FROM test_result WHERE run_id = ? AND status = 'PENDING')
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, finishedAt.toString());
            ps.setLong(2, durationMs);
            for (int i = 3; i <= 6; i++) {
                ps.setLong(i, runId);
            }
            ps.setLong(7, runId);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private long queryId(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No row for: " + sql);
                }
                return rs.getLong(1);
            }
        }
    }

    private void applySchema() throws SQLException {
        String schema;
        try (InputStream in = ResultRepository.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                throw new SQLException("db/schema.sql not found on classpath");
            }
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Cannot read schema", e);
        }
        String withoutComments = schema.replaceAll("(?m)--.*$", "");
        try (Statement st = connection.createStatement()) {
            for (String ddl : withoutComments.split(";")) {
                if (!ddl.isBlank()) {
                    st.execute(ddl);
                }
            }
        }
    }

    private static void prepareSqliteFile(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:sqlite:")) {
            return;
        }
        Path parent = Path.of(jdbcUrl.substring("jdbc:sqlite:".length())).toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create results directory " + parent, e);
        }
    }

    private static String now() {
        return Instant.now().toString();
    }
}
