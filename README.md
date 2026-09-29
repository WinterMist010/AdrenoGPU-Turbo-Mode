# Adreno Performance Manager

This application monitors and tunes Qualcomm Adreno GPUs, including toggling Turbo mode, inspecting GPU temperature, managing devfreq governors, and monitoring frequency limits. It supports legacy Adreno devices and newer A8XX devices when their vendor KGSL/devfreq driver exposes required controls.

Example on Samsung Galaxy Z Fold4:

![Example.](example.gif)


# You should be aware of the following

1. Turbo mode only works on Adreno GPUs.

2. The application does not require ADB or ROOT access.

3. Locking the maximum GPU frequency may increase device heating ⚠️

4. Turbo mode may not work on all devices. The app reports when a driver does not expose the required control instead of silently failing.

5. Frequency reporting supports both the legacy `gpuclk` node and the `devfreq/cur_freq` nodes used by many newer A8XX vendor kernels.

6. On some devices, Turbo mode may turn off if the GPU is not under load. In this case, enable Turbo through the floating window while the game is running.

7. On some devices, if Turbo is enabled, after locking the screen the GPU may get stuck at a low frequency. To fix this, restart the device.

## Third party applications

[libadrenotools](https://github.com/bylaws/libadrenotools/).
