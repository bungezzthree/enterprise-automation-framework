package com.enterprise.qa.core.testdesk;

import com.enterprise.qa.core.testdesk.ScenarioRecord.Evidence;
import com.enterprise.qa.core.testdesk.TestDeskClient.TestDeskException;
import com.enterprise.qa.core.testdesk.TestDeskPayloads.RunContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Sends one finished run to TestDesk:
 * <ol>
 *   <li><b>Test specification.</b> Test cases that already exist in TestDesk are linked/updated; test cases that do not
 *       exist yet are created (when {@code autoCreate} is on), with their steps taken from the Gherkin.</li>
 *   <li><b>Execution.</b> The results are recorded in a test plan.</li>
 *   <li><b>Evidence.</b> Screenshots and API exchanges are attached to those results.</li>
 * </ol>
 */
public final class TestDeskReporter {

    /** @param payloadDir where the JSON bodies that are sent are also saved for replay; null to skip */
    public record Settings(String project, String baseUrl, String suiteRoot, boolean syncSpec, boolean autoCreate, boolean dryRun,
                           String evidenceMode, long evidenceMaxBytes, Path payloadDir) {
    }

    /** What happened, for logging and tests. {@code planId} is -1 when TestDesk did not return one. */
    public record Summary(long planId, int recorded, int duplicates, int created, int linked, int updated, int unmatched, int evidenceFiles) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(TestDeskReporter.class);
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    /** TestDesk's answer when a {@code case_key} is not in the project; it means "create it". */
    static final String NOT_FOUND_MARKER = "not found in this project";

    private final TestDeskClient client;
    private final Settings settings;

    public TestDeskReporter(TestDeskClient client, Settings settings) {
        this.client = client;
        this.settings = settings;
    }

    public Summary report(List<ScenarioRecord> records, RunContext run) throws IOException, InterruptedException {
        int created = 0;
        int linked = 0;
        int updated = 0;
        if (settings.syncSpec()) {
            int[] counts = syncSpecification(records, run);
            created = counts[0];
            linked = counts[1];
            updated = counts[2];
        }

        boolean resultsMayCreate = settings.autoCreate() && !settings.syncSpec();
        JsonObject body = TestDeskPayloads.resultsBody(records, run, settings.dryRun(), resultsMayCreate);
        savePayload("results", run, body);
        JsonObject results = client.recordResults(settings.project(), body);
        int unmatched = logResults(results, run);

        long planId = results.has("plan_id") && !results.get("plan_id").isJsonNull() ? results.get("plan_id").getAsLong() : -1;
        int files = 0;
        if (!settings.dryRun() && !settings.evidenceMode().equals("none") && planId > 0) {
            files = uploadEvidence(planId, records, run.runId());
        }
        return new Summary(planId, count(results, "recorded"), count(results, "duplicates"), created, linked, updated, unmatched, files);
    }

    // ---------------------------------------------------------------- specification

    /** @return {created, linked, updated} */
    private int[] syncSpecification(List<ScenarioRecord> records, RunContext run) throws IOException, InterruptedException {
        JsonObject first = sync(TestDeskPayloads.syncBody(records, settings.suiteRoot(), settings.dryRun(), true, false), "sync", run);
        if (first == null) {
            return new int[] {0, 0, 0};
        }
        int created = count(first, "created"); // a TestDesk that keeps the requested case_key creates them right away
        int linked = count(first, "linked");
        int updated = count(first, "updated");
        LOG.info("TestDesk spec: {} created, {} linked to existing, {} updated, {} unchanged", created, linked, updated, count(first, "unchanged"));
        forEachError(first, (ref, message) -> {
            if (!(settings.autoCreate() && message.contains(NOT_FOUND_MARKER))) {
                LOG.warn("TestDesk spec sync: {}: {}", ref, message);
            }
        });

        Set<String> missing = refsWithError(first, NOT_FOUND_MARKER);
        if (missing.isEmpty()) {
            return new int[] {created, linked, updated};
        }
        List<ScenarioRecord> toCreate = records.stream().filter(r -> missing.contains(r.key())).toList();
        if (!settings.autoCreate()) {
            LOG.warn("TestDesk: {} test case(s) do not exist yet and testdesk.auto.create is off: {}", toCreate.size(),
                    toCreate.stream().map(ScenarioRecord::key).toList());
            return new int[] {created, linked, updated};
        }
        JsonObject second = sync(TestDeskPayloads.syncBody(toCreate, settings.suiteRoot(), settings.dryRun(), false, true), "sync-create", run);
        if (second != null) {
            created += count(second, "created");
            LOG.info("TestDesk spec: {} new test case(s) added to Test Specification", created);
            logCreated(second);
            forEachError(second, (ref, message) -> LOG.warn("TestDesk could not create {}: {}", ref, message));
        }
        return new int[] {created, linked, updated};
    }

    /** Wrong URL, token or project: nothing else will work either, so give up instead of failing every call. */
    private static boolean isFatal(TestDeskException e) {
        return e.status() == 0 || e.status() == 401 || e.status() == 403 || e.status() == 404;
    }

    private JsonObject sync(JsonObject body, String name, RunContext run) throws IOException, InterruptedException {
        savePayload(name, run, body);
        try {
            return client.syncCases(settings.project(), body);
        } catch (TestDeskException e) {
            if (isFatal(e)) {
                throw e;
            }
            LOG.warn("TestDesk spec sync failed, results are still sent: {}", e.getMessage());
            return null;
        }
    }

    private static void logCreated(JsonObject sync) {
        if (!sync.has("cases") || !sync.get("cases").isJsonArray()) {
            return;
        }
        for (JsonElement element : sync.getAsJsonArray("cases")) {
            JsonObject c = element.getAsJsonObject();
            if ("created".equals(text(c, "action"))) {
                LOG.info("  new test case {} (TestDesk id {})", text(c, "automation_ref"), text(c, "case_key"));
            }
        }
    }

    private static Set<String> refsWithError(JsonObject response, String marker) {
        Set<String> refs = new HashSet<>();
        forEachError(response, (ref, message) -> {
            if (message.contains(marker)) {
                refs.add(ref);
            }
        });
        return refs;
    }

    // ---------------------------------------------------------------- results

    private int logResults(JsonObject results, RunContext run) {
        JsonObject summary = results.has("summary") ? results.getAsJsonObject("summary") : new JsonObject();
        LOG.info("TestDesk plan #{} '{}': {} recorded (passed {}, failed {}, blocked {}, skipped {}), {} already recorded for run {} -> {}{}",
                text(results, "plan_id"), text(results, "plan_name"), count(results, "recorded"), count(summary, "Passed"),
                count(summary, "Failed"), count(summary, "Blocked"), count(summary, "Skipped"), count(results, "duplicates"),
                run.runId(), settings.baseUrl(), text(results, "plan_path"));
        int unmatched = results.has("unmatched") && results.get("unmatched").isJsonArray() ? results.getAsJsonArray("unmatched").size() : 0;
        if (unmatched > 0) {
            LOG.warn("TestDesk could not match these test cases (set testdesk.auto.create=true or import them first): {}", results.get("unmatched"));
        }
        forEachError(results, (ref, message) -> LOG.warn("TestDesk result for {} rejected: {}", ref, message));
        return unmatched;
    }

    // ---------------------------------------------------------------- evidence

    private int uploadEvidence(long planId, List<ScenarioRecord> records, String runId) {
        int uploaded = 0;
        for (ScenarioRecord record : records) {
            List<Evidence> files = new ArrayList<>();
            for (Evidence f : record.evidence()) {
                try {
                    if (Files.size(f.file()) > settings.evidenceMaxBytes()) {
                        LOG.warn("Evidence '{}' of {} is larger than {} MB and is skipped", f.fileName(), record.key(), settings.evidenceMaxBytes() / 1024 / 1024);
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
                upload(planId, record.key(), runId, files);
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
        return uploaded;
    }

    /** Test cases linked by the spec sync are found by automation_ref; imported ones that were not synced, by case_key. */
    private void upload(long planId, String key, String runId, List<Evidence> files) throws IOException, InterruptedException {
        try {
            client.uploadEvidence(planId, "automation_ref", key, runId, files);
        } catch (TestDeskException e) {
            if (e.status() != 404) {
                throw e;
            }
            client.uploadEvidence(planId, "case_key", key, runId, files);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void savePayload(String name, RunContext run, JsonObject body) {
        if (settings.payloadDir() == null) {
            return;
        }
        try {
            Files.createDirectories(settings.payloadDir());
            String safe = run.runId().replaceAll("[^A-Za-z0-9._-]", "_");
            Files.writeString(settings.payloadDir().resolve(name + "-" + safe + ".json"), PRETTY.toJson(body));
        } catch (IOException e) {
            LOG.debug("Cannot save TestDesk payload {}: {}", name, e.getMessage());
        }
    }

    private static void forEachError(JsonObject response, BiConsumer<String, String> consumer) {
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
}
