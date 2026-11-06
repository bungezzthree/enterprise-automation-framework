package com.enterprise.qa.core.mobile;

import java.util.Locale;

/**
 * Where a mobile test runs ({@code mobile.mode}).
 *
 * <ul>
 *   <li>{@link #EMULATOR}: an Android Virtual Device with a visible window (started if it is not running yet).</li>
 *   <li>{@link #HEADLESS}: the same AVD without a window, for CI servers and background runs.</li>
 *   <li>{@link #DEVICE}: a physical phone or tablet connected by USB or Wi-Fi (never started or stopped by the framework).</li>
 *   <li>{@link #REMOTE}: a device farm or remote Appium server; no local device handling at all.</li>
 * </ul>
 */
public enum MobileMode {
    EMULATOR, HEADLESS, DEVICE, REMOTE;

    public boolean usesEmulator() {
        return this == EMULATOR || this == HEADLESS;
    }

    public static MobileMode parse(String value) {
        if (value == null || value.isBlank()) {
            return EMULATOR;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown mobile.mode '" + value + "' (expected emulator, headless, device or remote)");
        }
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
