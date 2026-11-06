package com.enterprise.qa.core.mobile.android;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidSdkTest {

    private static Path folder(Path base, String... parts) throws IOException {
        Path p = base;
        for (String part : parts) {
            p = p.resolve(part);
        }
        return Files.createDirectories(p);
    }

    @Test
    void theConfiguredPathWinsOverEnvironmentVariables(@TempDir Path tmp) throws IOException {
        Path configured = folder(tmp, "configured");
        Path fromEnv = folder(tmp, "env");
        AndroidSdk sdk = AndroidSdk.locate(configured.toString(), Map.of("ANDROID_HOME", fromEnv.toString()), "Linux", tmp).orElseThrow();
        assertThat(sdk.root()).isEqualTo(configured);
        assertThat(sdk.source()).isEqualTo("android.sdk.root setting");
    }

    @Test
    void androidHomeIsUsedBeforeAndroidSdkRoot(@TempDir Path tmp) throws IOException {
        Path home = folder(tmp, "a");
        Path root = folder(tmp, "b");
        AndroidSdk sdk = AndroidSdk.locate(null, Map.of("ANDROID_HOME", home.toString(), "ANDROID_SDK_ROOT", root.toString()), "Linux", tmp).orElseThrow();
        assertThat(sdk.root()).isEqualTo(home);
        assertThat(AndroidSdk.locate(null, Map.of("ANDROID_SDK_ROOT", root.toString()), "Linux", tmp).orElseThrow().source()).isEqualTo("ANDROID_SDK_ROOT");
    }

    @Test
    void aVariablePointingToAMissingFolderIsSkipped(@TempDir Path tmp) throws IOException {
        Path real = folder(tmp, "Android", "Sdk");
        AndroidSdk sdk = AndroidSdk.locate(null, Map.of("ANDROID_HOME", tmp.resolve("gone").toString()), "Linux", tmp).orElseThrow();
        assertThat(sdk.root()).isEqualTo(real);
    }

    @Test
    void windowsDefaultIsUnderLocalAppData(@TempDir Path tmp) throws IOException {
        Path sdkDir = folder(tmp, "Local", "Android", "Sdk");
        AndroidSdk sdk = AndroidSdk.locate(null, Map.of("LOCALAPPDATA", tmp.resolve("Local").toString()), "Windows 11", tmp).orElseThrow();
        assertThat(sdk.root()).isEqualTo(sdkDir);
    }

    @Test
    void macOsAndLinuxDefaultsDiffer(@TempDir Path tmp) throws IOException {
        Path mac = folder(tmp, "Library", "Android", "sdk");
        assertThat(AndroidSdk.locate(null, Map.of(), "Mac OS X", tmp).orElseThrow().root()).isEqualTo(mac);
        Path linuxHome = Files.createDirectories(tmp.resolve("linux"));
        Path linux = folder(linuxHome, "Android", "Sdk");
        assertThat(AndroidSdk.locate(null, Map.of(), "Linux", linuxHome).orElseThrow().root()).isEqualTo(linux);
    }

    @Test
    void nothingFoundGivesAnEmptyResult(@TempDir Path tmp) {
        assertThat(AndroidSdk.locate(null, Map.of(), "Linux", tmp)).isEmpty();
    }

    @Test
    void toolPathsGetAnExeSuffixOnWindowsOnly(@TempDir Path tmp) throws IOException {
        Path root = folder(tmp, "sdk");
        Files.createFile(folder(root, "platform-tools").resolve("adb.exe"));
        Files.createFile(folder(root, "emulator").resolve("emulator.exe"));
        AndroidSdk windows = AndroidSdk.locate(root.toString(), Map.of(), "Windows 10", tmp).orElseThrow();
        assertThat(windows.adb()).endsWith("adb.exe");
        assertThat(windows.hasEmulator()).isTrue();
        AndroidSdk linux = AndroidSdk.locate(root.toString(), Map.of(), "Linux", tmp).orElseThrow();
        assertThat(linux.adb()).as("no adb binary without the .exe suffix, so PATH is used").isEqualTo("adb");
        assertThat(linux.hasEmulator()).isFalse();
    }

    @Test
    void appiumGetsTheSdkLocationWhenTheUserSetNoVariable(@TempDir Path tmp) throws IOException {
        Path root = folder(tmp, "sdk");
        AndroidSdk sdk = AndroidSdk.locate(root.toString(), Map.of(), "Linux", tmp).orElseThrow();
        assertThat(sdk.environmentFor(Map.of())).containsEntry("ANDROID_HOME", root.toString()).containsEntry("ANDROID_SDK_ROOT", root.toString());
        assertThat(sdk.environmentFor(Map.of("ANDROID_HOME", "/already/set"))).doesNotContainKey("ANDROID_HOME").containsKey("ANDROID_SDK_ROOT");
        assertThat(sdk.environmentFor(Map.of("ANDROID_HOME", "/a", "ANDROID_SDK_ROOT", "/b"))).isEmpty();
    }

    @Test
    void detectsWindows() {
        assertThat(AndroidSdk.isWindows("Windows 11")).isTrue();
        assertThat(AndroidSdk.isWindows("Linux")).isFalse();
        assertThat(AndroidSdk.isWindows("Mac OS X")).isFalse();
    }
}
