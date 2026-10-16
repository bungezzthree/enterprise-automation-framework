package com.enterprise.qa.core.steps.web;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;

/** Add {@code com.enterprise.qa.core.steps.web} to a web project's glue to get these hooks. */
public class WebHooks {

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
            if (scenario.isFailed()) {
                scenario.attach(web.driver().screenshot(), "image/png", "failure-screenshot");
            }
        } finally {
            web.quit();
        }
    }
}
