package com.enterprise.qa.core.reporting;

import com.enterprise.qa.core.config.Config;

import java.time.Instant;

/** Execution metadata for one test run; detects Jenkins through its standard environment variables. */
public record RunInfo(Instant startedAt, String environment, String triggerSource, String buildNumber,
                      String tagFilter, String engine, String executedBy) {

    public static RunInfo current(Instant startedAt) {
        boolean jenkins = System.getenv("JENKINS_URL") != null;
        return new RunInfo(
                startedAt,
                Config.env(),
                jenkins ? "JENKINS" : "LOCAL",
                System.getenv("BUILD_NUMBER"),
                Config.get("cucumber.filter.tags"),
                Config.get("run.engine.label", engineLabel()),
                System.getProperty("user.name"));
    }

    private static String engineLabel() {
        return switch (Config.get("project.type", "WEB").toUpperCase()) {
            case "API" -> "rest-assured";
            case "MOBILE" -> "appium/" + Config.get("mobile.platform", "android");
            default -> Config.get("web.engine", "playwright") + "/" + Config.get("browser", "chromium");
        };
    }
}
