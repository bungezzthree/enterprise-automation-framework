package com.enterprise.qa.core.mobile.android;

import com.enterprise.qa.core.mobile.MobileSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds, starts and stops Android Virtual Devices (AVDs). */
public final class EmulatorManager {

    /** An emulator the framework can talk to. {@code process} is set only when this framework started it. */
    public record Running(String serial, Process process) {
        public boolean startedByUs() {
            return process != null;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(EmulatorManager.class);
    private static final String SOFTWARE_GPU = "swiftshader_indirect";

    private final String emulatorCommand;
    private final Adb adb;

    public EmulatorManager(String emulatorCommand, Adb adb) {
        this.emulatorCommand = emulatorCommand;
        this.adb = adb;
    }

    /** Names reported by {@code emulator -list-avds}. */
    public List<String> listAvds() throws IOException, InterruptedException {
        Exec.Result r = Exec.run(List.of(emulatorCommand, "-list-avds"), Duration.ofSeconds(30));
        return parseAvdList(r.output());
    }

    static List<String> parseAvdList(String output) {
        return output.lines().map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("INFO") && !l.startsWith("WARNING") && !l.startsWith("ERROR") && !l.contains(" "))
                .toList();
    }

    /** Arguments for starting an AVD. Headless means no window, no audio and no boot animation. */
    public static List<String> buildArgs(String avd, MobileSettings s) {
        return buildArgs(avd, s, s.emulatorGpu());
    }

    static List<String> buildArgs(String avd, MobileSettings s, String gpu) {
        List<String> args = new ArrayList<>(List.of("-avd", avd));
        if (s.headless()) {
            args.addAll(List.of("-no-window", "-no-audio", "-no-boot-anim"));
        }
        if (gpu != null && !gpu.isBlank()) {
            args.addAll(List.of("-gpu", gpu));
        }
        if (s.emulatorPort() != null) {
            args.addAll(List.of("-port", String.valueOf(s.emulatorPort())));
        }
        if (!s.emulatorSaveSnapshot()) {
            args.add("-no-snapshot-save");
        }
        if (s.emulatorWipe()) {
            args.add("-wipe-data");
        }
        args.addAll(s.emulatorArgs());
        return args;
    }

    /** The online emulator that is already running the given AVD, if any. */
    public Optional<String> findRunning(String avd) throws IOException, InterruptedException {
        for (Adb.Device d : adb.devices()) {
            if (d.isEmulator() && d.isOnline() && adb.avdName(d.serial()).map(avd::equalsIgnoreCase).orElse(false)) {
                return Optional.of(d.serial());
            }
        }
        return Optional.empty();
    }

    /**
     * Starts the AVD and waits until Android has booted. The emulator log goes to {@code logFile}. If the emulator
     * crashes while initialising the GPU (a common driver problem), it is started once more with software rendering.
     */
    public Running start(String avd, MobileSettings s, Path logFile) throws IOException, InterruptedException {
        try {
            return startWith(avd, s, s.emulatorGpu(), logFile);
        } catch (IllegalStateException e) {
            if (isGpuFailure(logFile) && !SOFTWARE_GPU.equals(s.emulatorGpu())) {
                LOG.warn("The emulator crashed while starting GPU emulation (-gpu {}); trying again with software rendering ({}). "
                        + "Set android.emulator.gpu={} to skip the first attempt.", s.emulatorGpu(), SOFTWARE_GPU, SOFTWARE_GPU);
                return startWith(avd, s, SOFTWARE_GPU, logFile);
            }
            throw e;
        }
    }

    static boolean isGpuFailure(Path logFile) {
        try {
            return Files.readString(logFile, java.nio.charset.StandardCharsets.UTF_8).contains("GPU emulation failed");
        } catch (IOException e) {
            return false;
        }
    }

    private Running startWith(String avd, MobileSettings s, String gpu, Path logFile) throws IOException, InterruptedException {
        Set<String> before = new HashSet<>();
        for (Adb.Device d : adb.devices()) {
            before.add(d.serial());
        }
        List<String> command = new ArrayList<>(List.of(emulatorCommand));
        command.addAll(buildArgs(avd, s, gpu));
        Files.createDirectories(logFile.toAbsolutePath().getParent());
        LOG.info("Starting emulator '{}' ({}): {}", avd, s.headless() ? "headless" : "with window", String.join(" ", command));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile())).start();
        try {
            String serial = waitForSerial(process, before, avd, s, logFile);
            if (s.emulatorSettleSec() > 0) {
                // Right after boot Android is still busy (package optimisation, launcher start-up); tapping now invites ANR dialogs.
                LOG.info("Emulator '{}' booted as {}; letting it settle for {} s", avd, serial, s.emulatorSettleSec());
                Thread.sleep(Duration.ofSeconds(s.emulatorSettleSec()).toMillis());
            }
            LOG.info("Emulator '{}' is up as {}", avd, serial);
            return new Running(serial, process);
        } catch (IOException | InterruptedException | RuntimeException e) {
            Exec.killTree(process);
            throw e;
        }
    }

    private String waitForSerial(Process process, Set<String> before, String avd, MobileSettings s, Path logFile)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(s.emulatorBootTimeoutSec()).toNanos();
        String serial = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("The emulator for AVD '" + avd + "' exited with code " + process.exitValue()
                        + " right after starting. Last log lines:\n" + tail(logFile, 15));
            }
            if (isGpuFailure(logFile)) {
                // The emulator can print this and then hang instead of exiting; do not wait out the whole boot timeout.
                throw new IllegalStateException("The emulator for AVD '" + avd + "' reported 'GPU emulation failed to initialize'.");
            }
            if (serial == null) {
                serial = adb.devices().stream().filter(d -> d.isEmulator() && !before.contains(d.serial())).map(Adb.Device::serial).findFirst().orElse(null);
            } else if (adb.isBooted(serial)) {
                return serial;
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Emulator '" + avd + "' did not finish booting within " + s.emulatorBootTimeoutSec()
                + " s. If a window never showed Android, the host GPU driver may not support the emulator: use mobile.mode=headless"
                + " (-Pheadless) or update the graphics driver; on a slow machine raise android.emulator.boot.timeout.s. Log: " + logFile.toAbsolutePath());
    }

    /** Stops an emulator this framework started: politely through adb first, then by killing the process tree. */
    public void stop(Running running) {
        try {
            adb.killEmulator(running.serial());
            if (running.process() != null && running.process().waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                return;
            }
        } catch (IOException e) {
            LOG.debug("adb emu kill failed: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (running.process() != null) {
            Exec.killTree(running.process());
        }
    }

    private static String tail(Path file, int lines) {
        try {
            List<String> all = Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
            return String.join(File.separator.equals("\\") ? "\r\n" : "\n", all.subList(Math.max(0, all.size() - lines), all.size()));
        } catch (IOException e) {
            return "(log not readable)";
        }
    }
}
