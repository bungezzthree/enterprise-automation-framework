package com.enterprise.qa.core.mobile;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobileSettingsTest {

    private static MobileSettings of(Map<String, String> cfg) {
        return MobileSettings.from(cfg::get, Map.of());
    }

    @Test
    void defaultsAreAWindowedEmulatorWithAnAutoStartedAppiumOnTheLocalPort() {
        MobileSettings s = of(Map.of());
        assertThat(s.platform()).isEqualTo("android");
        assertThat(s.mode()).isEqualTo(MobileMode.EMULATOR);
        assertThat(s.appiumUrl()).isEqualTo("http://127.0.0.1:4723");
        assertThat(s.appiumAutoStart()).isTrue();
        assertThat(s.appiumStartTimeoutSec()).as("a cold Appium start took about 70 s on a normal laptop").isEqualTo(150);
        assertThat(s.emulatorAutoStart()).isTrue();
        assertThat(s.emulatorStopAfter()).isTrue();
        assertThat(s.emulatorSettleSec()).isEqualTo(10);
        assertThat(s.emulatorGpu()).isEqualTo("auto");
        assertThat(s.sessionScope()).isEqualTo(MobileSettings.SessionScope.RUN);
        assertThat(s.recordVideo()).isFalse();
        assertThat(s.headless()).isFalse();
    }

    @Test
    void headlessModeUsesSoftwareGraphicsByDefault() {
        MobileSettings s = of(Map.of("mobile.mode", "headless"));
        assertThat(s.mode()).isEqualTo(MobileMode.HEADLESS);
        assertThat(s.headless()).isTrue();
        assertThat(s.mode().usesEmulator()).isTrue();
        assertThat(s.emulatorGpu()).isEqualTo("swiftshader_indirect");
    }

    @Test
    void modeIsCaseInsensitiveAndUnknownValuesAreRejectedWithAHelpfulMessage() {
        assertThat(MobileMode.parse(" Device ")).isEqualTo(MobileMode.DEVICE);
        assertThat(MobileMode.parse(null)).isEqualTo(MobileMode.EMULATOR);
        assertThatThrownBy(() -> MobileMode.parse("cloud")).hasMessageContaining("emulator, headless, device or remote");
    }

    @Test
    void readsAppDeviceAndEmulatorSettings() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("mobile.mode", "device");
        cfg.put("android.udid", "R58M12345");
        cfg.put("mobile.app", "C:\\apps\\demo.apk");
        cfg.put("mobile.app.package", "com.demo");
        cfg.put("appium.server.mode", "external");
        cfg.put("android.emulator.port", "5560");
        cfg.put("android.emulator.args", "-memory 3072  -no-boot-anim");
        cfg.put("mobile.session.scope", "scenario");
        cfg.put("mobile.record.video", "true");
        MobileSettings s = of(cfg);
        assertThat(s.udid()).isEqualTo("R58M12345");
        assertThat(s.app()).isEqualTo("C:\\apps\\demo.apk");
        assertThat(s.appiumAutoStart()).isFalse();
        assertThat(s.emulatorPort()).isEqualTo(5560);
        assertThat(s.emulatorArgs()).containsExactly("-memory", "3072", "-no-boot-anim");
        assertThat(s.sessionScope()).isEqualTo(MobileSettings.SessionScope.SCENARIO);
        assertThat(s.recordVideo()).isTrue();
    }

    @Test
    void theOlderMobileUdidKeyStillWorks() {
        assertThat(of(Map.of("mobile.udid", "emulator-5554")).udid()).isEqualTo("emulator-5554");
        assertThat(of(Map.of("android.udid", "A", "mobile.udid", "B")).udid()).isEqualTo("A");
    }
}
