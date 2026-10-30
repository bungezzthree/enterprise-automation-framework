package com.enterprise.qa.core.testdesk;

import com.enterprise.qa.core.config.Config;
import com.enterprise.qa.core.reporting.TestCaseInfo;
import com.enterprise.qa.core.testdesk.ScenarioRecord.Evidence;
import com.enterprise.qa.core.testdesk.ScenarioRecord.StepText;
import com.enterprise.qa.core.testdesk.TestDeskClient.TestDeskException;
import com.enterprise.qa.core.testdesk.TestDeskPayloads.RunContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EmbedEvent;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.WriteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Cucumber plugin that reports a run to TestDesk: syncs the test specification from the Gherkin, records the
 * results in a test plan and uploads evidence. Everything is sent once, at the end of the run.
 * Enable with {@code testdesk.enabled=true}; see docs/design-testdesk-integration.md.
 *
 * <p>Reporting problems are logged, never thrown: TestDesk being down must not change the outcome of a test run.</p>
 */
public class TestDeskResultListener implements ConcurrentEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestDeskResultListener.class);
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_MESSAGE_CHARS = 2000;

    private final List<ScenarioRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, List<Evidence>> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, StringBuilder> consoleLogs = new ConcurrentHashMap<>();
    private volatile Path evidenceDir;
    private String evidenceMode = "failed";
    private LocalDateTime startedAt = LocalDateTime.now();

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        if (!Config.getBool("testdesk.enabled", false)) {
            return;
        }
        List<String> missing = Stream.of("testdesk.url", "testdesk.token", "testdesk.project")
                .filter(key -> Config.get(key) == null).toList();
        if (!missing.isEmpty()) {
            LOG.warn("TestDesk reporting disabled: missing configuration {} (the token comes from env TESTDESK_TOKEN)", missing);
            return;
        }
        evidenceMode = Config.get("evidence.mode", "failed").toLowerCase(Locale.ROOT);
        publisher.registerHandlerFor(TestRunStarted.class, e -> startedAt = LocalDateTime.now());
        publisher.registerHandlerFor(EmbedEvent.class, this::onEmbed);
        publisher.registerHandlerFor(WriteEvent.class, this::onWrite);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        publisher.registerHandlerFor(TestRunFinished.class, e -> flush());
        LOG.info("TestDesk reporting enabled -> {} (project {}, evidence {})", Config.get("testdesk.url"), Config.get("testdesk.project"), evidenceMode);
    }

    // ---------------------------------------------------------------- collecting

    private void onEmbed(EmbedEvent event) {
        if (evidenceMode.equals("none")) {
            return;
        }
        try {
            Path file = Files.createTempFile(evidenceDir(), "ev-", ".bin");
            Files.write(file, event.getData());
            List<Evidence> list = evidence.computeIfAbsent(event.getTestCase().getId(), k -> new CopyOnWriteArrayList<>());
            String name = uniqueName(list, evidenceFileName(event.getName(), event.getMediaType()));
            list.add(new Evidence(name, event.getMediaType(), file));
        } catch (IOException e) {
            LOG.warn("Cannot keep evidence '{}': {}", event.getName(), e.getMessage());
        }
    }

    private void onWrite(WriteEvent event) {
        consoleLogs.computeIfAbsent(event.getTestCase().getId(), k -> new StringBuilder()).append(event.getText()).append('\n');
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        TestCase testCase = event.getTestCase();
        Result result = event.getResult();
        List<Evidence> files = evidence.remove(testCase.getId());
        StringBuilder log = consoleLogs.remove(testCase.getId());
        Optional<String> key = TestCaseInfo.tcKey(testCase.getTags());
        if (key.isEmpty()) {
            LOG.warn("Scenario '{}' has no @tc:<ID> tag; it is not reported to TestDesk", testCase.getName());
            if (files != null) {
                files.forEach(f -> deleteQuietly(f.file()));
            }
            return;
        }
        List<StepText> steps = testCase.getTestSteps().stream()
                .filter(PickleStepTestStep.class::isInstance).map(PickleStepTestStep.class::cast)
                .map(s -> new StepText(s.getStep().getKeyword(), s.getStep().getText())).toList();
        Throwable error = result.getError();
        records.add(new ScenarioRecord(key.get(), testCase.getName(), List.copyOf(testCase.getTags()), steps,
                result.getStatus().name(), result.getDuration().toMillis(), errorMessage(error), outputOf(error, log),
                files == null ? List.of() : List.copyOf(files)));
    }

    // ---------------------------------------------------------------- reporting

    private void flush() {
        try {
            report();
        } catch (TestDeskException e) {
            LOG.warn("TestDesk reporting failed: {}", e.getMessage());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("TestDesk reporting failed unexpectedly: {}", e.toString(), e);
        } finally {
            cleanup();
        }
    }

    private void report() throws IOException, InterruptedException {
        List<ScenarioRecord> all;
        synchronized (records) {
            all = TestDeskPayloads.mergeByKey(new ArrayList<>(records));
        }
        if (all.isEmpty()) {
            LOG.info("TestDesk: no scenario with an @tc tag ran, nothing to report");
            return;
        }
        String project = Config.require("testdesk.project");
        boolean dryRun = Config.getBool("testdesk.dry.run", false);
        RunContext run = runContext(project);
        JsonObject syncBody = TestDeskPayloads.syncBody(all, "Automation", dryRun);
        JsonObject resultsBody = TestDeskPayloads.resultsBody(all, run, dryRun);
        savePayloads(run.runId(), syncBody, resultsBody);

        TestDeskClient client = new TestDeskClient(Config.require("testdesk.url"), Config.require("testdesk.token"));
        if (Config.getBool("testdesk.sync.spec", true)) {
            try {
                logSync(client.syncCases(project, syncBody));
            } catch (TestDeskException e) {
                if (isFatal(e)) {
                    throw e;
                }
                LOG.warn("TestDesk spec sync failed, results are still sent: {}", e.getMessage());
            }
        }
        JsonObject results = client.recordResults(project, resultsBody);
        logResults(results, run);
        if (!dryRun && !evidenceMode.equals("none") && results.has("plan_id")) {
            uploadEvidence(client, results.get("plan_id").getAsLong(), all, run.runId());
        }
    }

    /** Wrong URL, token or project: nothing else will work either, so stop instead of failing every call. */
    private static boolean isFatal(TestDeskException e) {
        return e.status() == 0 || e.status() == 401 || e.status() == 403 || e.status() == 404;
    }

    private RunContext runContext(String project) {
        String environment = Config.get("testdesk.environment", "SIT").toUpperCase(Locale.ROOT);
        if (!environment.equals("SIT") && !environment.equals("UAT")) {
            LOG.warn("testdesk.environment '{}' is not SIT or UAT; using SIT", environment);
            environment = "SIT";
        }
        String planName = Config.get("testdesk.plan.name", "EAF " + Config.get("project.key", project) + " " + environment);
        String build = firstNonNull(Config.get("testdesk.build"), System.getenv("BUILD_NUMBER"));
        String ciUrl = firstNonNull(Config.get("testdesk.ci.url"), System.getenv("BUILD_URL"));
        String runId = firstNonNull(Config.get("testdesk.run.id"), System.getenv("BUILD_TAG"));
        if (runId == null) {
            runId = "local-" + System.getProperty("user.name", "user") + "-" + startedAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }
        return new RunContext(planName, environment, build, ciUrl, runId);
    }

    private void logSync(JsonObject sync) {
        LOG.info("TestDesk spec sync: created {}, updated {}, unchanged {}, linked {}", count(sync, "created"), count(sync, "updated"),
                count(sync, "unchanged"), count(sync, "linked"));
        forEachError(sync, (ref, message) -> LOG.warn("TestDesk spec sync: {}: {}{}", ref, message,
                message.contains("not found") ? " (import the catalog into TestDesk first: tools/catalog-to-testdesk.mjs)" : ""));
    }

    private void logResults(JsonObject results, RunContext run) {
        JsonObject summary = results.has("summary") ? results.getAsJsonObject("summary") : new JsonObject();
        LOG.info("TestDesk plan #{} '{}': {} recorded (passed {}, failed {}, blocked {}, skipped {}), {} already recorded for run {} -> {}{}",
                text(results, "plan_id"), text(results, "plan_name"), count(results, "recorded"), count(summary, "Passed"),
                count(summary, "Failed"), count(summary, "Blocked"), count(summary, "Skipped"), count(results, "duplicates"),
                run.runId(), Config.get("testdesk.url"), text(results, "plan_path"));
        if (results.has("unmatched") && results.getAsJsonArray("unmatched").size() > 0) {
            LOG.warn("TestDesk could not match these test cases (import the catalog first): {}", results.get("unmatched"));
        }
        forEachError(results, (ref, message) -> LOG.warn("TestDesk result for {} rejected: {}", ref, message));
    }

    private void uploadEvidence(TestDeskClient client, long planId, List<ScenarioRecord> all, String runId) {
        long maxBytes = (long) Config.getInt("testdesk.evidence.max.mb", 20) * 1024 * 1024;
        int uploaded = 0;
        for (ScenarioRecord record : all) {
            List<Evidence> files = new ArrayList<>();
            for (Evidence f : record.evidence()) {
                try {
                    if (Files.size(f.file()) > maxBytes) {
                        LOG.warn("Evidence '{}' of {} is larger than {} MB and is skipped", f.fileName(), record.key(), maxBytes / 1024 / 1024);
                    } else {
                        files.add(f);
                    }
                } catch (IOException e) {
                    LOG.warn("Evidence '{}' of {} cannot be read: {}", f.fileName(), record.key(), e.getMessage());
                }
            }
            if (files.isEmpty()) {
                continue;
            }
            try {
                client.uploadEvidence(planId, record.key(), runId, files);
                uploaded += files.size();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                LOG.warn("TestDesk evidence upload failed for {}: {}", record.key(), e.getMessage());
            }
        }
        if (uploaded > 0) {
            LOG.info("TestDesk: {} evidence file(s) uploaded", uploaded);
        }
    }

    private void savePayloads(String runId, JsonObject sync, JsonObject results) {
        try {
            Path dir = Files.createDirectories(Path.of("target", "testdesk"));
            String safe = runId.replaceAll("[^A-Za-z0-9._-]", "_");
            Files.writeString(dir.resolve("sync-" + safe + ".json"), PRETTY.toJson(sync));
            Files.writeString(dir.resolve("results-" + safe + ".json"), PRETTY.toJson(results));
        } catch (IOException e) {
            LOG.debug("Cannot save TestDesk payloads: {}", e.getMessage());
        }
    }

    private void cleanup() {
        Path dir = evidenceDir;
        if (dir == null) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(TestDeskResultListener::deleteQuietly);
        } catch (IOException ignored) {
            // temp files are harmless
        }
        deleteQuietly(dir);
    }

    private synchronized Path evidenceDir() throws IOException {
        if (evidenceDir == null) {
            evidenceDir = Files.createTempDirectory("eaf-evidence-");
        }
        return evidenceDir;
    }

    // ---------------------------------------------------------------- helpers

    /** File name for an attachment: the attachment name (or "evidence") plus an extension derived from the media type. */
    static String evidenceFileName(String name, String mediaType) {
        String base = name == null || name.isBlank() ? "evidence" : name.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        String extension = extensionFor(mediaType);
        return base.toLowerCase(Locale.ROOT).endsWith(extension) ? base : base + extension;
    }

    static String extensionFor(String mediaType) {
        String type = mediaType == null ? "" : mediaType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "text/plain" -> ".txt";
            case "text/html" -> ".html";
            case "application/json" -> ".json";
            case "application/pdf" -> ".pdf";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            default -> ".bin";
        };
    }

    private static String uniqueName(List<Evidence> existing, String name) {
        String candidate = name;
        int n = 2;
        while (hasName(existing, candidate)) {
            int dot = name.lastIndexOf('.');
            candidate = dot < 0 ? name + "-" + n : name.substring(0, dot) + "-" + n + name.substring(dot);
            n++;
        }
        return candidate;
    }

    private static boolean hasName(List<Evidence> list, String name) {
        return list.stream().anyMatch(e -> e.fileName().equals(name));
    }

    static String errorMessage(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        return message.length() > MAX_MESSAGE_CHARS ? message.substring(0, MAX_MESSAGE_CHARS) : message;
    }

    private static String outputOf(Throwable error, StringBuilder log) {
        StringBuilder out = new StringBuilder();
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            out.append(trace);
        }
        if (log != null && !log.isEmpty()) {
            out.append("\n--- console ---\n").append(log);
        }
        return out.isEmpty() ? null : out.toString();
    }

    private static void forEachError(JsonObject response, java.util.function.BiConsumer<String, String> consumer) {
        if (!response.has("errors") || !response.get("errors").isJsonArray()) {
            return;
        }
        for (JsonElement element : response.getAsJsonArray("errors")) {
            if (element.isJsonObject()) {
                JsonObject e = element.getAsJsonObject();
                String ref = e.has("automation_ref") && !e.get("automation_ref").isJsonNull() ? e.get("automation_ref").getAsString()
                        : e.has("index") ? "item " + e.get("index").getAsString() : "?";
                consumer.accept(ref, e.has("message") ? e.get("message").getAsString() : e.toString());
            }
        }
    }

    private static int count(JsonObject json, String field) {
        return json.has(field) && json.get(field).isJsonPrimitive() ? json.get(field).getAsInt() : 0;
    }

    private static String text(JsonObject json, String field) {
        return json.has(field) && !json.get(field).isJsonNull() ? json.get(field).getAsString() : "";
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
