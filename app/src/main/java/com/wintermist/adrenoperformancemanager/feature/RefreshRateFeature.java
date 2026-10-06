package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;

public class RefreshRateFeature implements FeatureModule {

    private Status status = Status.AVAILABLE;
    private String snapshotPeak;
    private String snapshotMin;

    @Override
    public String id() {
        return "refresh_rate_control";
    }

    @Override
    public String name() {
        return "Force Display Refresh Rate";
    }

    @Override
    public String description() {
        return "Forces peak and min system refresh rates via settings put system";
    }

    @Override
    public PrivilegeBackend.Tier requiredPrivilege() {
        return PrivilegeBackend.Tier.SHELL;
    }

    @Override
    public boolean isSupported(PrivilegeBackend backend) {
        if (backend == null || !backend.isAvailable()) return false;
        return backend.getTier().getLevel() <= requiredPrivilege().getLevel();
    }

    @Override
    public String snapshot(PrivilegeBackend backend) {
        if (backend == null || !backend.isAvailable()) return null;
        String peak = backend.executeCommand("settings get system peak_refresh_rate");
        String min = backend.executeCommand("settings get system min_refresh_rate");
        this.snapshotPeak = peak != null ? peak.trim() : "0";
        this.snapshotMin = min != null ? min.trim() : "0";
        return snapshotPeak + ":" + snapshotMin;
    }

    @Override
    public boolean apply(PrivilegeBackend backend, String config) {
        // config format: "fps" (e.g., "120" or "60") or "peak:min"
        if (config == null || config.trim().isEmpty()) {
            status = Status.ERROR;
            return false;
        }

        try {
            String fps = config.contains(":") ? config.split(":")[0] : config.trim();
            int fpsVal = Integer.parseInt(fps);
            if (fpsVal < 30 || fpsVal > 240) {
                status = Status.ERROR;
                return false;
            }

            if (getSnapshot(backend) == null) {
                snapshot(backend);
            }

            String cmdPeak = "settings put system peak_refresh_rate " + fpsVal;
            String cmdMin = "settings put system min_refresh_rate " + fpsVal;

            String resPeak = backend.executeCommand(cmdPeak);
            String resMin = backend.executeCommand(cmdMin);

            boolean ok = !resPeak.startsWith("ERROR") && !resMin.startsWith("ERROR");
            status = ok ? Status.APPLIED : Status.ERROR;
            return ok;
        } catch (Exception e) {
            status = Status.ERROR;
            return false;
        }
    }

    private String getSnapshot(PrivilegeBackend backend) {
        return snapshotPeak != null ? snapshotPeak + ":" + snapshotMin : null;
    }

    @Override
    public boolean rollback(PrivilegeBackend backend, String snapshotStr) {
        if (backend == null || !backend.isAvailable()) return false;

        String peakVal = "0";
        String minVal = "0";

        if (snapshotStr != null && snapshotStr.contains(":")) {
            String[] parts = snapshotStr.split(":");
            peakVal = parts[0];
            minVal = parts[1];
        } else if (snapshotPeak != null) {
            peakVal = snapshotPeak;
            minVal = snapshotMin;
        }

        String cmdPeak = "settings put system peak_refresh_rate " + peakVal;
        String cmdMin = "settings put system min_refresh_rate " + minVal;

        backend.executeCommand(cmdPeak);
        backend.executeCommand(cmdMin);

        status = Status.AVAILABLE;
        return true;
    }

    @Override
    public Status status() {
        return status;
    }
}
