# Adreno Performance Manager

Adreno Performance Manager is an Android utility for **monitoring Qualcomm Adreno GPUs** and applying the controls that a particular device exposes. It is not an overclocking tool: the app and the ADB examples below can only select limits, modes, and drivers already provided by Android or the vendor kernel. Thermal, battery, and firmware safeguards remain in control.

![Adreno Performance Manager running on a Galaxy Z Fold4](example.gif)

## What the app actually does

The available controls depend on the phone, Android release, vendor kernel, and privilege level. The in-app **Device Capabilities & Feature Tiers** card is the source of truth for the current device.

| Area | What is available | Access required |
| --- | --- | --- |
| Monitoring | GPU model, current frequency, temperature, driver frequency limit, and KGSL power level when their sysfs nodes are readable. | No special access where the kernel permits reads. |
| Turbo and KGSL power constraint | Requests the native KGSL Turbo switch or a minimum/maximum power-level constraint. These only work on compatible Adreno/KGSL drivers. | No ADB or root for the native request itself. |
| Game Mode profiles | Sets Android Game Mode plus a `game_overlay` profile with a selected mode and FPS cap. This reduces load or caps FPS; it does **not** raise GPU clocks. | Shizuku/ADB shell or root. |
| Fixed Performance Mode | Uses Android's power-service fixed-performance command when implemented by the device. | Shizuku/ADB shell or root. |
| GPU governor and frequency limits | Writes vendor KGSL/devfreq sysfs controls where they exist and are writable. | Root in the app; many production kernels keep these nodes read-only to ADB. |
| Experimental toggles | Framework thermal-status override, Skia Vulkan UI renderer, and per-app ANGLE settings. Their benefit is unverified and they can be reverted with **Restore Defaults**. | Shizuku/ADB shell or root, subject to Android restrictions. |

## Privilege levels

The original claim that the application never needs ADB or root was incomplete. The app can open and use its monitoring and native KGSL controls without either, but the advanced options have tiers:

1. **Tier 3 — unprivileged:** diagnostics and any native KGSL operation supported by the driver.
2. **Tier 2 — Shell/Shizuku:** Game Mode profiles, fixed-performance mode, and experimental framework settings. Start [Shizuku](https://shizuku.rikka.app/) with wireless debugging or a computer, then grant the app its Shizuku permission.
3. **Tier 1 — root:** KGSL/devfreq governor and min/max-frequency writes, if those sysfs interfaces are present.

The app automatically prefers root when it is available, otherwise an authorized Shizuku service, and otherwise unprivileged operation.

## Before tuning

- This project targets **Qualcomm Adreno/KGSL** devices. A GPU name or a sysfs path that looks similar is not a guarantee that a control works.
- Record the current values before changing anything. The app's **Restore Defaults** button rolls back settings it applied during its saved session; manual ADB changes must be reversed manually.
- Test one change at a time with the game restarted, and compare frame pacing, temperature, and power draw rather than relying on a benchmark score alone.
- Higher minimum clocks, Turbo, and fixed-performance modes can increase heat and battery use. Android and the vendor thermal service may still throttle performance.
- Do not disable thermal protection to chase a benchmark. The app labels its thermal-status override as experimental because it may only change a framework-reported value, not physical throttling.

## ADB performance-tuning options

These commands are useful alternatives to the Tier 2 app controls, or for testing a change before using a profile in the app. They are **device- and Android-version-dependent**. Run them from a trusted computer with USB or wireless debugging enabled:

```sh
adb devices
adb shell getprop ro.build.version.release
adb shell pm list packages | grep -i '<game-name>'
```

Replace `com.example.game` with the package name. Check the command's exit/output and verify the result after each change; some OEM builds reject or ignore shell commands.

### 1. Android Game Mode and FPS/load profile

Use Performance Game Mode first; it is Android's supported per-game policy mechanism. The game normally needs a full restart.

```sh
# Apply Android Game Mode performance (the same numeric mode used by the app).
adb shell cmd game mode 2 com.example.game

# Request a 120 FPS, full-resolution performance overlay. This caps/reduces
# workload when supported; it does not force the GPU clock to 120 MHz or boost it.
adb shell device_config put game_overlay com.example.game mode=2,fps=120,downscaleFactor=1.00

# Inspect and remove the app-specific overlay; return Game Mode to Standard.
adb shell device_config get game_overlay com.example.game
adb shell device_config delete game_overlay com.example.game
adb shell cmd game mode 1 com.example.game
```

Choose an FPS cap the display and game support (for example, 60, 90, 120, or 144). A lower cap can improve frame consistency and thermals; a higher value is not a guarantee that the game will render faster. An overlay can be ignored when the game supplies its own Game Mode configuration.

### 2. Fixed Performance Mode

Some Android builds implement a power-service fixed-performance mode. It is a system policy request, not a guaranteed GPU-clock lock.

```sh
adb shell cmd power set-fixed-performance-mode-enabled true

# Revert when finished.
adb shell cmd power set-fixed-performance-mode-enabled false
```

If the command is unavailable or reports an error, leave it off rather than attempting to replace thermal or power services.

### 3. Display policy and compositor overhead

These settings can affect perceived responsiveness, but they are not GPU overclocking. First inspect the device's supported display modes:

```sh
adb shell dumpsys display | grep -iE 'mode|refresh'

# Substitute a refresh rate the display actually advertises, then restore the
# previous values (often 0) after testing.
adb shell settings get system peak_refresh_rate
adb shell settings get system min_refresh_rate
adb shell settings put system peak_refresh_rate 120
adb shell settings put system min_refresh_rate 120

# Disable non-essential window blur, then restore it.
adb shell settings put global disable_window_blurs 1
adb shell settings delete global disable_window_blurs
```

For UI responsiveness testing only, animation scales can be shortened. This changes perceived transition time, not game rendering performance:

```sh
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5

# Restore Android defaults.
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
```

### 4. Compile a game ahead of testing

Ahead-of-time/profile compilation may reduce launch or Java-side stutter on supported builds. It does not change native GPU performance and can take time or be ignored by the package manager.

```sh
adb shell cmd package compile -m speed-profile -f com.example.game
```

Use `adb shell cmd package help` on the target device if its package command uses different compiler-filter names.

### 5. Renderer experiments (advanced)

The app exposes SkiaVK and per-app ANGLE as experimental options because compatibility varies widely. Prefer the app's controls and **Restore Defaults** so the original state is retained. For a manual Skia Vulkan UI-renderer experiment:

```sh
adb shell getprop debug.hwui.renderer
adb shell setprop debug.hwui.renderer skiavk

# Restore the usual OpenGL HWUI renderer, or reboot to clear a debug property.
adb shell setprop debug.hwui.renderer opengl
```

This targets Android's system UI renderer, not necessarily a game's renderer. Modern games commonly select Vulkan or OpenGL ES themselves, so forcing a framework renderer may have no benefit or may cause rendering issues.

## Diagnostics and measurement

Use these commands to observe a change rather than assuming it worked:

```sh
# GPU/KGSL nodes vary by vendor. Missing or permission-denied nodes are normal.
adb shell cat /sys/class/kgsl/kgsl-3d0/gpuclk
adb shell cat /sys/class/kgsl/kgsl-3d0/gpubusy
adb shell cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq

# Frame statistics for a running application.
adb shell dumpsys gfxinfo com.example.game framestats

# Framework thermal state.
adb shell dumpsys thermalservice
```

The app displays readable GPU temperature and frequency information, and its monitoring components can sample GPU busy time and before/after frame timing where the device permits it. Values may be unavailable because vendor kernels expose different paths or restrict reads.

## Known limitations

- Turbo can turn off when the GPU is idle; applying it while the game is active may help on compatible drivers.
- Some devices can remain at a low GPU frequency after screen lock with Turbo enabled. Restart the device if that happens.
- Frequency reporting supports legacy `gpuclk`/`clock_mhz` paths and common KGSL/devfreq `cur_freq` paths, but there is no universal Android GPU sysfs layout.
- A writable governor or frequency node is not expected on many stock kernels. Do not use root to bypass a vendor's thermal or hardware safety limits.

## Build

The project is an Android Gradle application (min SDK 25, target/compile SDK 34) with an arm64-v8a native component.

```sh
./gradlew assembleDebug
```

## Third-party software

- [libadrenotools](https://github.com/bylaws/libadrenotools/)
