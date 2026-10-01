package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GameModeFeature implements FeatureModule {

    public static final Set<Integer> STANDARD_REFRESH_RATES = new HashSet<>(Arrays.asList(30, 60, 90, 120, 144));

    public static final String HONEST_LABEL = "Reduces GPU load / caps FPS (does not boost hardware clocks)";
    public static final String RESTART_WARNING = "The game must be restarted for overlay changes to take effect.";
    public static final String OVERRIDE_WARNING = "Note: Overlay config will be ignored if the game declares its own Game Mode support.";

    private Status status = Status.AVAILABLE;
    private String activePackage;

    @Override
    public String id() {
        return "game_mode_profile";
    }

    @Override
    public String name() {
        return "Game Mode Profiles";
    }

    @Override
    public String description() {
        return HONEST_LABEL;
    }

    @Override
    public PrivilegeBackend.Tier requiredPrivilege() {
        return PrivilegeBackend.Tier.SHELL;
    }

    @Override
    public boolean isSupported(PrivilegeBackend backend) {
        if (backend == null || !backend.isAvailable()) {
            return false;
        }
        return backend.getTier().getLevel() <= requiredPrivilege().getLevel();
    }

    public boolean validateFps(int fps) {
        return STANDARD_REFRESH_RATES.contains(fps) || (fps > 0 && fps <= 240);
    }

    @Override
    public String snapshot(PrivilegeBackend backend) {
        if (activePackage == null) return null;
        return backend.executeCommand("device_config get game_overlay " + activePackage);
    }

    @Override
    public boolean apply(PrivilegeBackend backend, String configStr) {
        // configStr format: "packageName:mode:fps:downscale:useAngle"
        if (configStr == null || !configStr.contains(":")) {
            status = Status.ERROR;
            return false;
        }

        String[] parts = configStr.split(":");
        String packageName = parts[0];
        if (!ShellUtils.isValidPackageName(packageName)) {
            status = Status.ERROR;
            return false;
        }

        int mode = parts.length > 1 ? Integer.parseInt(parts[1]) : 2;
        int fps = parts.length > 2 ? Integer.parseInt(parts[2]) : 60;
        double downscale = parts.length > 3 ? Double.parseDouble(parts[3]) : 1.0;
        boolean useAngle = parts.length > 4 && Boolean.parseBoolean(parts[4]); // Default false

        if (!validateFps(fps)) {
            status = Status.ERROR;
            return false;
        }

        this.activePackage = packageName;

        String overlayCmd = ShellUtils.buildGameOverlayCommand(packageName, mode, fps, downscale, useAngle);
        String gameModeCmd = ShellUtils.buildGameModeCommand(packageName, mode);

        String overlayRes = backend.executeCommand(overlayCmd);
        String modeRes = backend.executeCommand(gameModeCmd);

        boolean ok = !overlayRes.startsWith("ERROR") && !modeRes.startsWith("ERROR");
        status = ok ? Status.APPLIED : Status.ERROR;
        return ok;
    }

    @Override
    public boolean rollback(PrivilegeBackend backend) {
        if (activePackage == null) return true;

        String resetCmd = "device_config delete game_overlay " + activePackage;
        String resetModeCmd = ShellUtils.buildGameModeCommand(activePackage, 1); // 1 = Standard/Off

        backend.executeCommand(resetCmd);
        backend.executeCommand(resetModeCmd);

        status = Status.AVAILABLE;
        activePackage = null;
        return true;
    }

    @Override
    public Status status() {
        return status;
    }
}
