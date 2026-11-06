package com.enterprise.qa.core.mobile.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the Android SDK and its command line tools on Windows, macOS and Linux.
 * Lookup order: {@code android.sdk.root} setting, {@code ANDROID_HOME}, {@code ANDROID_SDK_ROOT}, then the folder the
 * Android Studio installer uses on that operating system.
 */
public final class AndroidSdk {

    private final Path root;
    private final String source;
    private final boolean windows;

    private AndroidSdk(Path root, String source, boolean windows) {
        this.root = root;
        this.source = source;
        this.windows = windows;
    }

    public static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }

    public static Optional<AndroidSdk> locate(String configured, Map<String, String> env, String osName, Path home) {
        boolean windows = isWindows(osName);
        Map<String, String> candidates = new LinkedHashMap<>();
        put(candidates, "android.sdk.root setting", configured);
        put(candidates, "ANDROID_HOME", env.get("ANDROID_HOME"));
        put(candidates, "ANDROID_SDK_ROOT", env.get("ANDROID_SDK_ROOT"));
        for (Map.Entry<String, String> e : defaultLocations(windows, osName, env, home).entrySet()) {
            put(candidates, e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : candidates.entrySet()) {
            Path path = Path.of(e.getValue());
            if (Files.isDirectory(path)) {
                return Optional.of(new AndroidSdk(path, e.getKey(), windows));
            }
        }
        return Optional.empty();
    }

    public static Optional<AndroidSdk> locate(String configured) {
        return locate(configured, System.getenv(), System.getProperty("os.name"), Path.of(System.getProperty("user.home")));
    }

    /** Where Android Studio puts the SDK by default, per operating system. */
    static Map<String, String> defaultLocations(boolean windows, String osName, Map<String, String> env, Path home) {
        Map<String, String> out = new LinkedHashMap<>();
        if (windows) {
            String local = env.get("LOCALAPPDATA");
            out.put("default location", (local != null && !local.isBlank() ? Path.of(local) : home.resolve("AppData").resolve("Local"))
                    .resolve("Android").resolve("Sdk").toString());
        } else if (osName != null && osName.toLowerCase(Locale.ROOT).contains("mac")) {
            out.put("default location", home.resolve("Library").resolve("Android").resolve("sdk").toString());
        } else {
            out.put("default location", home.resolve("Android").resolve("Sdk").toString());
            out.put("default location (snap/distro)", home.resolve("Android").resolve("sdk").toString());
        }
        return out;
    }

    private static void put(Map<String, String> map, String name, String value) {
        if (value != null && !value.isBlank()) {
            map.put(name, value.trim());
        }
    }

    public Path root() {
        return root;
    }

    public String source() {
        return source;
    }

    /** Path of {@code platform-tools/adb}, or just {@code adb} (looked up on PATH) when the SDK does not contain it. */
    public String adb() {
        return tool(root.resolve("platform-tools"), "adb", "adb");
    }

    /** Path of {@code emulator/emulator}, or just {@code emulator} when the SDK does not contain it. */
    public String emulator() {
        return tool(root.resolve("emulator"), "emulator", "emulator");
    }

    /**
     * Environment variables Appium needs to find the SDK. Appium refuses to create a session without ANDROID_HOME or
     * ANDROID_SDK_ROOT, even when the SDK is in the default folder, so the framework passes them to a server it starts.
     * Variables the user already set are left alone.
     */
    public Map<String, String> environmentFor(Map<String, String> current) {
        Map<String, String> add = new LinkedHashMap<>();
        for (String name : new String[] {"ANDROID_HOME", "ANDROID_SDK_ROOT"}) {
            String existing = current.get(name);
            if (existing == null || existing.isBlank()) {
                add.put(name, root.toString());
            }
        }
        return add;
    }

    public boolean hasEmulator() {
        return Files.isRegularFile(root.resolve("emulator").resolve(exe("emulator")));
    }

    private String tool(Path dir, String name, String fallback) {
        Path candidate = dir.resolve(exe(name));
        return Files.isRegularFile(candidate) ? candidate.toString() : fallback;
    }

    private String exe(String name) {
        return windows ? name + ".exe" : name;
    }
}
