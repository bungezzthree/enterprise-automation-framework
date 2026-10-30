package com.enterprise.qa.core.testdesk;

import com.enterprise.qa.core.testdesk.ScenarioRecord.StepText;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Maps finished scenarios to the JSON that the TestDesk automation API expects. No I/O. */
public final class TestDeskPayloads {

    /** Execution metadata shared by every result of one run. */
    public record RunContext(String planName, String environment, String build, String ciUrl, String runId) {
    }

    static final int MAX_OUTPUT_CHARS = 20_000;
    private static final Set<String> TECHNICAL_TAG_PREFIXES = Set.of("@tc:", "@priority:", "@req:", "@defect:");
    private static final Set<String> PRIORITIES = Set.of("critical", "high", "medium", "low");

    private TestDeskPayloads() {
    }

    /** Cucumber status name to TestDesk status (design decision K1: undefined/pending are "blocked"). */
    public static String status(String cucumberStatus) {
        return switch (cucumberStatus) {
            case "PASSED" -> "passed";
            case "FAILED", "AMBIGUOUS" -> "failed";
            case "UNDEFINED", "PENDING" -> "blocked";
            default -> "skipped";
        };
    }

    /** Priority from {@code @priority:high}, capitalised for TestDesk, or empty when absent or unknown. */
    public static Optional<String> priority(Collection<String> tags) {
        return tagValue(tags, "@priority:")
                .map(v -> v.toLowerCase(Locale.ROOT))
                .filter(PRIORITIES::contains)
                .map(v -> Character.toUpperCase(v.charAt(0)) + v.substring(1));
    }

    /** Value after a tag prefix, e.g. {@code tagValue(tags, "@req:")} for {@code @req:FSD-1}. */
    public static Optional<String> tagValue(Collection<String> tags, String prefix) {
        return tags.stream()
                .filter(t -> t.regionMatches(true, 0, prefix, 0, prefix.length()))
                .map(t -> t.substring(prefix.length()))
                .filter(v -> !v.isBlank())
                .findFirst();
    }

    /** Descriptive tags as a comma separated list, without {@code @} and without technical tags. */
    public static String tags(Collection<String> tags) {
        return tags.stream()
                .filter(t -> TECHNICAL_TAG_PREFIXES.stream().noneMatch(p -> t.toLowerCase(Locale.ROOT).startsWith(p)))
                .map(t -> t.startsWith("@") ? t.substring(1) : t)
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * Gherkin steps to TestDesk steps: Given/When/And become actions; a Then (and the And/But that follow it)
     * becomes the expected result of the last action.
     */
    public static JsonArray steps(List<StepText> steps) {
        List<JsonObject> out = new ArrayList<>();
        boolean inThen = false;
        for (StepText step : steps) {
            String keyword = step.keyword().trim();
            String line = (keyword.isEmpty() ? "" : keyword + " ") + step.text();
            if (keyword.equals("Then")) {
                inThen = true;
                appendExpected(out, step.text());
            } else if (inThen && (keyword.equals("And") || keyword.equals("But") || keyword.equals("*"))) {
                appendExpected(out, step.text());
            } else {
                inThen = false;
                JsonObject action = new JsonObject();
                action.addProperty("action", line);
                out.add(action);
            }
        }
        JsonArray array = new JsonArray();
        out.forEach(array::add);
        return array;
    }

    private static void appendExpected(List<JsonObject> out, String text) {
        if (out.isEmpty()) {
            JsonObject placeholder = new JsonObject();
            placeholder.addProperty("action", "-");
            out.add(placeholder);
        }
        JsonObject last = out.get(out.size() - 1);
        String existing = last.has("expected_result") ? last.get("expected_result").getAsString() : "";
        last.addProperty("expected_result", existing.isEmpty() ? text : existing + "; " + text);
    }

    /**
     * Scenario Outlines produce several scenarios with the same {@code @tc}. TestDesk wants one entry per test case,
     * so they are merged: the worst status wins, durations add up, evidence is combined.
     */
    public static List<ScenarioRecord> mergeByKey(List<ScenarioRecord> records) {
        Map<String, ScenarioRecord> merged = new LinkedHashMap<>();
        for (ScenarioRecord r : records) {
            merged.merge(r.key(), r, TestDeskPayloads::combine);
        }
        return new ArrayList<>(merged.values());
    }

    private static ScenarioRecord combine(ScenarioRecord a, ScenarioRecord b) {
        boolean bWorse = severity(b.status()) > severity(a.status());
        ScenarioRecord worse = bWorse ? b : a;
        List<ScenarioRecord.Evidence> evidence = new ArrayList<>(a.evidence());
        evidence.addAll(b.evidence());
        return new ScenarioRecord(a.key(), a.name(), a.tags(), a.steps(), worse.status(), a.durationMs() + b.durationMs(),
                worse.message() != null ? worse.message() : a.message(), worse.output() != null ? worse.output() : a.output(), evidence);
    }

    private static int severity(String cucumberStatus) {
        return switch (status(cucumberStatus)) {
            case "failed" -> 3;
            case "blocked" -> 2;
            case "skipped" -> 1;
            default -> 0;
        };
    }

    /** Body for {@code PUT /api/projects/{prefix}/automation/cases}. */
    public static JsonObject syncBody(List<ScenarioRecord> records, String suiteRoot, boolean dryRun) {
        JsonArray cases = new JsonArray();
        for (ScenarioRecord r : records) {
            JsonObject c = new JsonObject();
            c.addProperty("automation_ref", r.key());
            c.addProperty("case_key", r.key());
            c.addProperty("title", r.name());
            priority(r.tags()).ifPresent(p -> c.addProperty("priority", p));
            String tags = tags(r.tags());
            if (!tags.isEmpty()) {
                c.addProperty("tags", tags);
            }
            tagValue(r.tags(), "@req:").ifPresent(v -> c.addProperty("requirement_ref", v));
            c.add("steps", steps(r.steps()));
            cases.add(c);
        }
        JsonObject body = new JsonObject();
        body.addProperty("suite_root", suiteRoot);
        body.addProperty("obsolete_missing", false);
        body.addProperty("dry_run", dryRun);
        body.add("cases", cases);
        return body;
    }

    /** Body for {@code POST /api/projects/{prefix}/automation/results}. Never auto-creates cases (design decision D3). */
    public static JsonObject resultsBody(List<ScenarioRecord> records, RunContext run, boolean dryRun) {
        JsonArray results = new JsonArray();
        for (ScenarioRecord r : records) {
            JsonObject item = new JsonObject();
            item.addProperty("automation_ref", r.key());
            item.addProperty("case_key", r.key());
            item.addProperty("status", status(r.status()));
            item.addProperty("duration_ms", r.durationMs());
            if (r.message() != null && !r.message().isBlank()) {
                item.addProperty("message", r.message());
            }
            if (r.output() != null && !r.output().isBlank()) {
                item.addProperty("output", truncate(r.output(), MAX_OUTPUT_CHARS));
            }
            tagValue(r.tags(), "@defect:").ifPresent(v -> item.addProperty("defect_ref", v));
            results.add(item);
        }
        JsonObject plan = new JsonObject();
        plan.addProperty("name", run.planName());
        plan.addProperty("environment", run.environment());
        if (run.build() != null) {
            plan.addProperty("build_version", run.build());
        }
        JsonObject body = new JsonObject();
        body.add("plan", plan);
        if (run.build() != null) {
            body.addProperty("build", run.build());
        }
        if (run.ciUrl() != null) {
            body.addProperty("ci_url", run.ciUrl());
        }
        body.addProperty("run_id", run.runId());
        body.addProperty("auto_create_cases", false);
        body.addProperty("dry_run", dryRun);
        body.add("results", results);
        return body;
    }

    static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "\n... (dipotong, " + (text.length() - max) + " karakter lagi)";
    }
}
