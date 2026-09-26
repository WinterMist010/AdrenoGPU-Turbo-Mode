# AdrenoGPU-Turbo-Mode

This application activates Turbo mode on Adreno GPUs, which can request the highest available GPU frequency. It supports legacy Adreno devices and newer A8XX devices when their vendor KGSL driver exposes Turbo control.

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
