package com.enterprise.qa.core.mobile.android;

import com.enterprise.qa.core.mobile.MobileMode;
import com.enterprise.qa.core.mobile.MobileSettings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checks the machine against what the configured {@code mobile.mode} needs and says what to fix.
 * Run it from the IDE (right-click {@code main}) or with Maven, see docs/android-setup.md. Exit code 1 when a check fails.
 */
public final class AndroidDoctor {

    public enum Status { OK, WARN, FAIL, INFO }

    public record Check(String name, Status status, String detail) {
    }

    private AndroidDoctor() {
    }

    public static void main(String[] args) {
        MobileSettings settings = MobileSettings.fromConfig();
        List<Check> checks = run(settings);
        System.out.println();
        System.out.println("Android environment check  (mobile.mode=" + settings.mode().label() + ")");
        System.out.println("-".repeat(78));
        for (Check c : checks) {
            System.out.printf("%-5s %-16s %s%n", "[" + c.status() + "]", c.name(), c.detail());
        }
        System.out.println("-".repeat(78));
        boolean failed = checks.stream().anyMatch(c -> c.status() == Status.FAIL);
        System.out.println(failed ? "Fix the [FAIL] items above, then run again." : "Ready to run.");
        System.exit(failed ? 1 : 0);
    }

    public static List<Check> run(MobileSettings s) {
        List<Check> out = new ArrayList<>();
        boolean windows = AndroidSdk.isWindows(System.getProperty("os.name"));
        out.add(new Check("Operating system", Status.INFO, System.getProperty("os.name") + " " + System.getProperty("os.version")));
        out.add(java());
        out.add(new Check("Mode", Status.INFO, s.mode().label() + (s.mode().usesEmulator() ? ", AVD '" + s.avd() + "'" : "")
                + (s.udid() != null ? ", device " + s.udid() : "")));
        out.add(app(s));
        out.addAll(appium(s, windows));
        if (s.mode() == MobileMode.REMOTE) {
            return out;
        }
        Optional<AndroidSdk> sdk = AndroidSdk.locate(s.sdkRoot());
        if (sdk.isEmpty()) {
            out.add(new Check("Android SDK", Status.FAIL, "not found. Install Android Studio and set ANDROID_HOME (or android.sdk.root)."));
            return out;
        }
        out.add(new Check("Android SDK", Status.OK, sdk.get().root() + "  (" + sdk.get().source() + ")"));
        if (!sdk.get().environmentFor(System.getenv()).isEmpty()) {
            out.add(new Check("ANDROID_HOME", Status.WARN, "not set. The framework passes the SDK location to an Appium it starts itself. "
                    + "If you start Appium yourself, set ANDROID_HOME=" + sdk.get().root() + " first."));
        }
        Adb adb = new Adb(sdk.get().adb());
        Optional<String> adbVersion = adb.version();
        if (adbVersion.isEmpty()) {
            out.add(new Check("adb", Status.FAIL, "cannot run '" + sdk.get().adb() + "'. Install the SDK 'platform-tools' package."));
            return out;
        }
        out.add(new Check("adb", Status.OK, adbVersion.get()));
        try {
            List<Adb.Device> devices = adb.devices();
            out.add(new Check("Connected", devices.isEmpty() ? Status.INFO : Status.OK, devices.isEmpty() ? "no devices or emulators"
                    : devices.stream().map(d -> d.serial() + " (" + d.state() + (d.model().isEmpty() ? "" : ", " + d.model()) + ")").toList().toString()));
            if (s.mode() == MobileMode.DEVICE) {
                out.add(deviceCheck(devices, s.udid()));
            }
            if (s.mode().usesEmulator()) {
                out.addAll(emulator(s, sdk.get(), adb));
            }
        } catch (IOException e) {
            out.add(new Check("adb devices", Status.FAIL, e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out;
    }

    private static Check java() {
        String home = System.getenv("JAVA_HOME");
        return new Check("Java", Status.OK, "Java " + System.getProperty("java.version") + (home == null ? "  (JAVA_HOME is not set: needed only for ./mvnw in a terminal)" : "  (JAVA_HOME=" + home + ")"));
    }

    private static Check app(MobileSettings s) {
        if (s.app() == null && s.appPackage() == null) {
            return new Check("App under test", Status.FAIL, "set mobile.app (path or URL of the .apk) or mobile.app.package for an installed app.");
        }
        if (s.app() == null) {
            return new Check("App under test", Status.INFO, "installed app " + s.appPackage());
        }
        if (s.app().startsWith("http://") || s.app().startsWith("https://")) {
            return new Check("App under test", Status.INFO, "downloaded by Appium from " + s.app());
        }
        return Files.isRegularFile(Path.of(s.app()))
                ? new Check("App under test", Status.OK, s.app())
                : new Check("App under test", Status.FAIL, "file not found: " + s.app());
    }

    private static List<Check> appium(MobileSettings s, boolean windows) {
        List<Check> out = new ArrayList<>();
        AppiumServerManager manager = new AppiumServerManager(s.appiumCommand(), windows);
        boolean up = AppiumServerManager.isUp(s.appiumUrl(), Duration.ofSeconds(3));
        Optional<String> version = manager.version();
        if (version.isEmpty()) {
            out.add(new Check("Appium CLI", up ? Status.WARN : (s.appiumAutoStart() ? Status.FAIL : Status.INFO),
                    "'" + s.appiumCommand() + "' not found. Install: npm install -g appium  then  appium driver install uiautomator2"));
        } else {
            out.add(new Check("Appium CLI", Status.OK, "appium " + version.get()));
            out.add(driverCheck(manager));
        }
        out.add(new Check("Appium server", up ? Status.OK : (s.appiumAutoStart() && AppiumServerManager.isLocal(s.appiumUrl()) ? Status.INFO : Status.WARN),
                s.appiumUrl() + (up ? " is running" : s.appiumAutoStart() ? " is not running; the framework will start it" : " is not running")));
        return out;
    }

    private static Check driverCheck(AppiumServerManager manager) {
        Optional<String> json = manager.installedDriversJson();
        if (json.isEmpty()) {
            return new Check("UiAutomator2", Status.WARN, "could not list the installed Appium drivers");
        }
        String text = json.get();
        int start = text.indexOf('{');
        try {
            JsonObject drivers = new Gson().fromJson(start >= 0 ? text.substring(start) : text, JsonObject.class);
            if (drivers != null && drivers.has("uiautomator2")) {
                JsonObject d = drivers.getAsJsonObject("uiautomator2");
                return new Check("UiAutomator2", Status.OK, "driver " + (d.has("version") ? d.get("version").getAsString() : "installed"));
            }
        } catch (JsonParseException e) {
            if (text.contains("uiautomator2")) {
                return new Check("UiAutomator2", Status.OK, "driver installed");
            }
        }
        return new Check("UiAutomator2", Status.FAIL, "driver missing. Run: appium driver install uiautomator2");
    }

    private static Check deviceCheck(List<Adb.Device> devices, String udid) {
        try {
            Adb.Device d = AndroidRuntime.selectDevice(devices, udid);
            return new Check("Device", Status.OK, d.serial() + (d.model().isEmpty() ? "" : " (" + d.model() + ")") + " will be used");
        } catch (IllegalStateException e) {
            return new Check("Device", Status.FAIL, e.getMessage());
        }
    }

    private static List<Check> emulator(MobileSettings s, AndroidSdk sdk, Adb adb) throws IOException, InterruptedException {
        List<Check> out = new ArrayList<>();
        if (!sdk.hasEmulator()) {
            out.add(new Check("Emulator", Status.FAIL, "not installed. Android Studio > SDK Manager > SDK Tools > Android Emulator."));
            return out;
        }
        out.add(new Check("Emulator", Status.OK, sdk.emulator()));
        Exec.Result accel = Exec.run(List.of(sdk.emulator(), "-accel-check"), Duration.ofSeconds(30));
        out.add(accelerationCheck(accel.output(), AndroidSdk.isWindows(System.getProperty("os.name"))));
        EmulatorManager manager = new EmulatorManager(sdk.emulator(), adb);
        List<String> avds = manager.listAvds();
        if (s.avd() == null || s.avd().isBlank()) {
            out.add(new Check("AVD", Status.FAIL, "android.avd is not set. Available: " + (avds.isEmpty() ? "none" : avds)));
        } else if (avds.contains(s.avd())) {
            out.add(new Check("AVD", Status.OK, "'" + s.avd() + "' exists" + (manager.findRunning(s.avd()).map(x -> " and is running as " + x).orElse(", not running yet (it will be started)"))));
        } else {
            out.add(new Check("AVD", Status.FAIL, "'" + s.avd() + "' does not exist. Available: " + (avds.isEmpty() ? "none" : avds)
                    + ". Create it in Android Studio > Device Manager."));
        }
        return out;
    }

    /** Reads {@code emulator -accel-check}. Without hardware acceleration an emulator is unusably slow. */
    static Check accelerationCheck(String output, boolean windows) {
        String lower = output.toLowerCase();
        if (lower.contains("is installed and usable") || lower.contains("kvm is installed") || lower.contains("hvf is")) {
            return new Check("Acceleration", Status.OK, output.lines().map(String::trim).filter(l -> l.toLowerCase().contains("usable") || l.toLowerCase().contains("installed")).findFirst().orElse("hardware acceleration available"));
        }
        return new Check("Acceleration", Status.WARN, "not available, the emulator will be very slow. "
                + (windows ? "Turn on 'Windows Hypervisor Platform' (Windows Features) and virtualization in the BIOS."
                : "Linux: install KVM and add your user to the 'kvm' group. macOS: Apple's Hypervisor.framework is used automatically."));
    }

    /** For tests and diagnostics: the extra capabilities that will be sent. */
    static Map<String, String> extras(MobileSettings s) {
        return s.extraCapabilities();
    }
}
