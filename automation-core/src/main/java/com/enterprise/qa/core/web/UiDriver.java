package com.enterprise.qa.core.web;

import java.util.List;

/**
 * Engine-neutral browser API used by page objects. Locators are CSS selectors, which both
 * Playwright and Selenium support, so a suite can switch engines with {@code -Dweb.engine=selenium}.
 * Implementations wait for elements up to {@code web.timeout.ms} before acting.
 */
public interface UiDriver extends AutoCloseable {

    void open(String pathOrUrl);

    void click(String css);

    void type(String css, String text);

    void selectByValue(String css, String value);

    String text(String css);

    List<String> texts(String css);

    int count(String css);

    /** Waits up to the configured timeout for the element to appear; false if it never does. */
    boolean isVisible(String css);

    /** Waits up to the configured timeout for the element to disappear; false if it is still shown. */
    boolean waitUntilHidden(String css);

    String currentUrl();

    byte[] screenshot();

    String engineName();

    @Override
    void close();
}
