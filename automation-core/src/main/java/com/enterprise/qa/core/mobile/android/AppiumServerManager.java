package com.enterprise.qa.core.mobile.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Makes sure an Appium server answers at the configured URL, starting a local one when allowed. */
public final class AppiumServerManager {

    private static final Logger LOG = LoggerFactory.getLogger(AppiumServerManager.class);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0");

    private final String appiumCommand;
    private final boolean windows;

    public AppiumServerManager(String appiumCommand, boolean windows) {
        this.appiumCommand = appiumCommand;
        this.windows = windows;
    }

    /** {@code GET <url>/status} answers 200. */
    public static boolean isUp(String url, Duration timeout) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpResponse<Void> r = client.send(HttpRequest.newBuilder(URI.create(stripSlash(url) + "/status")).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return r.statusCode() == 200;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean isLocal(String url) {
        String host = URI.create(url).getHost();
        return host != null && LOCAL_HOSTS.contains(host);
    }

    /** Command line that starts a local Appium server. */
    public List<String> command(String url, Path logFile) {
        URI uri = URI.create(url);
        List<String> args = new ArrayList<>(List.of(appiumCommand, "--address", uri.getHost() == null ? "127.0.0.1" : uri.getHost(),
                "--port", String.valueOf(uri.getPort() > 0 ? uri.getPort() : 4723), "--log", logFile.toAbsolutePath().toString(), "--log-no-colors"));
        return Exec.viaShell(windows, args);
    }

    /** Installed Appium version, or empty when the CLI is missing. */
    public java.util.Optional<String> version() {
        try {
            Exec.Result r = Exec.run(Exec.viaShell(windows, List.of(appiumCommand, "--version")), Duration.ofSeconds(60));
            return r.ok() ? r.output().lines().map(String::trim).filter(l -> l.matches("\\d+\\.\\d+\\.\\d+.*")).findFirst() : java.util.Optional.empty();
        } catch (IOException e) {
            return java.util.Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return java.util.Optional.empty();
        }
    }

    /** Output of {@code appium driver list --installed --json}, or empty when it cannot be read. */
    public java.util.Optional<String> installedDriversJson() {
        try {
            Exec.Result r = Exec.run(Exec.viaShell(windows, List.of(appiumCommand, "driver", "list", "--installed", "--json")), Duration.ofSeconds(90));
            return r.ok() ? java.util.Optional.of(r.output()) : java.util.Optional.empty();
        } catch (IOException e) {
            return java.util.Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return java.util.Optional.empty();
        }
    }

    /**
     * Returns a started process when this call had to launch Appium, or empty when a server was already answering.
     *
     * @throws IllegalStateException with a how-to-fix message when no server is reachable and none can be started
     */
    public java.util.Optional<Process> ensure(String url, boolean autoStart, Duration startTimeout, Path logFile, java.util.Map<String, String> environment)
            throws IOException, InterruptedException {
        if (isUp(url, Duration.ofSeconds(3))) {
            LOG.info("Using the Appium server already running at {}", url);
            return java.util.Optional.empty();
        }
        if (!autoStart || !isLocal(url)) {
            throw new IllegalStateException("No Appium server answers at " + url + (isLocal(url)
                    ? ". Start one ('appium') or set appium.server.mode=auto to let the framework start it."
                    : ". It is a remote address, so the framework does not start it; check the URL and your network."));
        }
        Files.createDirectories(logFile.toAbsolutePath().getParent());
        List<String> command = command(url, logFile);
        LOG.info("Starting Appium: {}", String.join(" ", command));
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.environment().putAll(environment);
            process = builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start Appium ('" + appiumCommand + "' was not found). Install it with: npm install -g appium"
                    + " and: appium driver install uiautomator2 (Node.js is required), or start your own server and set appium.server.mode=external.", e);
        }
        long deadline = System.nanoTime() + startTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isUp(url, Duration.ofSeconds(2))) {
                LOG.info("Appium is up at {} (log: {})", url, logFile.toAbsolutePath());
                return java.util.Optional.of(process);
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(1000);
        }
        Exec.killTree(process);
        throw new IllegalStateException("Appium did not start within " + startTimeout.toSeconds() + " s. See its log: " + logFile.toAbsolutePath());
    }

    public static void stop(Process process) {
        if (process != null) {
            Exec.killTree(process);
        }
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
