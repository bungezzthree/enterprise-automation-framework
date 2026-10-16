package com.enterprise.qa.core.reporting;

import com.enterprise.qa.core.config.Config;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cucumber plugin that streams every scenario result into the quality-metrics database.
 * Register it in {@code junit-platform.properties}:
 * {@code cucumber.plugin=com.enterprise.qa.core.reporting.DbResultPublisher}.
 *
 * <p>Publishing problems are logged, never thrown: a dashboard outage must not fail a test run.</p>
 */
public class DbResultPublisher implements ConcurrentEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(DbResultPublisher.class);

    private ResultRepository repository;
    private long projectId;
    private long runId;
    private Instant startedAt;
    private boolean active;

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        if (!Config.getBool("results.db.enabled", true)) {
            LOG.info("Result publishing disabled (results.db.enabled=false)");
            return;
        }
        publisher.registerHandlerFor(TestRunStarted.class, this::onRunStarted);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        publisher.registerHandlerFor(TestRunFinished.class, this::onRunFinished);
    }

    private synchronized void onRunStarted(TestRunStarted event) {
        String url = Config.get("results.db.url", "jdbc:sqlite:results/automation-results.db");
        try {
            repository = new ResultRepository(url);
            projectId = repository.upsertProject(ProjectInfo.fromConfig());
            List<TestCaseInfo> catalog = TestCaseCatalog.load(Config.get("testcases.catalog", "testcases/catalog.csv"));
            for (TestCaseInfo tc : catalog) {
                repository.upsertTestCase(projectId, tc);
            }
            startedAt = event.getInstant();
            runId = repository.createRun(projectId, RunInfo.current(startedAt));
            active = true;
            LOG.info("Publishing results to {} (run #{}, {} catalog test cases synced)", url, runId, catalog.size());
        } catch (Exception e) {
            LOG.warn("Result publishing disabled for this run: {}", e.getMessage(), e);
            closeQuietly();
        }
    }

    private synchronized void onTestCaseFinished(TestCaseFinished event) {
        if (!active) {
            return;
        }
        TestCase testCase = event.getTestCase();
        Result result = event.getResult();
        try {
            TestCaseInfo tc = TestCaseInfo.fromScenario(testCase.getName(), testCase.getTags());
            Long testCaseId = null;
            if (tc.key() == null) {
                LOG.warn("Scenario '{}' has no @tc:<ID> tag; result stored without a test case link", testCase.getName());
            } else {
                testCaseId = repository.upsertTestCase(projectId, tc);
            }
            repository.insertResult(runId, testCaseId, testCase.getName(), featureFile(testCase),
                    mapStatus(result.getStatus()), result.getDuration().toMillis(), errorOf(result));
        } catch (Exception e) {
            LOG.warn("Could not publish result for '{}': {}", testCase.getName(), e.getMessage());
        }
    }

    private synchronized void onRunFinished(TestRunFinished event) {
        if (!active) {
            return;
        }
        try {
            repository.finishRun(runId, event.getInstant(), Duration.between(startedAt, event.getInstant()).toMillis());
            LOG.info("Run #{} published to quality dashboard database", runId);
        } catch (Exception e) {
            LOG.warn("Could not close run #{}: {}", runId, e.getMessage());
        } finally {
            closeQuietly();
        }
    }

    /** Dashboard vocabulary: PASSED, FAILED, PENDING (not yet implemented / skipped / undefined). */
    static String mapStatus(Status status) {
        return switch (status) {
            case PASSED -> "PASSED";
            case FAILED, AMBIGUOUS -> "FAILED";
            default -> "PENDING";
        };
    }

    private static String featureFile(TestCase testCase) {
        String path = testCase.getUri().getSchemeSpecificPart();
        return path == null ? null : path.substring(path.lastIndexOf('/') + 1);
    }

    private static String errorOf(Result result) {
        Throwable error = result.getError();
        if (error == null) {
            return null;
        }
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private void closeQuietly() {
        active = false;
        if (repository != null) {
            try {
                repository.close();
            } catch (Exception ignored) {
                // nothing useful to do
            }
            repository = null;
        }
    }
}
