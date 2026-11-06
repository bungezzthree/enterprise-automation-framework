package com.enterprise.qa.core.mobile;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * Explicit-wait helpers for screen objects, the mobile counterpart of {@code UiDriver}. Every action waits (up to
 * {@code mobile.timeout.ms}) for its element, so screen objects never need sleeps.
 */
public final class MobileActions {

    private final AppiumDriver driver;
    private final WebDriverWait wait;

    public MobileActions(AppiumDriver driver, Duration timeout) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, timeout);
    }

    public WebElement visible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    public void tap(By by) {
        wait.until(ExpectedConditions.elementToBeClickable(by)).click();
    }

    public void type(By by, String text) {
        WebElement field = visible(by);
        field.click();
        field.clear();
        field.sendKeys(text);
    }

    public String text(By by) {
        return visible(by).getText().trim();
    }

    public List<String> texts(By by) {
        visible(by);
        return driver.findElements(by).stream().map(e -> e.getText().trim()).toList();
    }

    /** Waits for the element to appear; false if it never does. */
    public boolean isDisplayed(By by) {
        try {
            visible(by);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Waits for the element to disappear; false if it is still there. */
    public boolean isGone(By by) {
        try {
            return wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
        } catch (TimeoutException e) {
            return false;
        }
    }

    public int count(By by) {
        return driver.findElements(by).size();
    }

    /** Scrolls a vertical list until an element with this text is on screen. */
    public void scrollToText(String text) {
        scrollInto("textContains", text);
    }

    /** Scrolls a vertical list until an element with this accessibility id is on screen. */
    public void scrollToAccessibilityId(String accessibilityId) {
        scrollInto("description", accessibilityId);
    }

    private void scrollInto(String selector, String value) {
        driver.findElement(AppiumBy.androidUIAutomator("new UiScrollable(new UiSelector().scrollable(true).instance(0))"
                + ".scrollIntoView(new UiSelector()." + selector + "(\"" + Locators.escape(value) + "\"))"));
    }

    public void hideKeyboardIfShown() {
        if (driver instanceof AndroidDriver android && android.isKeyboardShown()) {
            android.hideKeyboard();
        }
    }

    public void back() {
        driver.navigate().back();
    }
}
