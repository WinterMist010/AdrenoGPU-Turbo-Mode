package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;

public class AotCompileFeature implements FeatureModule {

    private Status status = Status.AVAILABLE;

    @Override
    public String id() {
        return "aot_compile_game";
    }

    @Override
    public String name() {
        return "AOT Game Speed-Profile Compilation";
    }

    @Override
    public String description() {
        return "Compiles application code ahead-of-time (cmd package compile -m speed-profile -f <package>) to reduce runtime CPU/GPU compilation stutter";
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
        return null; // Package compilation cannot be directly snapshotted or reverted easily
    }

    @Override
    public boolean apply(PrivilegeBackend backend, String pkgName) {
        if (backend == null || !backend.isAvailable()) {
            status = Status.ERROR;
            return false;
        }

        if (pkgName == null || !ShellUtils.isValidPackageName(pkgName.trim())) {
            status = Status.ERROR;
            return false;
        }

        String cmd = "cmd package compile -m speed-profile -f " + pkgName.trim();
        String res = backend.executeCommand(cmd);

        boolean ok = !res.startsWith("ERROR");
        status = ok ? Status.APPLIED : Status.ERROR;
        return ok;
    }

    @Override
    public boolean rollback(PrivilegeBackend backend, String snapshot) {
        status = Status.AVAILABLE;
        return true;
    }

    @Override
    public Status status() {
        return status;
    }
}
