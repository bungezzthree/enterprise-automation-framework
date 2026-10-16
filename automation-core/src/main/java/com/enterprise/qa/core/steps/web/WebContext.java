package com.enterprise.qa.core.steps.web;

import com.enterprise.qa.core.web.UiDriver;
import com.enterprise.qa.core.web.UiDriverFactory;

/** Per-scenario browser session, created lazily and shared between step classes by PicoContainer. */
public class WebContext {

    private UiDriver driver;

    public UiDriver driver() {
        if (driver == null) {
            driver = UiDriverFactory.create();
        }
        return driver;
    }

    boolean isStarted() {
        return driver != null;
    }

    void quit() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }
}
