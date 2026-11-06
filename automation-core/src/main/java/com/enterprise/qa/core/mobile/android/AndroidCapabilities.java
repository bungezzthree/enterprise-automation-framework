package com.enterprise.qa.core.mobile.android;

import com.enterprise.qa.core.mobile.MobileSettings;
import io.appium.java_client.android.options.UiAutomator2Options;

import java.time.Duration;

/** Turns {@link MobileSettings} into UiAutomator2 capabilities. Pure, so it can be unit tested without a device. */
public final class AndroidCapabilities {

    private AndroidCapabilities() {
    }

    /**
     * @param serial the adb serial of the target device (emulator or phone); null lets Appium pick the only connected one
     */
    public static UiAutomator2Options build(MobileSettings s, String serial) {
        UiAutomator2Options options = new UiAutomator2Options()
                .setDeviceName(serial != null ? serial : s.deviceName())
                .setAutoGrantPermissions(true)
                .setNoReset(s.noReset())
                .setNewCommandTimeout(Duration.ofSeconds(s.commandTimeoutSec()))
                .setAdbExecTimeout(Duration.ofSeconds(60))
                .setUiautomator2ServerInstallTimeout(Duration.ofSeconds(120))
                .setUiautomator2ServerLaunchTimeout(Duration.ofSeconds(120))
                // Less animation means fewer flaky taps and faster scenarios.
                .setDisableWindowAnimation(true);
        if (serial != null) {
            options.setUdid(serial);
        }
        if (s.platformVersion() != null) {
            options.setPlatformVersion(s.platformVersion());
        }
        if (s.app() != null) {
            options.setApp(s.app());
        }
        if (s.appPackage() != null) {
            options.setAppPackage(s.appPackage());
        }
        if (s.appActivity() != null) {
            options.setAppActivity(s.appActivity());
        }
        if (s.appWaitActivity() != null) {
            options.setAppWaitActivity(s.appWaitActivity());
        }
        s.extraCapabilities().forEach((name, value) -> options.setCapability(name.contains(":") ? name : "appium:" + name, typed(value)));
        return options;
    }

    /** Capability values arrive as text; booleans and whole numbers are passed on with their real type. */
    static Object typed(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        if (value.matches("-?\\d{1,9}")) {
            return Integer.parseInt(value);
        }
        return value;
    }
}
