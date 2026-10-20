package com.enterprise.qa.core.web;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Selenium 4 implementation. Driver binaries are resolved by Selenium Manager; set {@code selenium.grid.url}
 * to run on a Selenium Grid instead of a local browser.
 */
public class SeleniumUiDriver implements UiDriver {

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String baseUrl;
    private final String browserName;

    public SeleniumUiDriver(String browserName, boolean headless, String baseUrl, int timeoutMs,
                            int navigationTimeoutMs, String gridUrl) {
        this.browserName = browserName;
        this.baseUrl = baseUrl;
        this.driver = create(browserName.toLowerCase(), headless, gridUrl);
        this.driver.manage().window().setSize(new Dimension(1366, 768));
        this.driver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(navigationTimeoutMs));
        this.wait = new WebDriverWait(driver, Duration.ofMillis(timeoutMs));
    }

    private static WebDriver create(String browser, boolean headless, String gridUrl) {
        var options = switch (browser) {
            case "firefox" -> {
                FirefoxOptions o = new FirefoxOptions();
                if (headless) {
                    o.addArguments("-headless");
                }
                yield o;
            }
            case "edge", "msedge" -> {
                EdgeOptions o = new EdgeOptions();
                if (headless) {
                    o.addArguments("--headless=new");
                }
                yield o;
            }
            default -> {
                ChromeOptions o = new ChromeOptions();
                if (headless) {
                    o.addArguments("--headless=new");
                }
                // Stop Chrome's password-leak popup from covering the page after a demo login.
                o.setExperimentalOption("prefs", Map.of(
                        "credentials_enable_service", false,
                        "profile.password_manager_enabled", false,
                        "profile.password_manager_leak_detection", false));
                o.addArguments("--disable-features=PasswordLeakDetection");
                yield o;
            }
        };
        // Return at DOMContentLoaded, like the Playwright driver; element waits cover the rest.
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        if (gridUrl != null) {
            try {
                return new RemoteWebDriver(URI.create(gridUrl).toURL(), options);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Invalid selenium.grid.url " + gridUrl, e);
            }
        }
        if (options instanceof FirefoxOptions f) {
            return new FirefoxDriver(f);
        }
        if (options instanceof EdgeOptions e) {
            return new EdgeDriver(e);
        }
        return new ChromeDriver((ChromeOptions) options);
    }

    @Override
    public void open(String pathOrUrl) {
        driver.get(WebUrls.resolve(baseUrl, pathOrUrl));
    }

    @Override
    public void click(String css) {
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(css))).click();
    }

    @Override
    public void type(String css, String text) {
        WebElement el = visible(css);
        el.clear();
        el.sendKeys(text);
    }

    @Override
    public void selectByValue(String css, String value) {
        new Select(visible(css)).selectByValue(value);
    }

    @Override
    public String text(String css) {
        return visible(css).getText().trim();
    }

    @Override
    public List<String> texts(String css) {
        visible(css);
        return driver.findElements(By.cssSelector(css)).stream().map(e -> e.getText().trim()).toList();
    }

    @Override
    public int count(String css) {
        return driver.findElements(By.cssSelector(css)).size();
    }

    @Override
    public boolean isVisible(String css) {
        try {
            visible(css);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    @Override
    public boolean waitUntilHidden(String css) {
        try {
            return wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(css)));
        } catch (TimeoutException e) {
            return false;
        }
    }

    @Override
    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    @Override
    public byte[] screenshot() {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    @Override
    public String engineName() {
        return "selenium/" + browserName;
    }

    @Override
    public void close() {
        driver.quit();
    }

    private WebElement visible(String css) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(css)));
    }
}
