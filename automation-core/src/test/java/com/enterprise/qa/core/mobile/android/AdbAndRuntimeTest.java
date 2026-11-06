package com.enterprise.qa.core.mobile.android;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdbAndRuntimeTest {

    private static final String OUTPUT = """
            * daemon not running; starting now at tcp:5037
            * daemon started successfully
            List of devices attached
            emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1
            R58M12345XYZ           device usb:1-2 product:a52sxq model:SM_A528B device:a52s transport_id:2
            0123456789ABCDEF       unauthorized usb:1-3 transport_id:3
            192.168.1.20:5555      offline transport_id:4
            """;

    @Test
    void parsesEveryDeviceStateAndIgnoresDaemonChatter() {
        List<Adb.Device> devices = Adb.parseDevices(OUTPUT);
        assertThat(devices).extracting(Adb.Device::serial).containsExactly("emulator-5554", "R58M12345XYZ", "0123456789ABCDEF", "192.168.1.20:5555");
        assertThat(devices).extracting(Adb.Device::state).containsExactly("device", "device", "unauthorized", "offline");
        assertThat(devices.get(0).isEmulator()).isTrue();
        assertThat(devices.get(1).isEmulator()).isFalse();
        assertThat(devices.get(1).model()).isEqualTo("SM_A528B");
        assertThat(devices.get(2).isOnline()).isFalse();
    }

    @Test
    void parsesTheNoPermissionsStateWhichContainsASpace() {
        List<Adb.Device> devices = Adb.parseDevices("List of devices attached\nXYZ no permissions (user in plugdev group; are your udev rules wrong?); see [http://developer.android.com/tools/device.html]\n");
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).state()).isEqualTo("no permissions");
    }

    @Test
    void anEmptyListGivesNoDevices() {
        assertThat(Adb.parseDevices("List of devices attached\n\n")).isEmpty();
    }

    private static final List<Adb.Device> MIXED = Adb.parseDevices(OUTPUT);

    @Test
    void selectsTheDeviceNamedByUdid() {
        assertThat(AndroidRuntime.selectDevice(MIXED, "R58M12345XYZ").serial()).isEqualTo("R58M12345XYZ");
        assertThat(AndroidRuntime.selectDevice(MIXED, "emulator-5554").isEmulator()).isTrue();
    }

    @Test
    void explainsAnUnauthorizedDevice() {
        assertThatThrownBy(() -> AndroidRuntime.selectDevice(MIXED, "0123456789ABCDEF"))
                .hasMessageContaining("unauthorized").hasMessageContaining("Allow USB debugging");
    }

    @Test
    void aMissingUdidListsWhatAdbSees() {
        assertThatThrownBy(() -> AndroidRuntime.selectDevice(MIXED, "NOPE"))
                .hasMessageContaining("'NOPE' is not connected").hasMessageContaining("emulator-5554");
    }

    @Test
    void withoutUdidTheOnlyOnlinePhoneIsChosenAndEmulatorsAreIgnored() {
        List<Adb.Device> one = Adb.parseDevices("emulator-5554 device\nR58 device model:Pixel\n");
        assertThat(AndroidRuntime.selectDevice(one, null).serial()).isEqualTo("R58");
    }

    @Test
    void severalPhonesNeedAnExplicitUdid() {
        List<Adb.Device> two = Adb.parseDevices("AAA device\nBBB device\n");
        assertThatThrownBy(() -> AndroidRuntime.selectDevice(two, null)).hasMessageContaining("Several devices").hasMessageContaining("android.udid");
    }

    @Test
    void onlyEmulatorsConnectedMeansNoPhone() {
        List<Adb.Device> onlyEmulator = Adb.parseDevices("emulator-5554 device\n");
        assertThatThrownBy(() -> AndroidRuntime.selectDevice(onlyEmulator, null))
                .hasMessageContaining("No physical device").hasMessageContaining("mobile.mode=emulator");
    }

    @Test
    void anOfflineOnlyPhoneIsExplained() {
        List<Adb.Device> offline = Adb.parseDevices("AAA offline\n");
        assertThatThrownBy(() -> AndroidRuntime.selectDevice(offline, null)).hasMessageContaining("offline").hasMessageContaining("Reconnect");
    }
}
