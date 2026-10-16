-- Quality metrics store shared by the test framework (writer) and the dashboard (reader).
-- Plain SQL that runs on SQLite (default) and PostgreSQL (swap AUTOINCREMENT for SERIAL/IDENTITY).

CREATE TABLE IF NOT EXISTS project (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    project_key  TEXT NOT NULL UNIQUE,
    name         TEXT NOT NULL,
    type         TEXT NOT NULL,               -- API | WEB | MOBILE
    description  TEXT,
    created_at   TEXT NOT NULL
);

-- Every test case known to test management (TestLink), automated or not.
-- Sanity is a subset of regression: is_sanity = 1 implies is_regression = 1.
CREATE TABLE IF NOT EXISTS test_case (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL REFERENCES project(id),
    tc_key          TEXT NOT NULL,            -- e.g. API-001, matches @tc:API-001 in feature files
    title           TEXT NOT NULL,
    is_regression   INTEGER NOT NULL DEFAULT 0,
    is_sanity       INTEGER NOT NULL DEFAULT 0,
    is_new_feature  INTEGER NOT NULL DEFAULT 0,
    is_automated    INTEGER NOT NULL DEFAULT 0,
    priority        TEXT,
    testlink_id     TEXT,
    updated_at      TEXT NOT NULL,
    UNIQUE (project_id, tc_key)
);

CREATE TABLE IF NOT EXISTS test_run (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL REFERENCES project(id),
    status          TEXT NOT NULL,            -- RUNNING | COMPLETED
    started_at      TEXT NOT NULL,
    finished_at     TEXT,
    duration_ms     INTEGER,
    environment     TEXT,
    trigger_source  TEXT,                     -- LOCAL | JENKINS
    build_number    TEXT,
    tag_filter      TEXT,
    engine          TEXT,                     -- e.g. playwright/chromium, rest-assured
    executed_by     TEXT,
    total           INTEGER NOT NULL DEFAULT 0,
    passed          INTEGER NOT NULL DEFAULT 0,
    failed          INTEGER NOT NULL DEFAULT 0,
    pending         INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS test_result (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id          INTEGER NOT NULL REFERENCES test_run(id),
    test_case_id    INTEGER REFERENCES test_case(id),
    scenario_name   TEXT NOT NULL,
    feature_file    TEXT,
    status          TEXT NOT NULL,            -- PASSED | FAILED | PENDING
    duration_ms     INTEGER,
    error_message   TEXT,
    executed_at     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_test_case_project ON test_case(project_id);
CREATE INDEX IF NOT EXISTS idx_test_run_project ON test_run(project_id, started_at);
CREATE INDEX IF NOT EXISTS idx_test_result_run ON test_result(run_id);
CREATE INDEX IF NOT EXISTS idx_test_result_case ON test_result(test_case_id);
