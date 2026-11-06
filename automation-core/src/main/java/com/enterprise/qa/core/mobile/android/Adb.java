package com.enterprise.qa.core.mobile.android;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Thin wrapper around the {@code adb} command line tool. */
public final class Adb {

    /** One line of {@code adb devices -l}. {@code state} is device, offline, unauthorized, ... */
    public record Device(String serial, String state, String model) {
        public boolean isEmulator() {
            return serial.startsWith("emulator-");
        }

        public boolean isOnline() {
            return state.equals("device");
        }
    }

    private static final Pattern DEVICE_LINE = Pattern.compile(
            "^(\\S+)\\s+(device|offline|unauthorized|authorizing|recovery|no permissions|bootloader|sideload|connecting|host)(?:\\s+(.*))?$");
    private static final Pattern MODEL = Pattern.compile("model:(\\S+)");
    private static final Duration SHORT = Duration.ofSeconds(20);

    private final String command;

    public Adb(String command) {
        this.command = command;
    }

    /** Parses the output of {@code adb devices -l}; daemon start-up chatter and the header line are ignored. */
    public static List<Device> parseDevices(String output) {
        List<Device> devices = new ArrayList<>();
        for (String raw : output.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("List of devices")) {
                continue;
            }
            Matcher m = DEVICE_LINE.matcher(line);
            if (m.matches()) {
                Matcher model = MODEL.matcher(m.group(3) == null ? "" : m.group(3));
                devices.add(new Device(m.group(1), m.group(2), model.find() ? model.group(1) : ""));
            }
        }
        return devices;
    }

    public List<Device> devices() throws IOException, InterruptedException {
        return parseDevices(run(SHORT, "devices", "-l").output());
    }

    /** Version line such as {@code Android Debug Bridge version 1.0.41}, or empty when adb cannot be run. */
    public Optional<String> version() {
        try {
            Exec.Result r = run(SHORT, "version");
            return r.ok() ? r.output().lines().findFirst().map(String::trim) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    public String shell(String serial, Duration timeout, String... args) throws IOException, InterruptedException {
        List<String> all = new ArrayList<>(List.of("-s", serial, "shell"));
        all.addAll(List.of(args));
        return run(timeout, all.toArray(String[]::new)).output().trim();
    }

    /** True once Android has finished booting (sys.boot_completed) and the package manager answers. */
    public boolean isBooted(String serial) {
        try {
            return shell(serial, SHORT, "getprop", "sys.boot_completed").equals("1")
                    && shell(serial, SHORT, "pm", "path", "android").startsWith("package:");
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Name of the AVD behind an emulator serial, or empty (physical devices have none). */
    public Optional<String> avdName(String serial) {
        try {
            Exec.Result r = run(SHORT, "-s", serial, "emu", "avd", "name");
            return r.ok() ? r.output().lines().map(String::trim).filter(l -> !l.isEmpty() && !l.equals("OK")).findFirst() : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    public void killEmulator(String serial) throws IOException, InterruptedException {
        run(SHORT, "-s", serial, "emu", "kill");
    }

    Exec.Result run(Duration timeout, String... args) throws IOException, InterruptedException {
        List<String> full = new ArrayList<>(List.of(command));
        full.addAll(List.of(args));
        return Exec.run(full, timeout);
    }
}
