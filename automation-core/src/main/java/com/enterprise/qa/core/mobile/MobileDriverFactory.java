package com.enterprise.qa.core.mobile;

import com.enterprise.qa.core.config.Config;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Appium 2 driver factory for native/hybrid mobile suites (phase 2). Config keys:
 * {@code mobile.platform} (android | ios), {@code appium.server.url}, {@code mobile.device.name},
 * {@code mobile.platform.version}, {@code mobile.app} (path or URL to .apk/.ipa/.app),
 * {@code mobile.udid}, {@code mobile.timeout.ms}.
 */
public final class MobileDriverFactory {

    private MobileDriverFactory() {
    }

    public static AppiumDriver create() {
        String platform = Config.get("mobile.platform", "android").toLowerCase();
        URL server = serverUrl();
        Duration timeout = Duration.ofMillis(Config.getInt("mobile.timeout.ms", 15_000));
        AppiumDriver driver = switch (platform) {
            case "ios" -> new IOSDriver(server, iosOptions());
            case "android" -> new AndroidDriver(server, androidOptions());
            default -> throw new IllegalArgumentException("Unknown mobile.platform '" + platform + "' (android|ios)");
        };
        driver.manage().timeouts().implicitlyWait(timeout);
        return driver;
    }

    private static UiAutomator2Options androidOptions() {
        UiAutomator2Options options = new UiAutomator2Options()
                .setDeviceName(Config.get("mobile.device.name", "Android Emulator"))
                .setNewCommandTimeout(Duration.ofSeconds(120));
        String version = Config.get("mobile.platform.version");
        if (version != null) {
            options.setPlatformVersion(version);
        }
        String app = Config.get("mobile.app");
        if (app != null) {
            options.setApp(app);
        }
        String udid = Config.get("mobile.udid");
        if (udid != null) {
            options.setUdid(udid);
        }
        return options;
    }

    private static XCUITestOptions iosOptions() {
        XCUITestOptions options = new XCUITestOptions()
                .setDeviceName(Config.get("mobile.device.name", "iPhone 15"))
                .setNewCommandTimeout(Duration.ofSeconds(120));
        String version = Config.get("mobile.platform.version");
        if (version != null) {
            options.setPlatformVersion(version);
        }
        String app = Config.get("mobile.app");
        if (app != null) {
            options.setApp(app);
        }
        String udid = Config.get("mobile.udid");
        if (udid != null) {
            options.setUdid(udid);
        }
        return options;
    }

    private static URL serverUrl() {
        String url = Config.get("appium.server.url", "http://127.0.0.1:4723");
        try {
            return URI.create(url).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid appium.server.url " + url, e);
        }
    }
}
