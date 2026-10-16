package com.enterprise.qa.core.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;

public class PlaywrightUiDriver implements UiDriver {

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;
    private final String baseUrl;
    private final int timeoutMs;
    private final String browserName;

    public PlaywrightUiDriver(String browserName, boolean headless, String baseUrl, int timeoutMs) {
        this.browserName = browserName;
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.playwright = Playwright.create();
        BrowserType type = switch (browserName.toLowerCase()) {
            case "firefox" -> playwright.firefox();
            case "webkit", "safari" -> playwright.webkit();
            default -> playwright.chromium();
        };
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
        if ("chrome".equalsIgnoreCase(browserName) || "msedge".equalsIgnoreCase(browserName)) {
            options.setChannel(browserName.toLowerCase());
        }
        this.browser = type.launch(options);
        this.context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1366, 768));
        this.context.setDefaultTimeout(timeoutMs);
        this.page = context.newPage();
    }

    @Override
    public void open(String pathOrUrl) {
        page.navigate(WebUrls.resolve(baseUrl, pathOrUrl));
    }

    @Override
    public void click(String css) {
        page.locator(css).first().click();
    }

    @Override
    public void type(String css, String text) {
        page.locator(css).first().fill(text);
    }

    @Override
    public void selectByValue(String css, String value) {
        page.locator(css).first().selectOption(value);
    }

    @Override
    public String text(String css) {
        return page.locator(css).first().innerText().trim();
    }

    @Override
    public List<String> texts(String css) {
        Locator locator = page.locator(css);
        locator.first().waitFor();
        return locator.allInnerTexts().stream().map(String::trim).toList();
    }

    @Override
    public int count(String css) {
        return page.locator(css).count();
    }

    @Override
    public boolean isVisible(String css) {
        try {
            page.locator(css).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    @Override
    public boolean waitUntilHidden(String css) {
        try {
            page.locator(css).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(timeoutMs));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    @Override
    public String currentUrl() {
        return page.url();
    }

    @Override
    public byte[] screenshot() {
        return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
    }

    @Override
    public String engineName() {
        return "playwright/" + browserName;
    }

    @Override
    public void close() {
        context.close();
        browser.close();
        playwright.close();
    }
}
