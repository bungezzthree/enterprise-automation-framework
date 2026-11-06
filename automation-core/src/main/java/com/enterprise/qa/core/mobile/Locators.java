package com.enterprise.qa.core.mobile;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * Locator factories for Android screen objects, from most to least preferred:
 * accessibility id (content-desc), resource id, visible text, XPath (last resort: slow and fragile).
 */
public final class Locators {

    private Locators() {
    }

    /** Accessibility id: the {@code content-desc} on Android. The best choice when developers set it. */
    public static By acc(String accessibilityId) {
        return AppiumBy.accessibilityId(accessibilityId);
    }

    /** Resource id, e.g. {@code com.example.app:id/login_button}. */
    public static By id(String resourceId) {
        return AppiumBy.id(resourceId);
    }

    /** Element whose visible text is exactly {@code text}. */
    public static By text(String text) {
        return AppiumBy.androidUIAutomator("new UiSelector().text(\"" + escape(text) + "\")");
    }

    /** Element whose visible text contains {@code text}. */
    public static By textContains(String text) {
        return AppiumBy.androidUIAutomator("new UiSelector().textContains(\"" + escape(text) + "\")");
    }

    public static By xpath(String xpath) {
        return AppiumBy.xpath(xpath);
    }

    /** UiSelector strings are double quoted Java-style literals. */
    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
