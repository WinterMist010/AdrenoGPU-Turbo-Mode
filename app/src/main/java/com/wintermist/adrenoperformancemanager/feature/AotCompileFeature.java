package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;

import java.util.Locale;

public class AotCompileFeature implements FeatureModule {

    private Status status = Status.AVAILABLE;
    private String targetPackage;

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
        return "Compiles Java/Kotlin ART bytecode ahead-of-time (cmd package compile -m speed-profile -f <package>). Improves launch times and Java-side UI jank; does not affect C++ native engine code or GPU shader compilation. May be reset by system updates or background dexopt.";
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
        return targetPackage;
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

        String pkg = pkgName.trim();
        this.targetPackage = pkg;

        String cmd = "cmd package compile -m speed-profile -f " + pkg;
        String res = backend.executeCommand(cmd);

        boolean ok = isSuccessResponse(res);
        status = ok ? Status.APPLIED : Status.ERROR;
        return ok;
    }

    public static boolean isSuccessResponse(String res) {
        if (res == null || res.trim().isEmpty()) {
            return false;
        }
        String lower = res.toLowerCase(Locale.US);
        if (lower.startsWith("error") || lower.contains("failure") || lower.contains("failed") || lower.contains("error:")) {
            return false;
        }
        return lower.contains("success") || res.trim().equalsIgnoreCase("success") || !lower.contains("error");
    }

    @Override
    public boolean rollback(PrivilegeBackend backend, String snapshotPkg) {
        if (backend == null || !backend.isAvailable()) {
            status = Status.AVAILABLE;
            return false;
        }

        String pkg = (snapshotPkg != null && ShellUtils.isValidPackageName(snapshotPkg.trim()))
                ? snapshotPkg.trim()
                : targetPackage;

        if (pkg != null && ShellUtils.isValidPackageName(pkg)) {
            String cmd = "cmd package compile --reset " + pkg;
            backend.executeCommand(cmd);
        }

        status = Status.AVAILABLE;
        targetPackage = null;
        return true;
    }

    @Override
    public Status status() {
        return status;
    }
}
