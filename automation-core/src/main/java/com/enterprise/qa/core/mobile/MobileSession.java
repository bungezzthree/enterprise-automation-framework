package com.enterprise.qa.core.mobile;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.screenrecording.CanRecordScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The Appium session of this run. With {@code mobile.session.scope=run} (default) one session serves all scenarios and
 * the app is relaunched between them, which is much faster; with {@code scenario} every scenario gets a fresh session.
 * If the session or the device cannot be prepared, later scenarios fail fast instead of repeating a slow attempt.
 */
public final class MobileSession {

    private static final Logger LOG = LoggerFactory.getLogger(MobileSession.class);

    private static AppiumDriver driver;
    private static String appPackage;
    private static IllegalStateException failure;

    private MobileSession() {
    }

    public static MobileSettings settings() {
        return MobileSettings.fromConfig();
    }

    /** The driver of the current session, creating the session on first use. */
    public static synchronized AppiumDriver driver() {
        if (failure != null) {
            throw new IllegalStateException("The mobile session could not be started earlier in this run: " + failure.getMessage(), failure);
        }
        if (driver == null) {
            MobileSettings s = settings();
            try {
                driver = MobileDriverFactory.create(s);
                appPackage = s.appPackage() != null ? s.appPackage() : currentPackage(driver);
                LOG.info("Appium session {} started (app package {})", driver.getSessionId(), appPackage);
            } catch (RuntimeException e) {
                failure = e instanceof IllegalStateException ise ? ise : new IllegalStateException(e.getMessage(), e);
                throw failure;
            }
            return driver;
        }
        return driver;
    }

    /** The current driver, or null when no session was started. Never creates one. */
    public static synchronized AppiumDriver peek() {
        return driver;
    }

    /** Starts the scenario with the app on a clean login screen: a new session, or a relaunch of the running one. */
    public static synchronized AppiumDriver beginScenario() {
        boolean existing = driver != null;
        AppiumDriver d = driver();
        if (existing) {
            relaunchApp();
        }
        dismissSystemDialogs();
        return d;
    }

    /**
     * Presses "Wait" on Android's "... isn't responding" (ANR) dialogs. Emulators that are busy show them now and then,
     * and they cover the app so no element can be found. Returns how many dialogs were dismissed.
     */
    public static synchronized int dismissSystemDialogs() {
        if (driver == null) {
            return 0;
        }
        int dismissed = 0;
        try {
            for (int i = 0; i < 3; i++) {
                var wait = driver.findElements(AppiumBy.id("android:id/aerr_wait"));
                if (wait.isEmpty()) {
                    break;
                }
                wait.get(0).click();
                dismissed++;
                Thread.sleep(500);
            }
        } catch (RuntimeException e) {
            LOG.debug("Could not check for system dialogs: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (dismissed > 0) {
            LOG.warn("Dismissed {} 'not responding' system dialog(s); the emulator is overloaded (more RAM or a windowed emulator with GPU helps)", dismissed);
        }
        return dismissed;
    }

    /** Closes the app and opens it again; app data is kept, in-memory state starts over. */
    public static synchronized void relaunchApp() {
        AppiumDriver d = driver();
        if (appPackage == null) {
            return;
        }
        d.executeScript("mobile: terminateApp", Map.of("appId", appPackage));
        d.executeScript("mobile: activateApp", Map.of("appId", appPackage));
    }

    public static synchronized void startRecording() {
        if (driver instanceof CanRecordScreen recorder) {
            try {
                recorder.startRecordingScreen();
            } catch (RuntimeException e) {
                LOG.warn("Cannot start screen recording: {}", e.getMessage());
            }
        }
    }

    /** Stops recording and returns the video (mp4, base64), or null. */
    public static synchronized String stopRecording() {
        if (driver instanceof CanRecordScreen recorder) {
            try {
                return recorder.stopRecordingScreen();
            } catch (RuntimeException e) {
                LOG.warn("Cannot stop screen recording: {}", e.getMessage());
            }
        }
        return null;
    }

    public static synchronized void quit() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (RuntimeException e) {
                LOG.debug("Ignoring error while closing the Appium session: {}", e.getMessage());
            }
            driver = null;
        }
    }

    private static String currentPackage(AppiumDriver d) {
        return d instanceof AndroidDriver android ? android.getCurrentPackage() : null;
    }
}
