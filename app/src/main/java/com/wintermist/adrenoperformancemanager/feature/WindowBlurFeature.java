package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

public class WindowBlurFeature implements FeatureModule {

    private Status status = Status.AVAILABLE;
    private String snapshotValue;

    @Override
    public String id() {
        return "window_blur_toggle";
    }

    @Override
    public String name() {
        return "Disable Window Blurs";
    }

    @Override
    public String description() {
        return "Disables GPU window blurring to reduce composition load";
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
        String val = backend.executeCommand("settings get global disable_window_blurs");
        this.snapshotValue = (val != null && !val.startsWith("ERROR")) ? val.trim() : "0";
        return snapshotValue;
    }

    @Override
    public boolean apply(PrivilegeBackend backend, String config) {
        if (backend == null || !backend.isAvailable()) {
            status = Status.ERROR;
            return false;
        }

        boolean disable = config == null || config.isEmpty() || Boolean.parseBoolean(config) || "1".equals(config);

        if (snapshotValue == null) {
            snapshot(backend);
        }

        String cmd = "settings put global disable_window_blurs " + (disable ? "1" : "0");
        String res = backend.executeCommand(cmd);

        boolean ok = !res.startsWith("ERROR");
        status = ok ? Status.APPLIED : Status.ERROR;
        return ok;
    }

    @Override
    public boolean rollback(PrivilegeBackend backend, String snapshotStr) {
        if (backend == null || !backend.isAvailable()) return false;

        String cmd = "settings delete global disable_window_blurs";
        backend.executeCommand(cmd);

        status = Status.AVAILABLE;
        snapshotValue = null;
        return true;
    }

    @Override
    public Status status() {
        return status;
    }
}
