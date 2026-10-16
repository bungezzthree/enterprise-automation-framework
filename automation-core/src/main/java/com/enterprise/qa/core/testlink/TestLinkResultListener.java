package com.enterprise.qa.core.testlink;

import com.enterprise.qa.core.config.Config;
import com.enterprise.qa.core.reporting.TestCaseInfo;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseFinished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pushes each scenario result to TestLink when {@code testlink.enabled=true}.
 * The TestLink external id is taken from {@code @testlink:<id>} or, failing that, the {@code @tc:<id>} tag.
 * Required config: {@code testlink.url}, {@code testlink.devkey}, {@code testlink.testplan.id}, {@code testlink.build}.
 */
public class TestLinkResultListener implements ConcurrentEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestLinkResultListener.class);

    private TestLinkClient client;

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        if (!Config.getBool("testlink.enabled", false)) {
            return;
        }
        client = new TestLinkClient(Config.require("testlink.url"), Config.require("testlink.devkey"));
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        LOG.info("TestLink reporting enabled -> {}", Config.get("testlink.url"));
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        var tags = event.getTestCase().getTags();
        String externalId = tags.stream().filter(t -> t.startsWith("@testlink:")).map(t -> t.substring(10))
                .findFirst().orElse(TestCaseInfo.tcKey(tags).orElse(null));
        if (externalId == null) {
            return;
        }
        Status status = event.getResult().getStatus();
        String tlStatus = switch (status) {
            case PASSED -> "p";
            case FAILED, AMBIGUOUS -> "f";
            default -> "b";
        };
        String notes = "Automated run (" + Config.env() + ")"
                + (event.getResult().getError() != null ? ": " + event.getResult().getError().getMessage() : "");
        try {
            client.reportTestCaseResult(Config.require("testlink.testplan.id"),
                    Config.get("testlink.build", "automation"), externalId, tlStatus, notes);
        } catch (Exception e) {
            LOG.warn("TestLink update failed for {}: {}", externalId, e.getMessage());
        }
    }
}
