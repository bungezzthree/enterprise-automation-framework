package com.enterprise.qa.core.reporting;

import com.enterprise.qa.core.config.Config;

/** Identity of the automation project a test run belongs to, read from {@code project.*} config keys. */
public record ProjectInfo(String key, String name, String type, String description) {

    public static ProjectInfo fromConfig() {
        return new ProjectInfo(
                Config.require("project.key"),
                Config.require("project.name"),
                Config.get("project.type", "WEB").toUpperCase(),
                Config.get("project.description"));
    }
}
