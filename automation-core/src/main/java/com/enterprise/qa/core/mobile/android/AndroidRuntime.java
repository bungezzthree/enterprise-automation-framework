package com.enterprise.qa.core.mobile.android;

import com.enterprise.qa.core.mobile.MobileMode;
import com.enterprise.qa.core.mobile.MobileSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Prepares everything a local Android run needs, once per JVM: an Appium server and a target device chosen by
 * {@link MobileMode}. Whatever the framework started (Appium, an emulator) is stopped again when the JVM exits;
 * things that were already running, and physical devices, are never touched.
 */
public final class AndroidRuntime {

    /** The device the session will use. {@code serial} is null in REMOTE mode. */
    public record Target(String serial, String description) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(AndroidRuntime.class);
    private static final Path LOG_DIR = Path.of("target", "android");

    private static Target target;
    private static IllegalStateException failure;
    private static Process appium;
    private static EmulatorManager.Running emulator;
    private static EmulatorManager emulatorManager;
    private static boolean stopEmulatorAtExit;
    private static boolean hookRegistered;

    private AndroidRuntime() {
    }

    /**
     * Idempotent. If preparation failed once, later calls fail immediately with the same cause instead of retrying a
     * slow start for every scenario.
     */
    public static synchronized Target prepare(MobileSettings settings) {
        if (failure != null) {
            throw new IllegalStateException("The Android environment is not available: " + failure.getMessage(), failure);
        }
        if (target != null) {
            return target;
        }
        try {
            registerHook();
            stopEmulatorAtExit = settings.emulatorStopAfter();
            target = doPrepare(settings);
            return target;
        } catch (IOException | RuntimeException e) {
            failure = e instanceof IllegalStateException ise ? ise : new IllegalStateException(e.getMessage(), e);
            shutdown();
            throw failure;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while preparing the Android environment", e);
        }
    }

    private static Target doPrepare(MobileSettings s) throws IOException, InterruptedException {
        boolean windows = AndroidSdk.isWindows(System.getProperty("os.name"));
        AppiumServerManager appiumManager = new AppiumServerManager(s.appiumCommand(), windows);
        Duration appiumTimeout = Duration.ofSeconds(s.appiumStartTimeoutSec());
        if (s.mode() == MobileMode.REMOTE) {
            appium = appiumManager.ensure(s.appiumUrl(), s.appiumAutoStart(), appiumTimeout, LOG_DIR.resolve("appium.log"), java.util.Map.of()).orElse(null);
            LOG.info("Remote mode: using the Appium server at {} as it is", s.appiumUrl());
            return new Target(s.udid(), "remote Appium server " + s.appiumUrl());
        }
        AndroidSdk sdk = AndroidSdk.locate(s.sdkRoot()).orElseThrow(() -> new IllegalStateException(
                "Android SDK not found. Install Android Studio (or the command line tools) and set ANDROID_HOME, or set android.sdk.root."));
        appium = appiumManager.ensure(s.appiumUrl(), s.appiumAutoStart(), appiumTimeout, LOG_DIR.resolve("appium.log"),
                sdk.environmentFor(System.getenv())).orElse(null);
        Adb adb = new Adb(sdk.adb());
        if (adb.version().isEmpty()) {
            throw new IllegalStateException("adb could not be run (looked for '" + sdk.adb() + "'). Install the SDK 'platform-tools' package.");
        }
        LOG.info("Android SDK {} ({}), {}", sdk.root(), sdk.source(), adb.version().orElse("adb"));

        if (s.mode() == MobileMode.DEVICE) {
            Adb.Device device = selectDevice(adb.devices(), s.udid());
            return new Target(device.serial(), "device " + device.serial() + (device.model().isEmpty() ? "" : " (" + device.model() + ")"));
        }
        return prepareEmulator(s, sdk, adb);
    }

    private static Target prepareEmulator(MobileSettings s, AndroidSdk sdk, Adb adb) throws IOException, InterruptedException {
        if (s.udid() != null) {
            Adb.Device device = selectDevice(adb.devices(), s.udid());
            return new Target(device.serial(), "device " + device.serial());
        }
        if (!sdk.hasEmulator()) {
            throw new IllegalStateException("The Android emulator is not installed in " + sdk.root() + ". Install the SDK 'Android Emulator' package"
                    + " (Android Studio > SDK Manager > SDK Tools), or use mobile.mode=device.");
        }
        emulatorManager = new EmulatorManager(sdk.emulator(), adb);
        String avd = s.avd();
        List<String> avds = emulatorManager.listAvds();
        if (avd == null || avd.isBlank() || !avds.contains(avd)) {
            throw new IllegalStateException((avd == null || avd.isBlank() ? "android.avd is not set" : "AVD '" + avd + "' does not exist")
                    + ". Available AVDs: " + (avds.isEmpty() ? "none" : avds)
                    + ". Create one in Android Studio (Device Manager) or with: avdmanager create avd -n <name> -k \"system-images;android-36;google_apis;x86_64\".");
        }
        var running = emulatorManager.findRunning(avd);
        if (running.isPresent()) {
            LOG.info("Reusing the emulator that is already running for AVD '{}' ({})", avd, running.get());
            return new Target(running.get(), "emulator " + avd + " (" + running.get() + ", already running)");
        }
        if (!s.emulatorAutoStart()) {
            throw new IllegalStateException("AVD '" + avd + "' is not running and android.emulator.autostart=false.");
        }
        emulator = emulatorManager.start(avd, s, LOG_DIR.resolve("emulator-" + avd + ".log"));
        return new Target(emulator.serial(), "emulator " + avd + " (" + emulator.serial() + (s.headless() ? ", headless" : "") + ")");
    }

    /**
     * Chooses the device for {@code mobile.mode=device}: the one named by {@code android.udid}, or the only physical
     * device that is connected and authorised. Explains what to do when that is not possible.
     */
    public static Adb.Device selectDevice(List<Adb.Device> devices, String udid) {
        if (udid != null && !udid.isBlank()) {
            Adb.Device device = devices.stream().filter(d -> d.serial().equals(udid)).findFirst().orElseThrow(() -> new IllegalStateException(
                    "Device '" + udid + "' is not connected. adb sees: " + describe(devices)));
            if (!device.isOnline()) {
                throw new IllegalStateException("Device '" + udid + "' is " + device.state() + "." + hint(device.state()));
            }
            return device;
        }
        List<Adb.Device> physical = devices.stream().filter(d -> !d.isEmulator()).toList();
        List<Adb.Device> online = physical.stream().filter(Adb.Device::isOnline).toList();
        if (online.size() == 1) {
            return online.get(0);
        }
        if (online.size() > 1) {
            throw new IllegalStateException("Several devices are connected " + describe(online) + ". Choose one with android.udid=<serial>.");
        }
        if (!physical.isEmpty()) {
            Adb.Device d = physical.get(0);
            throw new IllegalStateException("Device '" + d.serial() + "' is " + d.state() + "." + hint(d.state()));
        }
        throw new IllegalStateException("No physical device is connected (adb sees: " + describe(devices) + "). Plug it in with USB debugging on,"
                + " accept the 'Allow USB debugging' prompt, and check 'adb devices'. To use an emulator instead, set mobile.mode=emulator.");
    }

    private static String hint(String state) {
        return switch (state) {
            case "unauthorized" -> " Unlock the phone and accept the 'Allow USB debugging' prompt.";
            case "offline" -> " Reconnect the cable or run 'adb kill-server' and try again.";
            default -> " Wait until it is fully connected ('adb devices' must show 'device').";
        };
    }

    private static String describe(List<Adb.Device> devices) {
        return devices.isEmpty() ? "no devices" : devices.stream().map(d -> d.serial() + " (" + d.state() + ")").toList().toString();
    }

    /** Stops what this JVM started. Safe to call more than once. */
    public static synchronized void shutdown() {
        if (emulator != null && emulator.startedByUs() && stopEmulatorAtExit && emulatorManager != null) {
            LOG.info("Stopping the emulator started for this run ({})", emulator.serial());
            emulatorManager.stop(emulator);
        }
        emulator = null;
        if (appium != null) {
            LOG.info("Stopping the Appium server started for this run");
            AppiumServerManager.stop(appium);
            appium = null;
        }
    }

    private static void registerHook() {
        if (!hookRegistered) {
            hookRegistered = true;
            Runtime.getRuntime().addShutdownHook(new Thread(AndroidRuntime::shutdown, "android-runtime-shutdown"));
        }
    }
}
