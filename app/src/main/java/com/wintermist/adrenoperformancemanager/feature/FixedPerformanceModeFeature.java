package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

public class FixedPerformanceModeFeature implements FeatureModule {

    private Status status = Status.AVAILABLE;

    @Override
    public String id() {
        return "fixed_performance_mode";
    }

    @Override
    public String name() {
        return "Fixed Performance Mode";
    }

    @Override
    public String description() {
        return "Toggles fixed performance mode via power service (device-dependent)";
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
        return "false";
    }

    @Override
    public boolean apply(PrivilegeBackend backend, String config) {
        boolean enable = Boolean.parseBoolean(config);
        String cmd = "cmd power set-fixed-performance-mode-enabled " + enable;
        String res = backend.executeCommand(cmd);

        boolean ok = !res.startsWith("ERROR");
        status = ok ? (enable ? Status.APPLIED : Status.AVAILABLE) : Status.ERROR;
        return ok;
    }

    @Override
    public boolean rollback(PrivilegeBackend backend) {
        return apply(backend, "false");
    }

    @Override
    public Status status() {
        return status;
    }
}
