# Android automation: setup and run guide

The `android-tests` pilot runs 10 end-to-end scenarios against the Sauce Labs *Swag Labs* sample app with Appium
(UiAutomator2). The same framework code (`automation-core`, package `...core.mobile`) serves any Android app: write
features, a few screen objects and steps, and choose **where** the tests run with one switch.

## 1. Where the tests run

| `mobile.mode` | Maven profile | What happens |
|---|---|---|
| `emulator` (default) | `-Pemulator` | Starts the AVD **with a window** if it is not running, runs the tests, stops it again. An emulator that was already running is reused and left running. |
| `headless` | `-Pheadless` | Same AVD **without a window** (`-no-window -no-audio -no-boot-anim`, software graphics). For CI servers and background runs. |
| `device` | `-Pdevice` | A **physical phone or tablet** connected by USB or Wi-Fi debugging. Picked automatically when exactly one is connected, otherwise set `android.udid`. Never started or stopped by the framework. |
| `remote` | `-Premote` | A device farm or remote Appium server. Set `appium.server.url` (and capabilities with `mobile.cap.*`); no local device handling. |

```bash
.\mvnw.cmd -pl android-tests test                 # emulator with a window
.\mvnw.cmd -pl android-tests test -Pheadless      # emulator without a window
.\mvnw.cmd -pl android-tests test -Pdevice        # real phone
.\mvnw.cmd -pl android-tests test "-Dcucumber.filter.tags=@tc:AND-001"   # one test case
```

On macOS/Linux use `./mvnw`. In every mode the framework also **starts Appium for you** when no server answers at
`appium.server.url` (and stops it afterwards). Set `appium.server.mode=external` to manage Appium yourself.

## 2. Prerequisites

| Need | Windows | macOS | Linux |
|---|---|---|---|
| JDK 21 | any distribution; set `JAVA_HOME` (`mvnw` needs it) | same | same |
| Node.js | current LTS (see appium.io for the supported range) | same | same |
| Appium 3 + UiAutomator2 | `npm install -g appium` then `appium driver install uiautomator2` | same | same |
| Android SDK | Android Studio installs it to `%LOCALAPPDATA%\Android\Sdk` | `~/Library/Android/sdk` | `~/Android/Sdk` |
| SDK packages | *Platform-Tools*, *Android Emulator*, one *SDK Platform* and one *System Image* (Android Studio > SDK Manager) | same (use an **arm64** image on Apple silicon) | same |
| Hardware acceleration | Windows Features > **Windows Hypervisor Platform** (and virtualization on in the BIOS) | automatic (Hypervisor.framework) | **KVM**: install `qemu-kvm`, add your user to the `kvm` group |

`ANDROID_HOME` is **optional**: the framework finds the SDK in the default folder of your OS and passes it to the Appium
it starts (Appium itself refuses to create a session without it). Set it yourself only if you start Appium by hand or use
a non-default location (`android.sdk.root` also works).

### Check the machine

`AndroidDoctor` verifies everything the chosen mode needs and says what to fix:

```bash
.\mvnw.cmd -q -pl android-tests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.enterprise.qa.core.mobile.android.AndroidDoctor -Dexec.classpathScope=test
```

Or in IntelliJ: open `automation-core/.../mobile/android/AndroidDoctor.java` and run its `main`. Add `-Dmobile.mode=headless`
(or `device`) to see the checks for that mode.

## 3. Create an AVD (emulator and headless modes)

Android Studio > **Device Manager** > *Create Virtual Device* (for example Pixel 6, a Google Play or Google APIs image,
3 GB RAM or more). Or from the command line, with the SDK *Command-line Tools* package installed:

```bash
avdmanager create avd -n eaf_pixel -k "system-images;android-36;google_apis_playstore;x86_64" -d pixel_6
```

Then set the name in `android-tests/src/test/resources/config/default.properties`: `android.avd=eaf_pixel`
(or `-Dandroid.avd=<name>`). `emulator -list-avds` shows what exists.

The first boot of a new AVD takes a few minutes. Cold boots are never saved (`-no-snapshot-save`), so a run does not change the AVD.

## 4. Real device

1. Phone: *Settings > About phone*, tap **Build number** seven times; then *Developer options > USB debugging* on.
2. Connect the cable and accept **Allow USB debugging** on the phone. `adb devices` must list it as `device`
   (not `unauthorized` or `offline`).
3. `.\mvnw.cmd -pl android-tests test -Pdevice`. With several devices connected, add `-Dandroid.udid=<serial from adb devices>`.
4. Wi-Fi instead of a cable (Android 11+): *Developer options > Wireless debugging*, then `adb pair <ip:port>` and `adb connect <ip:port>`.

`mobile.app` may be a URL (Appium downloads it) or a local path to an `.apk`. The APK must contain the phone's CPU
architecture (most phones are arm64).

## 5. Headless and CI

`-Pheadless` runs the AVD without a window and needs no display, so it works on Jenkins agents and in containers.

* **Linux agents:** KVM must be available (`ls /dev/kvm`); otherwise the emulator is too slow. Use an `x86_64` image on x86 hosts.
* **Windows agents:** run the Jenkins agent as a logged-in user or service with access to the SDK; WHPX must be enabled.
* The emulator started for a run is stopped when the JVM ends, even if tests fail. Logs: `android-tests/target/android/emulator-<avd>.log`
  and `appium.log`.
* Jenkins stage example: `sh './mvnw -B -pl android-tests test -Pheadless -Dmobile.app=$WORKSPACE/apps/swaglabs.apk'`.
* Give the emulator at least 3 GB RAM and 2 cores; a starved emulator shows "... isn't responding" dialogs (the framework presses *Wait* for you, but slow runs remain slow).

## 6. Running from IntelliJ IDEA (or another IDE)

1. Open the repository as a Maven project, use JDK 21, install the *Cucumber for Java* and *Gherkin* plugins.
2. Run `RunCucumberTest` (right-click > Run). Choose the target with **Run > Edit Configurations > VM options**:
   `-Dmobile.mode=headless`, `-Dmobile.mode=device`, `-Dandroid.avd=<name>`, `-Dmobile.app=<path>`.
3. One test case: add `-Dcucumber.filter.tags=@tc:AND-001`. In the Maven tool window you can also tick a profile
   (`headless`, `device`) and run `android-tests > Lifecycle > test`.
4. A single `.feature` or scenario can be run with the green gutter arrow, as long as the run configuration uses the glue
   `com.enterprise.qa.core.steps.mobile,com.enterprise.qa.android`.

## 7. Settings (`config/default.properties`, `-D...`, or environment variables)

| Key | Default | Meaning |
|---|---|---|
| `mobile.mode` | `emulator` | `emulator`, `headless`, `device`, `remote` (set by the Maven profile) |
| `android.avd` | - | AVD name for emulator and headless |
| `android.udid` | - | Device serial; also lets you run on an emulator that is already up (`emulator-5554`) |
| `android.sdk.root` | auto | SDK location when it is not in the default folder and `ANDROID_HOME` is not set |
| `appium.server.url` | `http://127.0.0.1:4723` | Appium address |
| `appium.server.mode` | `auto` | `auto` starts a local Appium when none answers; `external` never does |
| `appium.command` | `appium` | CLI name or path |
| `appium.start.timeout.s` | `150` | Time allowed for a cold Appium start (about 70 s on a normal laptop) |
| `mobile.app` | - | `.apk` path or URL |
| `mobile.app.package`, `.activity`, `.wait.activity` | - | Needed to relaunch the app between scenarios; the wait pattern covers apps that start with a splash activity |
| `mobile.no.reset` | `false` | Keep app data between sessions |
| `mobile.timeout.ms` | `15000` | Explicit wait for every element |
| `mobile.session.scope` | `run` | `run`: one session for all scenarios and the app is relaunched between them (fast); `scenario`: a new session each time |
| `mobile.record.video` | `false` | Attach a screen recording to failed scenarios (all scenarios with `evidence.mode=all`) |
| `mobile.cap.<name>` | - | Any other Appium capability, e.g. `mobile.cap.language=en` |
| `android.emulator.autostart` | `true` | Start the AVD when it is not running |
| `android.emulator.stop` | `true` | Stop an emulator this run started |
| `android.emulator.boot.timeout.s` | `600` | Time allowed for the emulator to finish booting |
| `android.emulator.settle.s` | `10` | Pause after boot, so Android is idle before the first tap |
| `android.emulator.gpu` | `auto` (`swiftshader_indirect` when headless) | `-gpu` mode |
| `android.emulator.port`, `.args`, `.wipe`, `.snapshot.save` | - | Extra emulator control |
| `evidence.mode` | `failed` | `none`, `failed`, `all`: screenshots; a failed scenario also gets the UI hierarchy (`page-source.xml`, to fix locators) and the last logcat lines |

## 8. Writing a new Android test

1. **Feature file** with `@tc:<ID>` and category tags, in business language (same rules as API and Web).
2. **Screen object** per app screen in `...android/screens/`, built on `MobileActions` (explicit waits) and `Locators`.
   Prefer, in this order: accessibility id (`acc`), resource id (`id`), visible text (`text`), XPath. Use **Appium Inspector**
   on the running emulator to read them, or the `page-source.xml` attached to any failed scenario.
3. **Step class** that calls the screen objects. Ready-made steps (`the app is launched`, `I relaunch the app`,
   `I press the back button`, `I tap the text ...`, `I should see the text ...`) are in `CommonMobileSteps`.
4. Add the test case to `testcases/catalog.csv` and run `-Dcucumber.execution.dry-run=true` first to find undefined steps (no device needed).

## 9. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Neither ANDROID_HOME nor ANDROID_SDK_ROOT environment variable was exported` | You started Appium yourself. Set `ANDROID_HOME`, or let the framework start it (`appium.server.mode=auto`). |
| `AVD 'x' does not exist. Available AVDs: [...]` | Wrong `android.avd`. Create the AVD (section 3) or use one of the listed names. |
| `Android emulator is not installed` | Install *Android Emulator* in SDK Manager > SDK Tools, or use `-Pdevice`. |
| Emulator never finishes booting | No hardware acceleration (run `AndroidDoctor`), or too little RAM. Raise `android.emulator.boot.timeout.s`. |
| `... isn't responding` dialog in the screenshot | The emulator is overloaded. Windowed mode with GPU, more RAM and a settle pause help; the framework dismisses these dialogs before each scenario. |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | The APK has no native code for the device CPU (for example an arm-only APK on an x86 emulator). Use a matching system image or APK. |
| `Device '...' is unauthorized` | Unlock the phone and accept *Allow USB debugging*. |
| `Several devices are connected` | Set `android.udid=<serial>`. |
| `No Appium server answers at ...` | Remote or `external` address is wrong or the server is down. |
| Everything fails after the first scenario error | By design the run fails fast when the session or device cannot be prepared, instead of retrying a slow start for every scenario. Fix the first error in the log. |
| Port 4723 already in use | An old Appium is still running: stop it, or set a different port in `appium.server.url`. |
