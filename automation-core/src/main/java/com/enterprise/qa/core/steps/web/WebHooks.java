package com.enterprise.qa.core.steps.web;

import com.enterprise.qa.core.config.Config;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Add {@code com.enterprise.qa.core.steps.web} to a web project's glue to get these hooks.
 * {@code evidence.mode}: {@code none}, {@code failed} (default) or {@code all} (also a final screenshot of passed scenarios).
 */
public class WebHooks {

    private static final Logger LOG = LoggerFactory.getLogger(WebHooks.class);

    private final WebContext web;

    public WebHooks(WebContext web) {
        this.web = web;
    }

    @After
    public void tearDown(Scenario scenario) {
        if (!web.isStarted()) {
            return;
        }
        try {
            String mode = Config.get("evidence.mode", "failed").toLowerCase(Locale.ROOT);
            if (!mode.equals("none") && (scenario.isFailed() || mode.equals("all"))) {
                scenario.attach(web.driver().screenshot(), "image/png", scenario.isFailed() ? "failure-screenshot" : "final-screenshot");
            }
        } catch (RuntimeException e) {
            LOG.warn("Cannot take a screenshot for '{}': {}", scenario.getName(), e.getMessage());
        } finally {
            web.quit();
        }
    }
}
