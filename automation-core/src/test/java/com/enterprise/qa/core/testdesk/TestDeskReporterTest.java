package com.enterprise.qa.core.testdesk;

import com.enterprise.qa.core.testdesk.ScenarioRecord.Evidence;
import com.enterprise.qa.core.testdesk.ScenarioRecord.StepText;
import com.enterprise.qa.core.testdesk.TestDeskClient.TestDeskException;
import com.enterprise.qa.core.testdesk.TestDeskPayloads.RunContext;
import com.enterprise.qa.core.testdesk.TestDeskReporter.Settings;
import com.enterprise.qa.core.testdesk.TestDeskReporter.Summary;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The behaviour the demo promises: missing test cases are created, existing ones only get their execution updated. */
class TestDeskReporterTest {

    /** A small stateful stand-in for TestDesk's automation API. */
    static final class FakeTestDesk {
        final HttpServer server;
        final Set<String> manualKeys = ConcurrentHashMap.newKeySet();          // imported by hand, not linked to code yet
        final Map<String, String> linked = new ConcurrentHashMap<>();         // automation_ref -> TestDesk case_key
        final List<JsonObject> syncBodies = new CopyOnWriteArrayList<>();
        final List<String> recordedRefs = new CopyOnWriteArrayList<>();
        final List<String> evidenceRequests = new CopyOnWriteArrayList<>();
        final AtomicInteger sequence = new AtomicInteger();
        final AtomicInteger resultCalls = new AtomicInteger();
        volatile int syncStatus = 200;
        private final Gson gson = new Gson();

        FakeTestDesk() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/projects/API/automation/cases", this::sync);
            server.createContext("/api/projects/API/automation/results", this::results);
            server.createContext("/api/plans/1/automation/evidence", this::evidence);
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void sync(HttpExchange ex) throws IOException {
            if (syncStatus != 200) {
                reply(ex, syncStatus, "{\"error\":\"Invalid or expired API token\"}");
                return;
            }
            JsonObject body = gson.fromJson(read(ex), JsonObject.class);
            syncBodies.add(body);
            int created = 0, linkedNow = 0, unchanged = 0;
            JsonArray cases = new JsonArray();
            JsonArray errors = new JsonArray();
            int index = 0;
            for (var element : body.getAsJsonArray("cases")) {
                JsonObject c = element.getAsJsonObject();
                String ref = c.get("automation_ref").getAsString();
                String key = c.has("case_key") ? c.get("case_key").getAsString() : null;
                JsonObject out = new JsonObject();
                out.addProperty("automation_ref", ref);
                if (linked.containsKey(ref)) {
                    unchanged++;
                    out.addProperty("case_key", linked.get(ref));
                    out.addProperty("action", "unchanged");
                } else if (key != null && manualKeys.contains(key)) {
                    linkedNow++;
                    linked.put(ref, key);
                    out.addProperty("case_key", key);
                    out.addProperty("action", "linked");
                } else if (key != null) {
                    JsonObject error = new JsonObject();
                    error.addProperty("index", index);
                    error.addProperty("automation_ref", ref);
                    error.addProperty("message", "case_key \"" + key + "\" not found in this project");
                    errors.add(error);
                    index++;
                    continue;
                } else {
                    created++;
                    String newKey = "API-" + sequence.incrementAndGet();
                    linked.put(ref, newKey);
                    out.addProperty("case_key", newKey);
                    out.addProperty("action", "created");
                }
                cases.add(out);
                index++;
            }
            JsonObject response = new JsonObject();
            response.addProperty("created", created);
            response.addProperty("linked", linkedNow);
            response.addProperty("updated", 0);
            response.addProperty("unchanged", unchanged);
            response.add("errors", errors);
            response.add("cases", cases);
            reply(ex, 200, gson.toJson(response));
        }

        private void results(HttpExchange ex) throws IOException {
            resultCalls.incrementAndGet();
            JsonObject body = gson.fromJson(read(ex), JsonObject.class);
            boolean mayCreate = body.get("auto_create_cases").getAsBoolean();
            JsonArray unmatched = new JsonArray();
            int recorded = 0;
            for (var element : body.getAsJsonArray("results")) {
                JsonObject item = element.getAsJsonObject();
                String ref = item.get("automation_ref").getAsString();
                String key = item.has("case_key") ? item.get("case_key").getAsString() : null;
                if (linked.containsKey(ref) || (key != null && manualKeys.contains(key))) {
                    recorded++;
                    recordedRefs.add(ref);
                } else if (mayCreate) {
                    linked.put(ref, "API-" + sequence.incrementAndGet());
                    recorded++;
                    recordedRefs.add(ref);
                } else {
                    unmatched.add(ref);
                }
            }
            JsonObject response = new JsonObject();
            response.addProperty("plan_id", 1);
            response.addProperty("plan_name", body.getAsJsonObject("plan").get("name").getAsString());
            response.addProperty("recorded", recorded);
            response.addProperty("duplicates", 0);
            response.add("unmatched", unmatched);
            response.add("errors", new JsonArray());
            reply(ex, 201, gson.toJson(response));
        }

        private void evidence(HttpExchange ex) throws IOException {
            String body = read(ex);
            Matcher m = Pattern.compile("name=\"(automation_ref|case_key)\"\r\n\r\n([^\r]+)").matcher(body);
            boolean found = false;
            if (m.find()) {
                String field = m.group(1), value = m.group(2);
                found = field.equals("automation_ref") ? linked.containsKey(value) : manualKeys.contains(value) || linked.containsValue(value);
                if (found) {
                    evidenceRequests.add(field + "=" + value);
                }
            }
            reply(ex, found ? 201 : 404, found ? "{\"ok\":true}" : "{\"error\":\"Test case not found.\"}");
        }

        private static String read(HttpExchange ex) throws IOException {
            return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }

        private static void reply(HttpExchange ex, int status, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        }
    }

    private FakeTestDesk fake;
    private static final RunContext RUN = new RunContext("EAF API SIT", "SIT", null, null, "run-1");

    @BeforeEach
    void start() throws IOException {
        fake = new FakeTestDesk();
    }

    @AfterEach
    void stop() {
        fake.server.stop(0);
    }

    private TestDeskReporter reporter(boolean syncSpec, boolean autoCreate) {
        TestDeskClient client = new TestDeskClient(fake.url(), "tdk_x", Duration.ofSeconds(2), Duration.ofSeconds(5), 0);
        return new TestDeskReporter(client, new Settings("API", fake.url(), "Automation", syncSpec, autoCreate, false, "failed", 20L * 1024 * 1024, null));
    }

    private static ScenarioRecord record(String key, List<Evidence> evidence) {
        return new ScenarioRecord(key, "Scenario " + key, "Posts read", List.of("@tc:" + key),
                List.of(new StepText("When ", "I act"), new StepText("Then ", "it works")), "PASSED", 10, null, null, evidence);
    }

    @Test
    void anEmptyTestDeskGetsTheMissingTestCasesCreatedAndTheirResultsRecorded(@TempDir Path dir) throws Exception {
        Path shot = Files.writeString(dir.resolve("a.txt"), "x");
        Summary summary = reporter(true, true).report(
                List.of(record("API-001", List.of()), record("API-002", List.of(new Evidence("api-exchange.txt", "text/plain", shot)))), RUN);

        assertThat(fake.syncBodies).hasSize(2);
        JsonObject createBody = fake.syncBodies.get(1);
        assertThat(createBody.getAsJsonArray("cases")).hasSize(2);
        JsonObject first = createBody.getAsJsonArray("cases").get(0).getAsJsonObject();
        assertThat(first.has("case_key")).as("new cases are created without a requested key").isFalse();
        assertThat(first.get("suite").getAsString()).isEqualTo("Posts read");
        assertThat(first.getAsJsonArray("steps")).hasSize(1);
        assertThat(summary.created()).isEqualTo(2);
        assertThat(summary.linked()).isZero();
        assertThat(summary.recorded()).isEqualTo(2);
        assertThat(fake.recordedRefs).containsExactlyInAnyOrder("API-001", "API-002");
        assertThat(summary.evidenceFiles()).isEqualTo(1);
        assertThat(fake.evidenceRequests).containsExactly("automation_ref=API-002");
    }

    @Test
    void testCasesThatAlreadyExistAreLinkedNotDuplicatedAndOnlyGetResults() throws Exception {
        fake.manualKeys.addAll(List.of("API-001", "API-002"));
        Summary summary = reporter(true, true).report(List.of(record("API-001", List.of()), record("API-002", List.of())), RUN);

        assertThat(fake.syncBodies).as("nothing is missing, so no creation round").hasSize(1);
        assertThat(summary.linked()).isEqualTo(2);
        assertThat(summary.created()).isZero();
        assertThat(fake.sequence.get()).as("TestDesk created no test case").isZero();
        assertThat(fake.recordedRefs).containsExactlyInAnyOrder("API-001", "API-002");
    }

    @Test
    void onlyTheMissingTestCasesAreCreatedWhenSomeExist() throws Exception {
        fake.manualKeys.add("API-001");
        Summary summary = reporter(true, true).report(List.of(record("API-001", List.of()), record("API-002", List.of())), RUN);

        assertThat(fake.syncBodies).hasSize(2);
        JsonArray created = fake.syncBodies.get(1).getAsJsonArray("cases");
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getAsJsonObject().get("automation_ref").getAsString()).isEqualTo("API-002");
        assertThat(summary.linked()).isEqualTo(1);
        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.recorded()).isEqualTo(2);
    }

    @Test
    void aSecondRunCreatesNothingAndOnlyAddsExecutions() throws Exception {
        List<ScenarioRecord> records = List.of(record("API-001", List.of()), record("API-002", List.of()));
        reporter(true, true).report(records, RUN);
        int syncCallsAfterFirstRun = fake.syncBodies.size();
        fake.recordedRefs.clear();

        Summary second = reporter(true, true).report(records, new RunContext("EAF API SIT", "SIT", null, null, "run-2"));

        assertThat(fake.syncBodies.size() - syncCallsAfterFirstRun).as("one sync call, no creation round").isEqualTo(1);
        assertThat(second.created()).isZero();
        assertThat(second.linked()).isZero();
        assertThat(second.recorded()).isEqualTo(2);
        assertThat(fake.sequence.get()).as("no additional test case created").isEqualTo(2);
    }

    @Test
    void withAutoCreateOffMissingTestCasesAreReportedNotCreated() throws Exception {
        Summary summary = reporter(true, false).report(List.of(record("API-001", List.of()), record("API-002", List.of())), RUN);

        assertThat(fake.syncBodies).hasSize(1);
        assertThat(summary.created()).isZero();
        assertThat(summary.unmatched()).isEqualTo(2);
        assertThat(summary.recorded()).isZero();
        assertThat(fake.sequence.get()).isZero();
    }

    @Test
    void evidenceFallsBackToCaseKeyForImportedTestCasesThatWereNeverSynced(@TempDir Path dir) throws Exception {
        fake.manualKeys.add("API-001");
        Path file = Files.writeString(dir.resolve("a.txt"), "x");
        Summary summary = reporter(false, false).report(
                List.of(record("API-001", List.of(new Evidence("api-exchange.txt", "text/plain", file)))), RUN);

        assertThat(summary.recorded()).isEqualTo(1);
        assertThat(summary.evidenceFiles()).isEqualTo(1);
        assertThat(fake.evidenceRequests).containsExactly("case_key=API-001");
        assertThat(fake.syncBodies).as("spec sync is off").isEmpty();
    }

    @Test
    void whenSpecSyncIsOffTheResultsCallCreatesMissingTestCases() throws Exception {
        Summary summary = reporter(false, true).report(List.of(record("API-001", List.of())), RUN);

        assertThat(summary.recorded()).isEqualTo(1);
        assertThat(fake.sequence.get()).isEqualTo(1);
    }

    @Test
    void aRejectedTokenStopsTheWholeReport() {
        fake.syncStatus = 401;
        assertThatThrownBy(() -> reporter(true, true).report(List.of(record("API-001", List.of())), RUN))
                .isInstanceOfSatisfying(TestDeskException.class, e -> assertThat(e.status()).isEqualTo(401));
        assertThat(fake.resultCalls.get()).as("no results are sent after a fatal sync error").isZero();
    }
}
