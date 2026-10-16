// Quality Metrics Dashboard - zero-dependency Node.js server (Node >= 22.13 for node:sqlite).
// Reads the same database the automation framework writes to.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { readFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';

const here = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT ?? 3000);
const DB_PATH = resolve(process.env.DB_PATH ?? join(here, '..', 'results', 'automation-results.db'));
const SCHEMA = join(here, '..', 'automation-core', 'src', 'main', 'resources', 'db', 'schema.sql');
const PUBLIC = join(here, 'public');

mkdirSync(dirname(DB_PATH), { recursive: true });
const db = new DatabaseSync(DB_PATH);
db.exec('PRAGMA busy_timeout = 5000');
db.exec('PRAGMA journal_mode = WAL');
if (existsSync(SCHEMA)) {
  db.exec(readFileSync(SCHEMA, 'utf8'));
}

const q = (sql, ...params) => db.prepare(sql).all(...params);

function dashboardData() {
  const projects = q(`SELECT id, project_key, name, type, description FROM project ORDER BY type, name`);

  const testCases = q(`
    SELECT tc.id, tc.project_id, tc.tc_key, tc.title, tc.is_regression, tc.is_sanity, tc.is_new_feature,
           tc.is_automated, tc.priority, tc.testlink_id,
           last.status AS last_status, last.duration_ms AS last_duration_ms,
           last.executed_at AS last_executed_at, last.error_message AS last_error, last.run_id AS last_run_id
    FROM test_case tc
    LEFT JOIN test_result last ON last.id = (
        SELECT r.id FROM test_result r WHERE r.test_case_id = tc.id ORDER BY r.id DESC LIMIT 1)
    ORDER BY tc.project_id, tc.tc_key`);

  const runs = q(`
    SELECT r.id, r.project_id, r.status, r.started_at, r.finished_at, r.duration_ms, r.environment,
           r.trigger_source, r.build_number, r.tag_filter, r.engine, r.executed_by,
           r.total, r.passed, r.failed, r.pending
    FROM test_run r
    WHERE r.status = 'COMPLETED'
      AND r.id IN (SELECT id FROM test_run x WHERE x.project_id = r.project_id AND x.status = 'COMPLETED'
                   ORDER BY x.id DESC LIMIT 30)
    ORDER BY r.started_at`);

  // Per-result rows of each project's latest completed run, so the client can slice by category.
  const latestResults = q(`
    SELECT res.run_id, run.project_id, res.test_case_id, res.status
    FROM test_result res JOIN test_run run ON run.id = res.run_id
    WHERE run.id IN (SELECT MAX(id) FROM test_run WHERE status = 'COMPLETED' GROUP BY project_id)`);

  return { generatedAt: new Date().toISOString(), database: DB_PATH, projects, testCases, runs, latestResults };
}

function runResults(runId) {
  return q(`
    SELECT res.id, res.scenario_name, res.feature_file, res.status, res.duration_ms, res.error_message,
           res.executed_at, tc.tc_key
    FROM test_result res LEFT JOIN test_case tc ON tc.id = res.test_case_id
    WHERE res.run_id = ? ORDER BY tc.tc_key, res.id`, runId);
}

const MIME = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml' };

function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  try {
    if (url.pathname === '/api/dashboard') {
      return sendJson(res, 200, dashboardData());
    }
    const runMatch = url.pathname.match(/^\/api\/runs\/(\d+)\/results$/);
    if (runMatch) {
      return sendJson(res, 200, runResults(Number(runMatch[1])));
    }
    if (url.pathname === '/api/health') {
      return sendJson(res, 200, { status: 'UP', database: DB_PATH });
    }
    const file = url.pathname === '/' ? 'index.html' : url.pathname.slice(1);
    const path = resolve(PUBLIC, file);
    if (!path.startsWith(PUBLIC)) {
      return sendJson(res, 403, { error: 'forbidden' });
    }
    const body = await readFile(path);
    res.writeHead(200, { 'Content-Type': MIME[extname(path)] ?? 'application/octet-stream' });
    res.end(body);
  } catch (err) {
    if (err.code === 'ENOENT') {
      return sendJson(res, 404, { error: 'not found' });
    }
    console.error(err);
    sendJson(res, 500, { error: err.message });
  }
});

server.listen(PORT, () => {
  console.log(`Quality dashboard: http://localhost:${PORT}`);
  console.log(`Database:          ${DB_PATH}`);
});
