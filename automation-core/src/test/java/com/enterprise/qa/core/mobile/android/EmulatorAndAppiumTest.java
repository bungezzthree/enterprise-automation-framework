package com.enterprise.qa.core.mobile.android;

import com.enterprise.qa.core.mobile.Locators;
import com.enterprise.qa.core.mobile.MobileSettings;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmulatorAndAppiumTest {

    private static MobileSettings of(Map<String, String> cfg) {
        return MobileSettings.from(cfg::get, Map.of());
    }

    @Test
    void aWindowedEmulatorGetsNoHeadlessFlags() {
        List<String> args = EmulatorManager.buildArgs("eaf_pixel", of(Map.of("mobile.mode", "emulator")));
        assertThat(args).containsExactly("-avd", "eaf_pixel", "-gpu", "auto", "-no-snapshot-save");
    }

    @Test
    void aHeadlessEmulatorHasNoWindowAudioOrBootAnimation() {
        List<String> args = EmulatorManager.buildArgs("eaf_pixel", of(Map.of("mobile.mode", "headless")));
        assertThat(args).containsSubsequence("-avd", "eaf_pixel").contains("-no-window", "-no-audio", "-no-boot-anim")
                .containsSubsequence("-gpu", "swiftshader_indirect");
    }

    @Test
    void portWipeSnapshotAndExtraArgsAreForwarded() {
        List<String> args = EmulatorManager.buildArgs("a", of(Map.of("android.emulator.port", "5560", "android.emulator.wipe", "true",
                "android.emulator.snapshot.save", "true", "android.emulator.args", "-memory 3072")));
        assertThat(args).containsSubsequence("-port", "5560").contains("-wipe-data").doesNotContain("-no-snapshot-save").endsWith("-memory", "3072");
    }

    @Test
    void theGpuModeCanBeOverriddenForTheRetryWithSoftwareRendering() {
        MobileSettings s = of(Map.of("mobile.mode", "emulator"));
        assertThat(EmulatorManager.buildArgs("a", s, "swiftshader_indirect")).containsSubsequence("-gpu", "swiftshader_indirect").doesNotContain("auto");
    }

    @Test
    void recognisesAGpuInitialisationCrashInTheEmulatorLog(@org.junit.jupiter.api.io.TempDir Path tmp) throws java.io.IOException {
        Path crash = java.nio.file.Files.writeString(tmp.resolve("crash.log"), String.join(System.lineSeparator(),
                "INFO | ok", "ERROR | GPU emulation failed to initialize. Please consider..."));
        Path fine = java.nio.file.Files.writeString(tmp.resolve("fine.log"), "INFO | boot completed");
        assertThat(EmulatorManager.isGpuFailure(crash)).isTrue();
        assertThat(EmulatorManager.isGpuFailure(fine)).isFalse();
        assertThat(EmulatorManager.isGpuFailure(tmp.resolve("missing.log"))).isFalse();
    }

    @Test
    void parsesTheAvdListAndDropsEmulatorChatter() {
        String out = "INFO    | Storing crashdata in: C:\\Temp\\crash\neaf_pixel\nPixel_6_API_34\nWARNING | something odd\n\n";
        assertThat(EmulatorManager.parseAvdList(out)).containsExactly("eaf_pixel", "Pixel_6_API_34");
    }

    @Test
    void windowsRunsAppiumThroughCmdBecauseNpmInstallsACmdScript() {
        AppiumServerManager manager = new AppiumServerManager("appium", true);
        List<String> command = manager.command("http://127.0.0.1:4723", Path.of("target", "android", "appium.log"));
        assertThat(command).startsWith("cmd", "/c", "appium", "--address", "127.0.0.1", "--port", "4723");
        assertThat(command).contains("--log");
    }

    @Test
    void otherSystemsRunAppiumDirectlyOnTheConfiguredPort() {
        AppiumServerManager manager = new AppiumServerManager("/opt/bin/appium", false);
        assertThat(manager.command("http://localhost:4999", Path.of("a.log"))).startsWith("/opt/bin/appium", "--address", "localhost", "--port", "4999");
    }

    @Test
    void onlyLocalAddressesMayBeStartedByTheFramework() {
        assertThat(AppiumServerManager.isLocal("http://127.0.0.1:4723")).isTrue();
        assertThat(AppiumServerManager.isLocal("http://localhost:4723")).isTrue();
        assertThat(AppiumServerManager.isLocal("https://hub.cloud.example.com/wd/hub")).isFalse();
    }

    @Test
    void anUnreachableServerIsReportedDown() {
        assertThat(AppiumServerManager.isUp("http://127.0.0.1:1", java.time.Duration.ofMillis(500))).isFalse();
    }

    @Test
    void capabilitiesCarryTheDeviceAppAndTimeouts() {
        MobileSettings s = of(Map.of("mobile.app", "C:\\apps\\demo.apk", "mobile.app.package", "com.demo", "mobile.app.activity", "com.demo.Splash",
                "mobile.app.wait.activity", "com.demo.*"));
        Map<String, Object> caps = AndroidCapabilities.build(s, "emulator-5554").asMap();
        assertThat(String.valueOf(caps.get("platformName"))).isEqualToIgnoringCase("android");
        assertThat(caps).containsEntry("appium:automationName", "UIAutomator2")
                .containsEntry("appium:udid", "emulator-5554")
                .containsEntry("appium:app", "C:\\apps\\demo.apk")
                .containsEntry("appium:appPackage", "com.demo")
                .containsEntry("appium:appActivity", "com.demo.Splash")
                .containsEntry("appium:appWaitActivity", "com.demo.*")
                .containsEntry("appium:autoGrantPermissions", true)
                .containsEntry("appium:disableWindowAnimation", true)
                .containsKeys("appium:newCommandTimeout", "appium:adbExecTimeout");
    }

    @Test
    void withoutASerialAppiumPicksTheOnlyDeviceAndExtraCapabilitiesArePassedThrough() {
        MobileSettings s = MobileSettings.from(Map.of("mobile.app", "x.apk")::get, Map.of("language", "en", "ignoreHiddenApiPolicyError", "true", "adbPort", "5038"));
        UiAutomator2Options options = AndroidCapabilities.build(s, null);
        Map<String, Object> caps = options.asMap();
        assertThat(caps).doesNotContainKey("appium:udid")
                .containsEntry("appium:language", "en").containsEntry("appium:ignoreHiddenApiPolicyError", true).containsEntry("appium:adbPort", 5038);
    }

    @Test
    void textLocatorsEscapeQuotes() {
        assertThat(Locators.escape("Say \"hi\"")).isEqualTo("Say \\\"hi\\\"");
        assertThat(Locators.text("OK").toString()).contains("new UiSelector().text(\"OK\")");
    }

    @Test
    void accelerationCheckReadsWindowsHypervisorOutput() {
        AndroidDoctor.Check ok = AndroidDoctor.accelerationCheck("accel:\n0\nWHPX(10.0.26200) is installed and usable.\naccel\n", true);
        assertThat(ok.status()).isEqualTo(AndroidDoctor.Status.OK);
        assertThat(ok.detail()).contains("WHPX");
        AndroidDoctor.Check bad = AndroidDoctor.accelerationCheck("accel:\n1\nHyper-V not enabled\n", true);
        assertThat(bad.status()).isEqualTo(AndroidDoctor.Status.WARN);
        assertThat(bad.detail()).contains("Windows Hypervisor Platform");
        assertThat(AndroidDoctor.accelerationCheck("nothing", false).detail()).contains("KVM");
    }

    @Test
    void windowsWrapsCommandsInCmdOthersDoNot() {
        assertThat(Exec.viaShell(true, List.of("appium", "--version"))).containsExactly("cmd", "/c", "appium", "--version");
        assertThat(Exec.viaShell(false, List.of("appium", "--version"))).containsExactly("appium", "--version");
    }
}
