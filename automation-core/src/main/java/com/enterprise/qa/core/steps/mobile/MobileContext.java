package com.enterprise.qa.core.steps.mobile;

import com.enterprise.qa.core.mobile.MobileActions;
import com.enterprise.qa.core.mobile.MobileSession;
import io.appium.java_client.AppiumDriver;

import java.time.Duration;

/** Per-scenario access to the Appium session, shared between step classes by PicoContainer. */
public class MobileContext {

    public AppiumDriver driver() {
        return MobileSession.driver();
    }

    /** Explicit-wait helpers for the current session. */
    public MobileActions actions() {
        return new MobileActions(driver(), Duration.ofMillis(MobileSession.settings().waitMs()));
    }
}
