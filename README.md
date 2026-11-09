# Enterprise Automation Framework

One BDD framework (Java 21 · Cucumber/Gherkin · JUnit 5) for **API**, **Web** and **Mobile (Android/iOS)**
automation, plus a **Quality Metrics Dashboard** that every automation project reports into.

| Phase 1 deliverable | Where | Status |
|---|---|---|
| Framework core (config, drivers, API client, reporting, TestDesk) | `automation-core/` | ✅ |
| API pilot - 10 contract tests on [JSONPlaceholder](https://jsonplaceholder.typicode.com) | `api-tests/` | ✅ 10/10 passing |
| Web pilot - 10 UI tests on [Sauce Demo](https://www.saucedemo.com) | `web-tests/` | ✅ 10/10 passing (Playwright and Selenium) |
| Quality Metrics Dashboard | `dashboard/` | ✅ http://localhost:3000 |
| Jenkins pipeline | `Jenkinsfile` | ✅ provided (not run against a live Jenkins yet) |
| Android pilot - 10 UI tests on the [Swag Labs sample app](https://github.com/saucelabs/sample-app-mobile) (phase 2.1) | `android-tests/` | ✅ 10/10 passing on a headless emulator; windowed emulator and real device supported, see [docs/android-setup.md](docs/android-setup.md) |

```
┌────────────┐   ┌────────────┐   ┌───────────────┐
│ api-tests  │   │ web-tests  │   │ android-tests │
│ .feature   │   │ .feature   │   │ .feature      │
└─────┬──────┘   └─────┬──────┘   └───────┬───────┘
      └───────── automation-core ─────────┘
        Config · ApiClient · UiDriver (Playwright | Selenium)
        Mobile: emulator / headless / real device + Appium (AndroidRuntime, MobileSession)
        DbResultPublisher ──► results DB (SQLite / JDBC) ◄── dashboard (Node.js)
        TestDeskResultListener ──► TestDesk: test cases, results, evidence (optional)
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

### Web run modes: headless or Google Chrome

| Mode | Command | What you get |
|---|---|---|
| **Headless** (default) | `./mvnw -pl web-tests test` *(same as `-Pheadless`)* | No window, bundled Chromium. Use for CI and fast local runs |
| **Google Chrome** | `./mvnw -pl web-tests test -Pchrome` | A visible window of your installed Google Chrome, so you can watch each scenario |

Modes combine with every other switch, e.g. `./mvnw -pl web-tests test -Pchrome -Dcucumber.filter.tags=@sanity`
or `-Pchrome -Dweb.engine=selenium`. Chrome mode needs Google Chrome installed. Scenarios run 3 at a time, so expect
up to 3 Chrome windows. The dashboard's run history labels each run `(headless)` or `(headed)`.

### Useful switches (all are `-Dkey=value`; most can also be set as an env var, e.g. `TEST_ENV=staging`)

| Switch | Default | Purpose |
|---|---|---|
| `cucumber.filter.tags` | *(all)* | `@sanity`, `@regression`, `@newfeature`, `@tc:WEB-007`, `"@regression and not @wip"` |
| `env` | `local` | Loads `config/<env>.properties` over `config/default.properties` |
| `web.engine` | `playwright` | `playwright` or `selenium` (same page objects, same features) |
| `browser` | set by run mode | Overrides the mode's browser: `chromium`, `chrome`, `firefox`, `webkit` (Playwright), `edge` |
| `headless` | set by run mode | Overrides the mode's headless flag (`true` / `false`) |
| `selenium.grid.url` | - | Run Selenium on a Grid instead of a local browser |
| `results.db.url` | `jdbc:sqlite:<repo>/results/automation-results.db` | Where results are published |
| `results.db.enabled` | `true` | `false` for throw-away runs that shouldn't affect metrics |
| `testdesk.enabled` | `false` | Send test cases, results and evidence to TestDesk (see below) |
| `evidence.mode` | `failed` | Evidence attached to scenarios and sent to TestDesk: `none`, `failed`, `all` |

Examples:

```bash
./mvnw -pl web-tests test -Dcucumber.filter.tags=@sanity
./mvnw -pl web-tests test -Dbrowser=firefox
```

Every run also produces Cucumber HTML/JSON/JUnit reports in `<module>/target/cucumber-reports/`
(failed web scenarios have a screenshot attached; failed API scenarios have the last response attached).

---

## Conventions every automation project follows

**Tags = the link between a scenario, TestDesk and the dashboard.**

```gherkin
@tc:WEB-009 @regression @sanity @priority:high
Scenario: Complete a purchase end to end
```

| Tag | Meaning |
|---|---|
| `@tc:<ID>` | Test case ID; must match the catalog and the TestDesk `case_key` (TestDesk project prefix `API`, `WEB`, `AND`). **Required.** |
| `@regression` | Part of the regression suite |
| `@sanity` | Part of the sanity suite. **Sanity is a subset of regression**, so `@sanity` implies regression |
| `@newfeature` | Covers a new feature (not yet in regression) |
| `@priority:<critical/high/medium/low>` | Optional; sent to TestDesk as the priority |
| `@req:<ref>` | Optional requirement reference (BRD/FSD), sent to TestDesk as `requirement_ref` |
| `@defect:<ref>` | Optional defect reference, sent with the TestDesk result as `defect_ref` |

**Test case catalog** (`src/test/resources/testcases/catalog.csv`): the project's full inventory, manual *and*
automated, kept in the repository. It provides the denominator for automation coverage. Each run syncs it to
the DB, and any scenario that executes is marked automated.

```csv
tc_key,title,categories,automated,priority
WEB-011,Logout from the side menu returns to login,regression|sanity,false,high
```

Columns are read by position. Older catalogs have a sixth, deprecated legacy-ID column; it's still accepted (and stored in
the DB) but is not used by TestDesk and can be left empty or dropped.

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

`Jenkinsfile` is a parameterised pipeline: `SUITE` (all/api/web/android), `TAGS`, `TEST_ENV`, `WEB_ENGINE`, `RUN_MODE` (headless/chrome), `ANDROID_MODE`, `ANDROID_APP`,
`RESULTS_DB_URL`, `TESTDESK`, `TESTDESK_ENV` (SIT/UAT), `TESTDESK_PLAN`. It builds the core, runs the API and Web suites **in parallel**, publishes JUnit results, archives the Cucumber reports,
and runs a nightly regression (`H 2 * * 1-5`). It works on Linux (`sh`) and Windows (`bat`) agents.

Setup: create a Pipeline job from SCM pointing at this repo, add a JDK 21 tool named `jdk21`, and set `RESULTS_DB_URL`
to the database the dashboard reads (for SQLite on the same machine, e.g. `jdbc:sqlite:C:/qa/automation-results.db`, then start the
dashboard with `DB_PATH=C:/qa/automation-results.db`). Runs record `trigger_source=JENKINS` and the build number.

To report to TestDesk from Jenkins: add a *Secret text* credential with ID `TESTDESK_TOKEN` (the pipeline binds it to the env var
`TESTDESK_TOKEN`), set `TESTDESK_URL` in the Jenkins global or agent environment, then tick `TESTDESK`. The job passes
`-Dtestdesk.enabled=true -Dtestdesk.environment=<TESTDESK_ENV>` and, when `TESTDESK_PLAN` is filled in, `-Dtestdesk.plan.name=<TESTDESK_PLAN>`.

## TestDesk

TestDesk is the system of record for test cases, execution results and **test evidence** (SIT/UAT). `TestDeskResultListener`
(registered as a Cucumber plugin in each module's `junit-platform.properties`) collects every scenario and, at the end of the run:

1. syncs the specification (title, tags, priority, Gherkin steps) to `PUT /api/projects/{prefix}/automation/cases`;
   test cases that don't exist in TestDesk yet are created in a suite named after the feature (`testdesk.auto.create`);
2. sends the results to `POST /api/projects/{prefix}/automation/results` (passed, failed, blocked for undefined/pending, skipped);
3. uploads evidence per test case (screenshots, API request/response) according to `evidence.mode`;
4. logs a summary with a link to the test plan.

Results are sent once per run, so parallel execution is safe. Scenarios without `@tc` are skipped with a warning.

```properties
testdesk.enabled=true
testdesk.url=http://<testdesk-host>:3100     # or env TESTDESK_URL
testdesk.project=API                         # TestDesk project prefix
testdesk.environment=SIT                     # SIT or UAT
# token: set env var TESTDESK_TOKEN (never commit it)
```

| Key | Default | Purpose |
|---|---|---|
| `testdesk.enabled` | `false` | Master switch |
| `testdesk.url` | - | TestDesk base URL, e.g. `http://localhost:3100` |
| `testdesk.token` | env `TESTDESK_TOKEN` | API token; only ever from the environment |
| `testdesk.project` | - | TestDesk project prefix (`API`, `WEB`, `AND`) |
| `testdesk.environment` | `SIT` | `SIT` or `UAT`, used when the plan is created |
| `testdesk.plan.name` | `EAF <project.key> <env>` | Rolling plan name; set per build for a release, e.g. `Release 2.4 #123` |
| `testdesk.run.id` | `BUILD_TAG`, or `local-<user>-<time>` | Idempotency key: re-sending the same run doesn't duplicate results |
| `testdesk.build` / `testdesk.ci.url` | `BUILD_NUMBER` / `BUILD_URL` | Shown on each execution |
| `testdesk.suite.root` | *(empty)* | Optional folder above the module suites |
| `testdesk.auto.create` | `true` | Create test cases missing in TestDesk; `false` only reports them. Needs `testdesk.sync.spec=true` for steps |
| `testdesk.sync.spec` | `true` | `false` when titles and steps are maintained by hand in TestDesk |
| `testdesk.dry.run` | `false` | Passes `dry_run` to the API: see the outcome without saving |
| `evidence.mode` | `failed` | `none`, `failed`, `all` (UAT usually wants `all`) |
| `testdesk.evidence.max.mb` | `20` | Larger files are skipped with a warning (TestDesk limit: 25 MB) |

**TestDesk problems never fail the test run.** They are logged as warnings:

| Situation | Behaviour |
|---|---|
| TestDesk down, token wrong or expired (401) | Warning; the run passes/fails on its test results; payloads are saved in `<module>/target/testdesk/` |
| Test case not in TestDesk and `testdesk.auto.create=false` | Reported in the log; import the catalog or turn auto-create on |
| Plan already closed (409) | Warning; use a new plan name (`testdesk.plan.name`) |
| Scenario without `@tc` | Skipped with a warning |
| Results re-sent with the same run ID | Not duplicated |
| Evidence upload fails | Warning per test case; the result is already recorded |

A saved payload can be re-sent by hand, e.g.
`curl -X POST -H "Authorization: Bearer $TESTDESK_TOKEN" -H "Content-Type: application/json" --data @api-tests/target/testdesk/results-<run>.json $TESTDESK_URL/api/projects/API/automation/results`.

Design and decisions: [docs/design-testdesk-integration.md](docs/design-testdesk-integration.md).

## Android (phase 2.1)

`automation-core` runs Android tests on an **emulator**, a **headless emulator** or a **real device**, and starts what is
needed (Appium, the emulator) for you. Pick the target with a Maven profile:

```bash
.\mvnw.cmd -pl android-tests test                 # emulator with a window (default)
.\mvnw.cmd -pl android-tests test -Pheadless      # emulator without a window (CI)
.\mvnw.cmd -pl android-tests test -Pdevice        # physical phone over USB / Wi-Fi
```

Prerequisites per OS (Windows, macOS, Linux), creating an AVD, connecting a phone, CI, running from IntelliJ and every setting
are in **[docs/android-setup.md](docs/android-setup.md)**. `AndroidDoctor` checks your machine and says what to fix.
`MobileDriverFactory` still builds iOS (XCUITest) drivers; an iOS pilot is not part of this phase.

## Adding a new automation project

1. Copy `api-tests/` or `web-tests/`, rename the artifactId, and add it to `<modules>` in the root `pom.xml`.
2. Set `project.key/name/type` in `config/default.properties`.
3. Replace `testcases/catalog.csv` with the project's test case inventory, set `testdesk.project` to its TestDesk prefix, and write features tagged `@tc:<ID>` plus category tags.
4. Run it. The project appears on the dashboard automatically.

## Repository layout

```
automation-core/   framework library (config, web/mobile/api drivers, reporting, TestDesk, reusable steps)
api-tests/         API pilot: features/, contracts/ (JSON Schema), testcases/catalog.csv
web-tests/         Web pilot: features/, pages/ (page objects), steps/, testcases/catalog.csv
android-tests/     Android pilot: features/, screens/ (screen objects), steps/, testcases/catalog.csv
dashboard/         Node.js server (no npm dependencies) + static HTML/CSS/JS front end
results/           SQLite results DB (git-ignored)
Jenkinsfile        CI orchestration
```
