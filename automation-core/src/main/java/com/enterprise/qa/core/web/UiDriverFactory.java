package com.enterprise.qa.core.web;

import com.enterprise.qa.core.config.Config;

/**
 * Creates a {@link UiDriver} from configuration:
 * {@code web.engine} (playwright | selenium), {@code browser} (chromium | chrome | firefox | webkit | edge),
 * {@code headless}, {@code web.base.url}, {@code web.timeout.ms}, {@code web.navigation.timeout.ms}, {@code selenium.grid.url}.
 */
public final class UiDriverFactory {

    private UiDriverFactory() {
    }

    public static UiDriver create() {
        String engine = Config.get("web.engine", "playwright").toLowerCase();
        String browser = Config.get("browser", "chromium");
        boolean headless = Config.getBool("headless", true);
        String baseUrl = Config.get("web.base.url");
        int timeoutMs = Config.getInt("web.timeout.ms", 10_000);
        int navigationTimeoutMs = Config.getInt("web.navigation.timeout.ms", 30_000);
        return switch (engine) {
            case "selenium" -> new SeleniumUiDriver("chromium".equalsIgnoreCase(browser) ? "chrome" : browser,
                    headless, baseUrl, timeoutMs, navigationTimeoutMs, Config.get("selenium.grid.url"));
            case "playwright" -> new PlaywrightUiDriver(browser, headless, baseUrl, timeoutMs, navigationTimeoutMs);
            default -> throw new IllegalArgumentException("Unknown web.engine '" + engine + "' (playwright|selenium)");
        };
    }
}
