package com.enterprise.qa.core.mobile.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a short-lived command and captures its output. Used for adb, the emulator and the Appium CLI. */
final class Exec {

    /** @param exit -1 when the command was killed because it timed out */
    record Result(int exit, String output, boolean timedOut) {
        boolean ok() {
            return exit == 0 && !timedOut;
        }
    }

    private Exec() {
    }

    static Result run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuffer output = new StringBuffer();
        // A daemon (such as the adb server) may keep the pipe open after the client exits, so the reader must not block us.
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (IOException ignored) {
                // process ended
            }
        }, "exec-reader");
        reader.setDaemon(true);
        reader.start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            killTree(process);
            reader.join(1000);
            return new Result(-1, output.toString(), true);
        }
        reader.join(2000);
        return new Result(process.exitValue(), output.toString(), false);
    }

    /** Kills a process and everything it started (the Windows {@code cmd /c} wrapper leaves its child behind otherwise). */
    static void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /** On Windows, npm-installed CLIs such as {@code appium} are {@code .cmd} scripts and need {@code cmd /c}. */
    static List<String> viaShell(boolean windows, List<String> command) {
        if (!windows) {
            return command;
        }
        List<String> wrapped = new ArrayList<>(List.of("cmd", "/c"));
        wrapped.addAll(command);
        return wrapped;
    }
}
