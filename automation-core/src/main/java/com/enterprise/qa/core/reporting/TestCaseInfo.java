package com.enterprise.qa.core.reporting;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/** A test case as it is stored in the metrics database. */
public record TestCaseInfo(String key, String title, boolean regression, boolean sanity, boolean newFeature,
                           boolean automated, String priority, String testlinkId) {

    public static final String TC_TAG_PREFIX = "@tc:";

    /**
     * Builds an automated test case from a scenario's Gherkin tags:
     * {@code @tc:API-001} (identity), {@code @regression}, {@code @sanity}, {@code @newfeature}.
     */
    public static TestCaseInfo fromScenario(String scenarioName, Collection<String> tags) {
        String key = tcKey(tags).orElse(null);
        return new TestCaseInfo(
                key,
                scenarioName,
                hasTag(tags, "@regression"),
                hasTag(tags, "@sanity"),
                hasTag(tags, "@newfeature"),
                true,
                tags.stream().filter(t -> t.startsWith("@priority:")).map(t -> t.substring(10)).findFirst().orElse(null),
                null);
    }

    public static Optional<String> tcKey(Collection<String> tags) {
        return tags.stream()
                .filter(t -> t.toLowerCase(Locale.ROOT).startsWith(TC_TAG_PREFIX))
                .map(t -> t.substring(TC_TAG_PREFIX.length()))
                .findFirst();
    }

    private static boolean hasTag(Collection<String> tags, String tag) {
        return tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag));
    }
}
