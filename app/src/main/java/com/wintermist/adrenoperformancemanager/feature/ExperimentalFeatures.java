package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

public class ExperimentalFeatures {

    public static final String UNVERIFIED_LABEL = "(effect unverified)";

    public static class ThermalOverrideFeature implements FeatureModule {
        private Status status = Status.AVAILABLE;

        @Override public String id() { return "exp_thermal_override"; }
        @Override public String name() { return "Thermal Service Status Override " + UNVERIFIED_LABEL; }
        @Override public String description() { return "Overrides framework thermal status via cmd thermalservice override-status. Probably only changes framework-reported status."; }
        @Override public PrivilegeBackend.Tier requiredPrivilege() { return PrivilegeBackend.Tier.SHELL; }

        @Override
        public boolean isSupported(PrivilegeBackend backend) {
            return backend != null && backend.isAvailable() && backend.getTier().getLevel() <= 2;
        }

        @Override public String snapshot(PrivilegeBackend backend) { return "0"; }

        @Override
        public boolean apply(PrivilegeBackend backend, String config) {
            int statusLevel = config != null ? Integer.parseInt(config) : 0;
            String cmd = "cmd thermalservice override-status " + statusLevel;
            String res = backend.executeCommand(cmd);
            boolean ok = !res.startsWith("ERROR");
            status = ok ? Status.APPLIED : Status.ERROR;
            return ok;
        }

        @Override
        public boolean rollback(PrivilegeBackend backend) {
            String cmd = "cmd thermalservice reset";
            backend.executeCommand(cmd);
            status = Status.AVAILABLE;
            return true;
        }

        @Override public Status status() { return status; }
    }

    public static class SkiaVkRendererFeature implements FeatureModule {
        private Status status = Status.AVAILABLE;

        @Override public String id() { return "exp_skiavk_renderer"; }
        @Override public String name() { return "SkiaVK HWUI Renderer " + UNVERIFIED_LABEL; }
        @Override public String description() { return "Sets debug.hwui.renderer to skiavk for Vulkan UI rendering."; }
        @Override public PrivilegeBackend.Tier requiredPrivilege() { return PrivilegeBackend.Tier.SHELL; }

        @Override
        public boolean isSupported(PrivilegeBackend backend) {
            return backend != null && backend.isAvailable() && backend.getTier().getLevel() <= 2;
        }

        @Override public String snapshot(PrivilegeBackend backend) { return "opengl"; }

        @Override
        public boolean apply(PrivilegeBackend backend, String config) {
            String cmd = "setprop debug.hwui.renderer skiavk";
            String res = backend.executeCommand(cmd);
            boolean ok = !res.startsWith("ERROR");
            status = ok ? Status.APPLIED : Status.ERROR;
            return ok;
        }

        @Override
        public boolean rollback(PrivilegeBackend backend) {
            String cmd = "setprop debug.hwui.renderer opengl";
            backend.executeCommand(cmd);
            status = Status.AVAILABLE;
            return true;
        }

        @Override public Status status() { return status; }
    }

    public static class PerAppAngleFeature implements FeatureModule {
        private Status status = Status.AVAILABLE;
        private String targetPkg;

        @Override public String id() { return "exp_per_app_angle"; }
        @Override public String name() { return "Per-App ANGLE Driver " + UNVERIFIED_LABEL; }
        @Override public String description() { return "Forces ANGLE OpenGL ES driver for specified package (may need root or debuggable app)."; }
        @Override public PrivilegeBackend.Tier requiredPrivilege() { return PrivilegeBackend.Tier.SHELL; }

        @Override
        public boolean isSupported(PrivilegeBackend backend) {
            return backend != null && backend.isAvailable() && backend.getTier().getLevel() <= 2;
        }

        @Override public String snapshot(PrivilegeBackend backend) { return null; }

        @Override
        public boolean apply(PrivilegeBackend backend, String pkgName) {
            if (pkgName == null || pkgName.isEmpty()) return false;
            this.targetPkg = pkgName;
            String cmd = "device_config put game_overlay " + pkgName + " useAngle=true";
            String res = backend.executeCommand(cmd);
            boolean ok = !res.startsWith("ERROR");
            status = ok ? Status.APPLIED : Status.ERROR;
            return ok;
        }

        @Override
        public boolean rollback(PrivilegeBackend backend) {
            if (targetPkg != null && !targetPkg.isEmpty()) {
                String cmd = "device_config delete game_overlay " + targetPkg;
                backend.executeCommand(cmd);
            }
            status = Status.AVAILABLE;
            targetPkg = null;
            return true;
        }

        @Override public Status status() { return status; }
    }
}
