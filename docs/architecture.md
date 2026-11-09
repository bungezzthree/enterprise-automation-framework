# Architecture: Enterprise Automation Framework + Quality Metrics Dashboard

This document describes how the framework, the automation projects, the Quality Metrics Dashboard and the
surrounding systems (Jenkins, TestDesk, the applications under test) fit together. For installation steps see
**[deployment-guide.md](deployment-guide.md)**.

All diagrams are [Mermaid](https://mermaid.js.org) and render directly on GitHub, GitLab and in IntelliJ / VS Code
Markdown previews.

---

## 1. System context

Who and what the platform talks to.

```mermaid
flowchart LR
    qa(["👤 QA engineer<br/>writes features, runs suites locally"])
    lead(["👤 QA lead / PM / stakeholder<br/>reads quality metrics"])
    uat(["👤 UAT / SIT tester<br/>reviews evidence"])

    subgraph EAF["Enterprise Automation Framework"]
        direction TB
        suites["Automation projects<br/>api-tests · web-tests · android-tests"]
        core["automation-core<br/>(shared engine)"]
        db[("Quality metrics DB<br/>SQLite file")]
        dash["Quality Metrics Dashboard<br/>Node.js · :3000"]
        suites --> core
        core -- "writes results" --> db
        dash -- "reads" --> db
    end

    jenkins["Jenkins<br/>Jenkinsfile pipeline"]
    testdesk["TestDesk<br/>test management + evidence<br/>(optional, :3100)"]
    aut["Applications under test<br/>REST API · Web app · Android app"]
    git["Git repository"]

    qa -- "mvnw test" --> suites
    qa -- "push" --> git
    git -- "SCM checkout" --> jenkins
    jenkins -- "mvnw test (parallel)" --> suites
    core -- "HTTP / browser / Appium" --> aut
    core -- "spec + results + evidence<br/>REST, Bearer token" --> testdesk
    lead -- "browser" --> dash
    uat -- "browser" --> testdesk
```

| External system | Interaction | Required? |
|---|---|---|
| Applications under test | REST Assured (API), Playwright or Selenium (Web), Appium UiAutomator2 (Android) | Yes |
| Jenkins | Checks out the repo, runs `Jenkinsfile`, archives Cucumber reports, publishes JUnit | For CI |
| TestDesk | Receives Gherkin specs, results and evidence at the end of each run | Optional (`testdesk.enabled`) |
| Selenium Grid / Appium device farm | Remote browsers / devices | Optional (`selenium.grid.url`, `mobile.mode=remote`) |

---

## 2. Logical components

```mermaid
flowchart TB
    subgraph projects["Automation projects (Maven modules, one per application)"]
        direction LR
        api["<b>api-tests</b><br/>features/ · contracts/ (JSON Schema)<br/>testcases/catalog.csv"]
        web["<b>web-tests</b><br/>features/ · pages/ · steps/<br/>testcases/catalog.csv"]
        and["<b>android-tests</b><br/>features/ · screens/ · steps/<br/>testcases/catalog.csv"]
    end

    subgraph core["automation-core (Java 21 library)"]
        direction TB
        cfg["<b>Config</b><br/>-D props › env vars › &lt;env&gt;.properties › default.properties"]
        subgraph drivers["Drivers"]
            direction LR
            apic["<b>ApiClient</b><br/>REST Assured + Redaction"]
            ui["<b>UiDriverFactory</b><br/>PlaywrightUiDriver | SeleniumUiDriver"]
            mob["<b>MobileDriverFactory</b><br/>AndroidRuntime · EmulatorManager<br/>AppiumServerManager · Adb · AndroidDoctor"]
        end
        steps["<b>Reusable steps &amp; hooks</b><br/>CommonApiSteps · WebHooks · MobileHooks"]
        subgraph plugins["Cucumber plugins (reporting)"]
            direction LR
            pub["<b>DbResultPublisher</b><br/>ResultRepository (JDBC)<br/>TestCaseCatalog · RunInfo"]
            td["<b>TestDeskResultListener</b><br/>TestDeskReporter · TestDeskClient<br/>TestDeskPayloads"]
        end
    end

    subgraph runtime["Test runtime"]
        cuc["Cucumber 7 on JUnit 5 Platform<br/>(RunCucumberTest, Surefire)"]
    end

    subgraph dashboard["dashboard/ (Node.js ≥ 22.13, zero npm dependencies)"]
        direction TB
        srv["<b>server.js</b><br/>node:http + node:sqlite<br/>GET /api/dashboard · /api/runs/{id}/results · /api/health"]
        spa["<b>public/</b><br/>index.html · app.js · styles.css<br/>KPIs · trend chart · test cases · run history"]
        spa -- "fetch every 30 s" --> srv
    end

    db[("results/automation-results.db<br/>project · test_case · test_run · test_result")]
    reports["target/cucumber-reports/<br/>HTML · JSON · JUnit XML"]
    tdsys["TestDesk"]

    projects --> cuc
    cuc --> steps
    steps --> drivers
    drivers --> cfg
    cuc -- "events" --> plugins
    cuc --> reports
    pub -- "JDBC (sqlite-jdbc)" --> db
    td -- "HTTPS / REST" --> tdsys
    srv -- "read" --> db
```

### Responsibilities

| Component | Responsibility | Key files |
|---|---|---|
| **Config** | Layered settings; any key can be overridden by `-Dkey=value` or an upper-snake env var (`testdesk.url` → `TESTDESK_URL`) | `core/config/Config.java` |
| **ApiClient** | HTTP calls, JSON Schema contract checks, request/response evidence with secrets masked | `core/api/*` |
| **UiDriver** | One page-object API over Playwright or Selenium (local or Grid); headless or headed | `core/web/*` |
| **Mobile** | Starts/stops the emulator and Appium when needed, finds the Android SDK, builds capabilities, real-device and remote modes | `core/mobile/**` |
| **DbResultPublisher** | Syncs the project + catalog on run start, writes each scenario result, closes the run | `core/reporting/*` |
| **TestDeskResultListener** | At `TestRunFinished`: syncs specs (creates missing cases), posts results to a plan, uploads evidence | `core/testdesk/*` |
| **Dashboard server** | Read-only JSON API + static files over the same DB | `dashboard/server.js` |
| **Dashboard UI** | Filters (project, category), KPI tiles, success-rate trend, coverage, test-case and run drill-down | `dashboard/public/*` |

**Design rule:** reporting never breaks testing. Both `DbResultPublisher` and `TestDeskResultListener` log a WARN on
any failure (DB locked, TestDesk down, bad token) and the test run's own pass/fail is unaffected.

---

## 3. Runtime flow of one test run

```mermaid
sequenceDiagram
    autonumber
    actor U as Engineer / Jenkins
    participant M as Maven (mvnw + Surefire)
    participant C as Cucumber engine
    participant P as DbResultPublisher
    participant DB as Quality metrics DB
    participant T as TestDeskResultListener
    participant AUT as App under test
    participant TD as TestDesk
    participant D as Dashboard

    U->>M: ./mvnw -pl web-tests test -Denv=staging [-Dtestdesk.enabled=true]
    M->>C: RunCucumberTest (JUnit Platform)
    C->>P: TestRunStarted
    P->>DB: upsert project, upsert catalog.csv test cases
    P->>DB: INSERT test_run (status RUNNING, trigger LOCAL|JENKINS, build #)
    loop each scenario (Web: 3 in parallel)
        C->>AUT: steps via ApiClient / UiDriver / Appium
        AUT-->>C: responses / screens
        C->>P: TestCaseFinished (status, duration, error)
        P->>DB: upsert test_case (is_automated=1), INSERT test_result
        C->>T: TestCaseFinished (+ steps, attachments)
        Note right of T: buffered to temp files
    end
    C->>P: TestRunFinished
    P->>DB: UPDATE test_run → COMPLETED, totals, duration
    opt testdesk.enabled=true
        C->>T: TestRunFinished
        T->>TD: PUT /api/projects/{prefix}/automation/cases (spec sync, auto-create)
        T->>TD: POST /api/projects/{prefix}/automation/results → plan_id
        T->>TD: POST /api/plans/{plan_id}/automation/evidence (multipart)
    end
    M-->>U: target/cucumber-reports (HTML, JSON, JUnit)
    D->>DB: SELECT ... (on every /api/dashboard request)
    Note over D: browser auto-refreshes every 30 s
```

---

## 4. Data model (quality metrics DB)

Schema source of truth: `automation-core/src/main/resources/db/schema.sql`. Both the framework and the dashboard
apply it with `CREATE TABLE IF NOT EXISTS`, so a new empty DB is created automatically.

```mermaid
erDiagram
    project ||--o{ test_case : "has catalog"
    project ||--o{ test_run : "has runs"
    test_run ||--o{ test_result : "contains"
    test_case |o--o{ test_result : "executed as"

    project {
        int id PK
        text project_key UK "e.g. WEB-SAUCEDEMO"
        text name
        text type "API | WEB | MOBILE"
        text description
        text created_at
    }
    test_case {
        int id PK
        int project_id FK
        text tc_key "matches @tc:<ID>"
        text title
        int is_regression
        int is_sanity "subset of regression"
        int is_new_feature
        int is_automated "flips 0→1 once executed"
        text priority
        text testlink_id "deprecated"
        text updated_at
    }
    test_run {
        int id PK
        int project_id FK
        text status "RUNNING | COMPLETED"
        text started_at
        text finished_at
        int duration_ms
        text environment
        text trigger_source "LOCAL | JENKINS"
        text build_number
        text tag_filter
        text engine
        text executed_by
        int total
        int passed
        int failed
        int pending
    }
    test_result {
        int id PK
        int run_id FK
        int test_case_id FK "null if no @tc tag"
        text scenario_name
        text feature_file
        text status "PASSED | FAILED | PENDING"
        int duration_ms
        text error_message
        text executed_at
    }
```

### How the dashboard metrics are derived

| Metric | Query basis |
|---|---|
| Test cases | `test_case` rows for the selected project(s) and category |
| Automated / Coverage | `is_automated = 1` ÷ all test cases |
| Success rate, Passed / Failed / Pending | `test_result` rows of each project's **latest `COMPLETED` run** |
| Trend | Last 30 `COMPLETED` runs per project (`passed ÷ total`) |
| Test-case last result | Most recent `test_result` per `test_case` |

A run that crashes before `TestRunFinished` stays `RUNNING` and is left out of the KPIs, trend and run history.

---

## 5. Deployment views

### 5a. Developer workstation (default)

```mermaid
flowchart LR
    subgraph ws["Developer workstation (Windows / macOS / Linux)"]
        direction TB
        mvn["mvnw test<br/>JDK 21"]
        br["Chromium / Chrome<br/>(Playwright-managed)"]
        emu["Android emulator + Appium<br/>(started on demand)"]
        file[("./results/<br/>automation-results.db")]
        node["node dashboard/server.js<br/>http://localhost:3000"]
        mvn --> br
        mvn --> emu
        mvn --> file
        node --> file
    end
    ws -- "internet" --> aut["Public test targets<br/>jsonplaceholder · saucedemo · APK on GitHub"]
```

### 5b. Team / CI deployment (recommended for phase 1–2)

SQLite is a single file with WAL locking, which **only works on a local disk**. The Jenkins agent that runs the suites
and the dashboard therefore share one host (or one local volume).

```mermaid
flowchart TB
    users(["Browsers on the corporate network"])

    subgraph ci["QA server (one host, Linux or Windows)"]
        direction TB
        proxy["Reverse proxy (nginx / IIS)<br/>TLS :443 + SSO / basic auth"]
        dash["Quality Metrics Dashboard<br/>systemd / Windows service<br/>127.0.0.1:3000"]
        dbf[("/srv/qa/automation-results.db<br/>(local disk, backed up)")]
        agent["Jenkins agent label: qa<br/>JDK 21 (tool 'jdk21') · Playwright browsers<br/>Android SDK + AVD + Appium (if Android)"]
        proxy --> dash
        dash -- "DB_PATH" --> dbf
        agent -- "writes via<br/>results.db.url (JDBC)" --> dbf
    end

    controller["Jenkins controller<br/>Pipeline job from SCM<br/>nightly cron H 2 * * 1-5"]
    git["Git"]
    td["TestDesk :3100<br/>(separate host, optional)"]
    grid["Selenium Grid / device farm<br/>(optional)"]
    aut["SIT / UAT / staging environments<br/>of the applications under test"]

    users -- HTTPS --> proxy
    git --> controller
    controller -- "schedules build" --> agent
    agent --> aut
    agent -. "TESTDESK_TOKEN (Jenkins credential)" .-> td
    agent -. optional .-> grid
```

### 5c. Target state: shared database, many agents (not implemented yet)

```mermaid
flowchart LR
    a1["Jenkins agent 1"] --> pg
    a2["Jenkins agent 2"] --> pg
    a3["Jenkins agent N"] --> pg
    pg[("PostgreSQL<br/>quality metrics")]
    dash["Dashboard ×1..n<br/>(pg driver)"] --> pg
    lb["Load balancer / proxy"] --> dash
```

What it takes (see the deployment guide, §9): add the `org.postgresql:postgresql` driver to `automation-core`,
change `AUTOINCREMENT` to `GENERATED BY DEFAULT AS IDENTITY` in the schema, and replace `node:sqlite` with `pg` in
`dashboard/server.js`. The framework's SQL already uses portable `ON CONFLICT` upserts.

---

## 6. CI pipeline (Jenkinsfile)

```mermaid
flowchart LR
    trig(["Trigger<br/>manual · nightly cron · app deploy job"]) --> b
    b["Build framework<br/>mvnw install -DskipTests"] --> par
    subgraph par["Run suites (parallel)"]
        direction TB
        s1["API<br/>-pl api-tests"]
        s2["Web<br/>-pl web-tests -P&lt;RUN_MODE&gt;<br/>-Dweb.engine=&lt;WEB_ENGINE&gt;"]
    end
    par --> s3["Android (sequential, needs CPU)<br/>-pl android-tests -P&lt;ANDROID_MODE&gt;"]
    s3 --> post["post: always<br/>JUnit publish · archive cucumber-reports"]
    par -. "results" .-> db[("Metrics DB")]
    s3 -. "results" .-> db
    db --> d["Dashboard"]
```

Parameters: `SUITE`, `TAGS`, `TEST_ENV`, `WEB_ENGINE`, `RUN_MODE`, `ANDROID_MODE`, `ANDROID_APP`, `RESULTS_DB_URL`.
Tests are run with `-Dmaven.test.failure.ignore=true`, so a failing scenario marks the build **UNSTABLE**
(via the JUnit step) rather than aborting later stages.

---

## 7. Security notes

| Concern | Current state | Recommendation |
|---|---|---|
| Dashboard access | No authentication; listens on all interfaces on `PORT` | Bind behind a reverse proxy with TLS and SSO/basic auth, or firewall port 3000 |
| Dashboard data | Read-only API; path traversal guarded (`/public` only) | Fine to expose internally once behind the proxy |
| TestDesk token | Read only from env `TESTDESK_TOKEN`, never from properties files | Store as a Jenkins *Secret text* credential; use a dedicated `ci-bot` user |
| API evidence | `Redaction` masks `Authorization`, `password`, `token` fields | Review before attaching evidence from production-like data |
| Test accounts | Public demo credentials in `default.properties` | For real apps, inject credentials via env vars / Jenkins credentials |
