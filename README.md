# Adreno Performance Manager

Adreno Performance Manager is an Android utility for **monitoring Qualcomm Adreno GPUs** and applying hardware and system controls supported by the device. It is not an overclocking tool: all controls rely on limits, power levels, and profiles provided by Android or the vendor kernel. Thermal, battery, and firmware safeguards remain strictly in control.

![Adreno Performance Manager running on a Galaxy Z Fold4](example.gif)

## Strict AI Usage Disclaimer

> **IMPORTANT DISCLAIMER REGARDING AI-GENERATED CONTENT AND ASSISTANCE:**
>
> 1. **No Unverified AI Code or Advice:** Parts of this software, documentation, or associated scripts may be developed or refactored with the assistance of Artificial Intelligence (AI) tools. However, code and configuration suggestions must be strictly verified against device capabilities, kernel interfaces, and Android documentation.
> 2. **No Warranties or Guarantees:** AI models can hallucinate non-existent sysfs paths, driver flags, or performance claims. Never assume an AI-suggested command or feature is safe or effective without hardware and kernel verification.
> 3. **User Responsibility:** Modifying system settings or applying GPU power constraints is performed at your own risk. Neither the developers nor AI contributors accept liability for hardware instability, overheating, data loss, or unintended device behavior.

---

## Privilege Tiers & Controls

The available options depend on your phone, Android version, vendor kernel, and granted permissions:

| Tier | Access Level | Available Capabilities |
| --- | --- | --- |
| **Tier 3 (Unprivileged)** | No Root / No ADB / No Shizuku | Native KGSL Turbo request & min/max power-level constraints (where supported by driver), real-time sysfs monitoring (frequency, temperature), floating performance overlay, and CSV session diagnostics export. |
| **Tier 2 (Shell / Shizuku)** | Shizuku or ADB Shell | Android Game Mode profiles, `game_overlay` FPS/downscale caps, power fixed-performance mode, refresh rate forcing, window blur toggles, ahead-of-time package compilation, and experimental renderer toggles. |
| **Tier 1 (Root)** | Root (Magisk / KernelSU) | Direct sysfs hardware governor selection and minimum/maximum GPU clock frequency limits. |

---

## Improving Performance Options for Non-Root & Shizuku Users

### For Unprivileged Users (Non-Root, Non-ADB)
Even without elevated ADB or root privileges, users can leverage:
- **Native KGSL Constraints:** Native driver ioctls allow setting Turbo switches and power level constraints directly on supported kernel drivers.
- **Diagnostics & Monitoring:** Real-time GPU frequency, busy stats, and temperature monitoring via readable sysfs nodes, coupled with a floating overlay and CSV session export.
- **In-App Capability Diagnostics:** Generates report detailing which driver sysfs nodes and system APIs are readable or actionable on the current device.

### For Shizuku Users (ADB-level Access)
With Shizuku authorized, users gain seamless access to Android framework performance tools without needing a connected PC:
- **Android Game Mode & Overlay Profiles:** Configure per-app Game Mode (`Performance` / `Battery`) and FPS limits (`60`, `90`, `120`, `144 FPS`).
- **Fixed Performance Mode:** System power-service policy requests to maintain consistent clock targets.
- **Display & Rendering Tweaks:** Force display refresh rates (`peak_refresh_rate` / `min_refresh_rate`), toggle window blurs (`disable_window_blurs`), and adjust transition animation scales.
- **Package Pre-Compilation:** Run ahead-of-time (AOT) profile compilation (`cmd package compile -m speed-profile`) to reduce Java-side stutter in demanding games.

---

## Known Limitations & Safety Safeguards

- **Thermal Safeguards:** Android and vendor thermal services will throttle performance if temperatures rise above thermal thresholds. Do not attempt to bypass thermal protections.
- **Driver Differences:** sysfs paths and KGSL controls vary significantly across Qualcomm SoCs and Android vendor releases. Missing or read-only sysfs nodes are expected behavior on stock kernels.
- **Game Mode Overlays:** Custom `game_overlay` settings require restarting the game to take effect and will be ignored if the application defines its own custom Game Mode configuration.

---

## Build Instructions

The project is an Android Gradle application (min SDK 25, target/compile SDK 34) featuring native C++ JNI components (`libadrenotools`).

```sh
./gradlew assembleDebug
```

---

## Third-Party Software

- [libadrenotools](https://github.com/bylaws/libadrenotools/)
