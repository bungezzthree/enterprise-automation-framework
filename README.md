# Enterprise Automation Framework

One BDD framework (Java 21 · Cucumber/Gherkin · JUnit 5) for **API**, **Web** and **Mobile (Android/iOS)**
automation, plus a **Quality Metrics Dashboard** that every automation project reports into.

| Phase 1 deliverable | Where | Status |
|---|---|---|
| Framework core (config, drivers, API client, reporting, TestLink) | `automation-core/` | ✅ |
| API pilot - 10 contract tests on [JSONPlaceholder](https://jsonplaceholder.typicode.com) | `api-tests/` | ✅ 10/10 passing |
| Web pilot - 10 UI tests on [Sauce Demo](https://www.saucedemo.com) | `web-tests/` | ✅ 10/10 passing (Playwright and Selenium) |
| Quality Metrics Dashboard | `dashboard/` | ✅ http://localhost:3000 |
| Jenkins pipeline | `Jenkinsfile` | ✅ provided (not run against a live Jenkins yet) |
| Mobile (Appium) | `automation-core/.../mobile` | Driver factory ready; pilot is phase 2 |

```
┌────────────┐   ┌────────────┐   ┌──────────────┐
│ api-tests  │   │ web-tests  │   │ mobile-tests │  (phase 2)
│ .feature   │   │ .feature   │   │ .feature     │
└─────┬──────┘   └─────┬──────┘   └──────┬───────┘
      └───────── automation-core ────────┘
        Config · ApiClient · UiDriver (Playwright | Selenium) · MobileDriverFactory (Appium)
        DbResultPublisher ──► results DB (SQLite / JDBC) ◄── dashboard (Node.js)
        TestLinkResultListener ──► TestLink (optional)
```

---

## Quick start (local)

Prerequisites: **JDK 21** and **Node.js ≥ 22.13**. Maven doesn't need to be installed because the wrapper (`mvnw`) downloads it.
Internet access is needed for the public test targets and the first dependency/browser download.

```bash
# 1. Build the framework once
./mvnw install -DskipTests

# 2. Run the pilots (each run is written to results/automation-results.db)
./mvnw -pl api-tests test
./mvnw -pl web-tests test

# 3. Start the dashboard, then open http://localhost:3000
node dashboard/server.js
```

On Windows use `mvnw.cmd`. If `JAVA_HOME` isn't set, point it at a JDK 21 first, e.g.
`set JAVA_HOME=%USERPROFILE%\.jdks\ms-21.0.11`.

The dashboard auto-refreshes every 30 s, so you can leave it open while suites run.

### Useful switches (all are `-Dkey=value`, or an env var such as `BROWSER=firefox`)

| Switch | Default | Purpose |
|---|---|---|
| `cucumber.filter.tags` | *(all)* | `@sanity`, `@regression`, `@newfeature`, `@tc:WEB-007`, `"@regression and not @wip"` |
| `env` | `local` | Loads `config/<env>.properties` over `config/default.properties` |
| `web.engine` | `playwright` | `playwright` or `selenium` (same page objects, same features) |
| `browser` | `chromium` | `chromium`, `chrome`, `firefox`, `webkit` (Playwright), `edge` |
| `headless` | `true` | `false` to watch the browser |
| `selenium.grid.url` | - | Run Selenium on a Grid instead of a local browser |
| `results.db.url` | `jdbc:sqlite:<repo>/results/automation-results.db` | Where results are published |
| `results.db.enabled` | `true` | `false` for throw-away runs that shouldn't affect metrics |
| `testlink.enabled` | `false` | Push results to TestLink (see below) |

Examples:

```bash
./mvnw -pl web-tests test -Dcucumber.filter.tags=@sanity
./mvnw -pl web-tests test -Dweb.engine=selenium -Dbrowser=chrome -Dheadless=false
```

Every run also produces Cucumber HTML/JSON/JUnit reports in `<module>/target/cucumber-reports/`
(failed web scenarios have a screenshot attached; failed API scenarios have the last response attached).

---

## Conventions every automation project follows

**Tags = the link between a scenario, TestLink and the dashboard.**

```gherkin
@tc:WEB-009 @regression @sanity @priority:high
Scenario: Complete a purchase end to end
```

| Tag | Meaning |
|---|---|
| `@tc:<ID>` | Test case ID; must match the catalog / TestLink external ID. **Required.** |
| `@regression` | Part of the regression suite |
| `@sanity` | Part of the sanity suite. **Sanity is a subset of regression**, so `@sanity` implies regression |
| `@newfeature` | Covers a new feature (not yet in regression) |
| `@priority:<high/medium/low>` | Optional |
| `@testlink:<externalId>` | Optional override when the TestLink ID differs from `@tc` |

**Test case catalog** (`src/test/resources/testcases/catalog.csv`): the project's full inventory, manual *and*
automated, as exported from TestLink. It provides the denominator for automation coverage. Each run syncs it to
the DB, and any scenario that executes is marked automated.

```csv
tc_key,title,categories,automated,priority,testlink_id
WEB-011,Logout from the side menu returns to login,regression|sanity,false,high,EAF-WEB-11
```

**Project identity** (`src/test/resources/config/default.properties`): `project.key`, `project.name`,
`project.type` (API / WEB / MOBILE). This is what the dashboard groups by.

---

## Dashboard metrics

| Metric | Definition |
|---|---|
| Test cases | All catalog test cases for the selected project(s) and category |
| Automated | Test cases flagged automated (in the catalog, or executed by automation) |
| Automation coverage | Automated ÷ Test cases |
| Success rate | Passed ÷ executed, in each project's **latest** completed run |
| Passed / Failed / Pending | Scenario results from the latest run. *Pending* = Cucumber pending, undefined or skipped |
| Category | Regression, Sanity (⊂ Regression), New feature, which filter every widget |

The dashboard also shows a success-rate trend for the last 30 runs per project, a test-case list with each case's last result
and error, and a run history you can drill into scenario by scenario.

API: `GET /api/dashboard` (all metrics data), `GET /api/runs/{id}/results`, `GET /api/health`.

### Database

Default: a single SQLite file at `results/automation-results.db` (no server to install). The framework writes it; the dashboard
reads it. The schema lives in `automation-core/src/main/resources/db/schema.sql` and both sides create it if it's missing.
Tables: `project`, `test_case`, `test_run`, `test_result`.

For a shared, multi-agent setup, move to PostgreSQL: the repository uses portable SQL (`ON CONFLICT` upserts). You'd add the
`org.postgresql:postgresql` driver, change `AUTOINCREMENT` to `GENERATED BY DEFAULT AS IDENTITY` in the schema, and swap the
dashboard's `node:sqlite` for `pg`. *(Not done or tested in phase 1.)*

---

## Jenkins

`Jenkinsfile` is a parameterised pipeline: `SUITE` (all/api/web), `TAGS`, `TEST_ENV`, `WEB_ENGINE`, `BROWSER`, `RESULTS_DB_URL`,
`TESTLINK`. It builds the core, runs the API and Web suites **in parallel**, publishes JUnit results, archives the Cucumber reports,
and runs a nightly regression (`H 2 * * 1-5`). It works on Linux (`sh`) and Windows (`bat`) agents.

Setup: create a Pipeline job from SCM pointing at this repo, add a JDK 21 tool named `jdk21`, and set `RESULTS_DB_URL`
to the database the dashboard reads (for SQLite on the same machine, e.g. `jdbc:sqlite:C:/qa/automation-results.db`, then start the
dashboard with `DB_PATH=C:/qa/automation-results.db`). Runs record `trigger_source=JENKINS` and the build number.

## TestLink

Two integration points:

1. **Inventory → dashboard:** export each test suite from TestLink and keep it as `testcases/catalog.csv` (see format above).
2. **Results → TestLink:** `TestLinkResultListener` calls `tl.reportTCResult` over XML-RPC after each scenario
   (passed → `p`, failed → `f`, pending → `b` blocked).

```properties
testlink.enabled=true
testlink.url=http://<testlink-host>/lib/api/xmlrpc/v1/xmlrpc.php
testlink.testplan.id=<plan id>
testlink.build=<build name>
# devkey: set env var TESTLINK_DEVKEY (never commit it)
```

> The XML-RPC client follows TestLink 1.9.x's documented API but hasn't been run against a live TestLink instance yet.
> Verify it in your environment before relying on it.

## Mobile (phase 2)

`MobileDriverFactory` already builds Appium 2 drivers (`UiAutomator2` for Android, `XCUITest` for iOS) from config:
`mobile.platform`, `appium.server.url`, `mobile.device.name`, `mobile.platform.version`, `mobile.app`, `mobile.udid`.
A mobile pilot becomes a new module shaped like `web-tests` with `project.type=MOBILE`, and the dashboard picks it up with no changes.

## Adding a new automation project

1. Copy `api-tests/` or `web-tests/`, rename the artifactId, and add it to `<modules>` in the root `pom.xml`.
2. Set `project.key/name/type` in `config/default.properties`.
3. Replace `testcases/catalog.csv` with the TestLink export and write features tagged `@tc:<ID>` plus category tags.
4. Run it. The project appears on the dashboard automatically.

## Repository layout

```
automation-core/   framework library (config, web/mobile/api drivers, reporting, TestLink, reusable steps)
api-tests/         API pilot: features/, contracts/ (JSON Schema), testcases/catalog.csv
web-tests/         Web pilot: features/, pages/ (page objects), steps/, testcases/catalog.csv
dashboard/         Node.js server (no npm dependencies) + static HTML/CSS/JS front end
results/           SQLite results DB (git-ignored)
Jenkinsfile        CI orchestration
```
