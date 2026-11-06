package com.enterprise.qa.core.steps.mobile;

import com.enterprise.qa.core.config.Config;
import com.enterprise.qa.core.mobile.MobileSession;
import com.enterprise.qa.core.mobile.MobileSettings;
import io.appium.java_client.AppiumDriver;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Add {@code com.enterprise.qa.core.steps.mobile} to an Android project's glue to get these hooks.
 * Before a scenario the app is put on a clean start screen (new session, or relaunch of the running one).
 * After it, evidence is attached according to {@code evidence.mode}: {@code none}, {@code failed} (default) or
 * {@code all}. A failed scenario also gets the UI hierarchy (to fix locators) and the last logcat lines;
 * {@code mobile.record.video=true} adds a screen recording.
 */
public class MobileHooks {

    private static final Logger LOG = LoggerFactory.getLogger(MobileHooks.class);
    private static final int LOGCAT_LINES = 300;

    @Before(order = 100)
    public void beginScenario(Scenario scenario) {
        MobileSession.beginScenario();
        if (MobileSession.settings().recordVideo()) {
            MobileSession.startRecording();
        }
    }

    @After(order = 100)
    public void endScenario(Scenario scenario) {
        MobileSettings settings = MobileSession.settings();
        AppiumDriver driver = MobileSession.peek();
        if (driver == null) {
            return; // the session never started; the failure is already reported by the scenario itself
        }
        try {
            String mode = Config.get("evidence.mode", "failed").toLowerCase(Locale.ROOT);
            boolean capture = !mode.equals("none") && (scenario.isFailed() || mode.equals("all"));
            if (capture) {
                attachScreenshot(scenario, driver);
                if (scenario.isFailed()) {
                    attachPageSource(scenario, driver);
                    attachLogcat(scenario, driver);
                }
            }
            String video = settings.recordVideo() ? MobileSession.stopRecording() : null;
            if (video != null && capture) {
                scenario.attach(Base64.getMimeDecoder().decode(video), "video/mp4", "scenario-video");
            }
        } catch (RuntimeException e) {
            LOG.warn("Cannot collect evidence for '{}': {}", scenario.getName(), e.getMessage());
        } finally {
            if (settings.sessionScope() == MobileSettings.SessionScope.SCENARIO) {
                MobileSession.quit();
            }
        }
    }

    private static void attachScreenshot(Scenario scenario, AppiumDriver driver) {
        byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        scenario.attach(png, "image/png", scenario.isFailed() ? "failure-screenshot" : "final-screenshot");
    }

    private static void attachPageSource(Scenario scenario, AppiumDriver driver) {
        try {
            scenario.attach(driver.getPageSource().getBytes(StandardCharsets.UTF_8), "application/xml", "page-source");
        } catch (RuntimeException e) {
            LOG.debug("No page source: {}", e.getMessage());
        }
    }

    private static void attachLogcat(Scenario scenario, AppiumDriver driver) {
        try {
            LogEntries entries = driver.manage().logs().get("logcat");
            List<String> lines = new ArrayList<>();
            for (LogEntry entry : entries) {
                lines.add(entry.getMessage());
            }
            List<String> tail = lines.subList(Math.max(0, lines.size() - LOGCAT_LINES), lines.size());
            if (!tail.isEmpty()) {
                scenario.attach(String.join("\n", tail).getBytes(StandardCharsets.UTF_8), "text/plain", "logcat");
            }
        } catch (RuntimeException e) {
            LOG.debug("No logcat: {}", e.getMessage());
        }
    }
}
