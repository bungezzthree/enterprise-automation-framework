package com.enterprise.qa.core.testdesk;

import com.enterprise.qa.core.testdesk.ScenarioRecord.StepText;
import com.enterprise.qa.core.testdesk.TestDeskPayloads.RunContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDeskPayloadsTest {

    private static ScenarioRecord record(String key, String status, long ms, String message, String... tags) {
        return new ScenarioRecord(key, "Scenario " + key, "Posts read", List.of(tags),
                List.of(new StepText("Given ", "a precondition"), new StepText("When ", "I act"), new StepText("Then ", "it works")),
                status, ms, message, null, List.of());
    }

    @Test
    void mapsCucumberStatusesToTestDeskStatuses() {
        assertThat(TestDeskPayloads.status("PASSED")).isEqualTo("passed");
        assertThat(TestDeskPayloads.status("FAILED")).isEqualTo("failed");
        assertThat(TestDeskPayloads.status("AMBIGUOUS")).isEqualTo("failed");
        assertThat(TestDeskPayloads.status("UNDEFINED")).isEqualTo("blocked");
        assertThat(TestDeskPayloads.status("PENDING")).isEqualTo("blocked");
        assertThat(TestDeskPayloads.status("SKIPPED")).isEqualTo("skipped");
    }

    @Test
    void readsPriorityFromTagCaseInsensitively() {
        assertThat(TestDeskPayloads.priority(List.of("@priority:high"))).contains("High");
        assertThat(TestDeskPayloads.priority(List.of("@PRIORITY:Critical"))).contains("Critical");
        assertThat(TestDeskPayloads.priority(List.of("@priority:urgent"))).isEmpty();
        assertThat(TestDeskPayloads.priority(List.of("@regression"))).isEmpty();
    }

    @Test
    void keepsOnlyDescriptiveTags() {
        List<String> tags = List.of("@api", "@posts", "@tc:API-011", "@newfeature", "@priority:low", "@req:FSD-1", "@defect:JIRA-9");
        assertThat(TestDeskPayloads.tags(tags)).isEqualTo("api,posts,newfeature");
    }

    @Test
    void thenStepsBecomeExpectedResultOfTheLastAction() {
        JsonArray steps = TestDeskPayloads.steps(List.of(
                new StepText("Given ", "the query parameter \"_page\" is \"2\""),
                new StepText("When ", "I send a GET request to \"/posts\""),
                new StepText("Then ", "the response status code should be 200"),
                new StepText("And ", "the response should be a list of 5 items")));
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getAsJsonObject().get("action").getAsString()).isEqualTo("Given the query parameter \"_page\" is \"2\"");
        assertThat(steps.get(0).getAsJsonObject().has("expected_result")).isFalse();
        JsonObject second = steps.get(1).getAsJsonObject();
        assertThat(second.get("action").getAsString()).isEqualTo("When I send a GET request to \"/posts\"");
        assertThat(second.get("expected_result").getAsString())
                .isEqualTo("the response status code should be 200; the response should be a list of 5 items");
    }

    @Test
    void anAndAfterAWhenIsAnotherAction() {
        JsonArray steps = TestDeskPayloads.steps(List.of(
                new StepText("When ", "I add the backpack"), new StepText("And ", "I open the cart"), new StepText("Then ", "it is listed")));
        assertThat(steps).hasSize(2);
        assertThat(steps.get(1).getAsJsonObject().get("action").getAsString()).isEqualTo("And I open the cart");
    }

    @Test
    void syncBodyCarriesKeyTitlePriorityTagsAndSteps() {
        ScenarioRecord r = record("API-011", "PASSED", 100, null, "@api", "@tc:API-011", "@newfeature", "@priority:low", "@req:FSD-9");
        JsonObject body = TestDeskPayloads.syncBody(List.of(r), "Automation", false, true, false);
        assertThat(body.get("suite_root").getAsString()).isEqualTo("Automation");
        assertThat(body.get("obsolete_missing").getAsBoolean()).isFalse();
        JsonObject c = body.getAsJsonArray("cases").get(0).getAsJsonObject();
        assertThat(c.get("automation_ref").getAsString()).isEqualTo("API-011");
        assertThat(c.get("case_key").getAsString()).isEqualTo("API-011");
        assertThat(c.get("title").getAsString()).isEqualTo("Scenario API-011");
        assertThat(c.get("priority").getAsString()).isEqualTo("Low");
        assertThat(c.get("tags").getAsString()).isEqualTo("api,newfeature");
        assertThat(c.get("requirement_ref").getAsString()).isEqualTo("FSD-9");
        assertThat(c.getAsJsonArray("steps")).hasSize(2);
    }

    @Test
    void newTestCasesAreSyncedWithoutCaseKeyAndUnderAFeatureSuite() {
        ScenarioRecord r = record("API-011", "PASSED", 100, null, "@api", "@tc:API-011");
        JsonObject c = TestDeskPayloads.syncBody(List.of(r), "Automation", false, false, true).getAsJsonArray("cases").get(0).getAsJsonObject();
        assertThat(c.has("case_key")).isFalse();
        assertThat(c.get("automation_ref").getAsString()).isEqualTo("API-011");
        assertThat(c.get("suite").getAsString()).isEqualTo("Posts read");
    }

    @Test
    void existingTestCasesAreNeverMovedBecauseNoSuiteIsSentWhenLinking() {
        ScenarioRecord r = record("API-011", "PASSED", 100, null, "@tc:API-011");
        JsonObject c = TestDeskPayloads.syncBody(List.of(r), "Automation", false, true, false).getAsJsonArray("cases").get(0).getAsJsonObject();
        assertThat(c.has("suite")).isFalse();
        assertThat(c.get("case_key").getAsString()).isEqualTo("API-011");
    }

    @Test
    void resultsCanCarryTitleTagsAndPriorityWhenTheyMayCreateCases() {
        ScenarioRecord r = record("API-011", "PASSED", 100, null, "@api", "@tc:API-011", "@priority:high");
        JsonObject body = TestDeskPayloads.resultsBody(List.of(r), new RunContext("p", "SIT", null, null, "r"), false, true, "");
        JsonObject item = body.getAsJsonArray("results").get(0).getAsJsonObject();
        assertThat(body.get("auto_create_cases").getAsBoolean()).isTrue();
        assertThat(item.get("title").getAsString()).isEqualTo("Scenario API-011");
        assertThat(item.get("tags").getAsString()).isEqualTo("api");
        assertThat(item.get("priority").getAsString()).isEqualTo("High");
        assertThat(item.get("suite").getAsString()).isEqualTo("Posts read");
        assertThat(body.get("suite_root").getAsString()).isEmpty();
    }

    @Test
    void thereIsNoWrapperFolderByDefaultAndAnUnknownFeatureFallsBackToGeneral() {
        ScenarioRecord known = record("API-011", "PASSED", 1, null, "@tc:API-011");
        ScenarioRecord unknown = new ScenarioRecord("API-012", "n", "", List.of("@tc:API-012"), List.of(), "PASSED", 1, null, null, List.of());
        JsonObject body = TestDeskPayloads.syncBody(List.of(known, unknown), "", false, false, true);
        assertThat(body.get("suite_root").getAsString()).isEmpty();
        assertThat(body.getAsJsonArray("cases").get(0).getAsJsonObject().get("suite").getAsString()).isEqualTo("Posts read");
        assertThat(body.getAsJsonArray("cases").get(1).getAsJsonObject().get("suite").getAsString()).isEqualTo("General");
    }

    @Test
    void featureTitleComesFromTheFileName() {
        assertThat(TestDeskPayloads.featureTitle("classpath:features/posts_read.feature")).isEqualTo("Posts read");
        assertThat(TestDeskPayloads.featureTitle("features/user-profile.feature")).isEqualTo("User profile");
        assertThat(TestDeskPayloads.featureTitle(null)).isEmpty();
    }

    @Test
    void resultsBodyNeverAutoCreatesCases() {
        RunContext run = new RunContext("EAF API-X SIT", "SIT", "123", "http://ci/123", "jenkins-123");
        JsonObject body = TestDeskPayloads.resultsBody(
                List.of(record("API-001", "PASSED", 50, null, "@tc:API-001"), record("API-002", "FAILED", 70, "expected 200 but was 404", "@tc:API-002", "@defect:JIRA-5")),
                run, false);
        assertThat(body.get("auto_create_cases").getAsBoolean()).isFalse();
        assertThat(body.get("run_id").getAsString()).isEqualTo("jenkins-123");
        assertThat(body.get("ci_url").getAsString()).isEqualTo("http://ci/123");
        assertThat(body.getAsJsonObject("plan").get("name").getAsString()).isEqualTo("EAF API-X SIT");
        assertThat(body.getAsJsonObject("plan").get("environment").getAsString()).isEqualTo("SIT");
        JsonArray results = body.getAsJsonArray("results");
        assertThat(results).hasSize(2);
        JsonObject failed = results.get(1).getAsJsonObject();
        assertThat(failed.get("status").getAsString()).isEqualTo("failed");
        assertThat(failed.get("message").getAsString()).isEqualTo("expected 200 but was 404");
        assertThat(failed.get("defect_ref").getAsString()).isEqualTo("JIRA-5");
        assertThat(failed.get("duration_ms").getAsLong()).isEqualTo(70);
    }

    @Test
    void omitsOptionalRunFieldsWhenAbsent() {
        RunContext run = new RunContext("Plan", "SIT", null, null, "local-1");
        JsonObject body = TestDeskPayloads.resultsBody(List.of(record("API-001", "PASSED", 1, null, "@tc:API-001")), run, true);
        assertThat(body.has("build")).isFalse();
        assertThat(body.has("ci_url")).isFalse();
        assertThat(body.getAsJsonObject("plan").has("build_version")).isFalse();
        assertThat(body.get("dry_run").getAsBoolean()).isTrue();
    }

    @Test
    void longOutputIsTruncated() {
        ScenarioRecord r = new ScenarioRecord("API-001", "n", "", List.of(), List.of(), "FAILED", 1, "m", "x".repeat(30_000), List.of());
        JsonObject body = TestDeskPayloads.resultsBody(List.of(r), new RunContext("p", "SIT", null, null, "r"), false);
        String output = body.getAsJsonArray("results").get(0).getAsJsonObject().get("output").getAsString();
        assertThat(output).startsWith("xxx").contains("dipotong").hasSizeLessThan(20_100);
    }

    @Test
    void recordsAreSortedByKeySoParallelRunsGiveStableNumbering() {
        List<ScenarioRecord> sorted = TestDeskPayloads.mergeByKey(List.of(
                record("WEB-003", "PASSED", 1, null, "@tc:WEB-003"),
                record("WEB-001", "PASSED", 1, null, "@tc:WEB-001"),
                record("WEB-002", "PASSED", 1, null, "@tc:WEB-002")));
        assertThat(sorted).extracting(ScenarioRecord::key).containsExactly("WEB-001", "WEB-002", "WEB-003");
    }

    @Test
    void scenarioOutlineRowsAreMergedWithTheWorstStatus() {
        List<ScenarioRecord> merged = TestDeskPayloads.mergeByKey(List.of(
                record("WEB-001", "PASSED", 100, null, "@tc:WEB-001"),
                record("WEB-001", "FAILED", 200, "row 2 failed", "@tc:WEB-001"),
                record("WEB-002", "PASSED", 50, null, "@tc:WEB-002")));
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).status()).isEqualTo("FAILED");
        assertThat(merged.get(0).durationMs()).isEqualTo(300);
        assertThat(merged.get(0).message()).isEqualTo("row 2 failed");
        assertThat(merged.get(1).key()).isEqualTo("WEB-002");
    }
}
