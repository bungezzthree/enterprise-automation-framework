package com.enterprise.qa.core.mobile;

import com.enterprise.qa.core.config.Config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Everything the mobile layer reads from configuration, parsed once. Built from any {@code key -> value} lookup so it
 * can be unit tested; {@link #fromConfig()} reads the framework's layered {@link Config}.
 */
public record MobileSettings(
        String platform, MobileMode mode,
        String sdkRoot, String avd, String udid, String deviceName, String platformVersion,
        String appiumUrl, boolean appiumAutoStart, String appiumCommand, int appiumStartTimeoutSec,
        String app, String appPackage, String appActivity, String appWaitActivity, boolean noReset,
        boolean emulatorAutoStart, boolean emulatorStopAfter, int emulatorBootTimeoutSec, int emulatorSettleSec, String emulatorGpu,
        Integer emulatorPort, List<String> emulatorArgs, boolean emulatorWipe, boolean emulatorSaveSnapshot,
        int commandTimeoutSec, int waitMs, SessionScope sessionScope, boolean recordVideo, Map<String, String> extraCapabilities) {

    /** One Appium session for the whole run (fast, the app is relaunched between scenarios) or one per scenario. */
    public enum SessionScope {
        RUN, SCENARIO;

        static SessionScope parse(String value) {
            return value != null && value.trim().equalsIgnoreCase("scenario") ? SCENARIO : RUN;
        }
    }

    public boolean isAndroid() {
        return platform.equals("android");
    }

    public boolean headless() {
        return mode == MobileMode.HEADLESS;
    }

    public static MobileSettings fromConfig() {
        return from(Config::get, Config.withPrefix("mobile.cap."));
    }

    public static MobileSettings from(Function<String, String> cfg, Map<String, String> extraCapabilities) {
        MobileMode mode = MobileMode.parse(cfg.apply("mobile.mode"));
        String gpuDefault = mode == MobileMode.HEADLESS ? "swiftshader_indirect" : "auto";
        return new MobileSettings(
                orDefault(cfg.apply("mobile.platform"), "android").toLowerCase(Locale.ROOT), mode,
                cfg.apply("android.sdk.root"), cfg.apply("android.avd"),
                first(cfg.apply("android.udid"), cfg.apply("mobile.udid")),
                orDefault(cfg.apply("mobile.device.name"), "Android Device"), cfg.apply("mobile.platform.version"),
                orDefault(cfg.apply("appium.server.url"), "http://127.0.0.1:4723"),
                !"external".equalsIgnoreCase(cfg.apply("appium.server.mode")),
                orDefault(cfg.apply("appium.command"), "appium"), intOf(cfg.apply("appium.start.timeout.s"), 150),
                cfg.apply("mobile.app"), cfg.apply("mobile.app.package"), cfg.apply("mobile.app.activity"), cfg.apply("mobile.app.wait.activity"),
                boolOf(cfg.apply("mobile.no.reset"), false),
                boolOf(cfg.apply("android.emulator.autostart"), true), boolOf(cfg.apply("android.emulator.stop"), true),
                intOf(cfg.apply("android.emulator.boot.timeout.s"), 600), intOf(cfg.apply("android.emulator.settle.s"), 10), orDefault(cfg.apply("android.emulator.gpu"), gpuDefault),
                cfg.apply("android.emulator.port") == null ? null : intOf(cfg.apply("android.emulator.port"), 5554),
                splitArgs(cfg.apply("android.emulator.args")), boolOf(cfg.apply("android.emulator.wipe"), false),
                boolOf(cfg.apply("android.emulator.snapshot.save"), false),
                intOf(cfg.apply("mobile.command.timeout.s"), 120), intOf(cfg.apply("mobile.timeout.ms"), 15_000),
                SessionScope.parse(cfg.apply("mobile.session.scope")), boolOf(cfg.apply("mobile.record.video"), false),
                extraCapabilities == null ? Map.of() : Map.copyOf(extraCapabilities));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String first(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : b != null && !b.isBlank() ? b.trim() : null;
    }

    private static int intOf(String value, int fallback) {
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    private static boolean boolOf(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static List<String> splitArgs(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.trim().split("\\s+")).toList();
    }
}
