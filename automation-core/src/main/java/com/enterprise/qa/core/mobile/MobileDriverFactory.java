package com.enterprise.qa.core.mobile;

import com.enterprise.qa.core.mobile.android.AndroidCapabilities;
import com.enterprise.qa.core.mobile.android.AndroidRuntime;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Creates an Appium driver. For Android it first prepares the environment for the configured {@code mobile.mode}
 * (see {@link AndroidRuntime}): Appium server, and an emulator or a connected phone.
 */
public final class MobileDriverFactory {

    private MobileDriverFactory() {
    }

    public static AppiumDriver create() {
        return create(MobileSettings.fromConfig());
    }

    public static AppiumDriver create(MobileSettings settings) {
        URL server = serverUrl(settings.appiumUrl());
        if (settings.isAndroid()) {
            AndroidRuntime.Target target = AndroidRuntime.prepare(settings);
            return new AndroidDriver(server, AndroidCapabilities.build(settings, target.serial()));
        }
        if (settings.platform().equals("ios")) {
            return new IOSDriver(server, iosOptions(settings));
        }
        throw new IllegalArgumentException("Unknown mobile.platform '" + settings.platform() + "' (android or ios)");
    }

    private static XCUITestOptions iosOptions(MobileSettings s) {
        XCUITestOptions options = new XCUITestOptions()
                .setDeviceName(s.deviceName().equals("Android Device") ? "iPhone 15" : s.deviceName())
                .setNewCommandTimeout(Duration.ofSeconds(s.commandTimeoutSec()));
        if (s.platformVersion() != null) {
            options.setPlatformVersion(s.platformVersion());
        }
        if (s.app() != null) {
            options.setApp(s.app());
        }
        if (s.udid() != null) {
            options.setUdid(s.udid());
        }
        return options;
    }

    private static URL serverUrl(String url) {
        try {
            return URI.create(url).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid appium.server.url '" + url + "'", e);
        }
    }
}
