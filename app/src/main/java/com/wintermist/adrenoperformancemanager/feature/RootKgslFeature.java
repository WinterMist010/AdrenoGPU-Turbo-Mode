package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RootKgslFeature implements FeatureModule {

    public static final double DEFAULT_MAX_TEMP_C = 80.0;

    private static final String[] PWRLEVEL_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/min_pwrlevel",
            "/sys/class/kgsl/kgsl-3d0/max_pwrlevel",
            "/sys/class/kgsl/kgsl-3d0/pwrlevel"
    };

    private static final String[] GOVERNOR_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/devfreq/kgsl-3d0/governor",
            "/sys/class/devfreq/3d00000.gpu/governor"
    };

    private static final String[] MIN_FREQ_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            "/sys/class/devfreq/kgsl-3d0/min_freq",
            "/sys/class/devfreq/3d00000.gpu/min_freq"
    };

    private static final String[] MAX_FREQ_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/devfreq/kgsl-3d0/max_freq",
            "/sys/class/devfreq/3d00000.gpu/max_freq"
    };

    private Status status = Status.AVAILABLE;
    private String snapshotGovernor;
    private String snapshotMinFreq;
    private String snapshotMaxFreq;
    private double maxAllowedTempC = DEFAULT_MAX_TEMP_C;

    public static String findFirstWritablePath(String[] paths, PrivilegeBackend backend) {
        if (backend != null && backend.getTier() == PrivilegeBackend.Tier.ROOT) {
            for (String p : paths) {
                String test = backend.executeCommand("[ -w " + p + " ] && echo writable");
                if (test != null && test.contains("writable")) {
                    return p;
                }
            }
        }
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.canWrite()) {
                return p;
            }
        }
        return paths[0];
    }

    public static String findFirstReadablePath(String[] paths, PrivilegeBackend backend) {
        if (backend != null && backend.isAvailable()) {
            for (String p : paths) {
                String res = backend.executeCommand("cat " + p);
                if (res != null && !res.startsWith("ERROR") && !res.isEmpty()) {
                    return p;
                }
            }
        }
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.canRead()) {
                return p;
            }
        }
        return paths[0];
    }

    public void setMaxAllowedTempC(double tempC) {
        this.maxAllowedTempC = tempC;
    }

    public double getMaxAllowedTempC() {
        return maxAllowedTempC;
    }

    @Override
    public String id() {
        return "root_kgsl_control";
    }

    @Override
    public String name() {
        return "Root KGSL Hardware Tuning";
    }

    @Override
    public String description() {
        return "Hardware clock and governor control via KGSL sysfs (Requires Root)";
    }

    @Override
    public PrivilegeBackend.Tier requiredPrivilege() {
        return PrivilegeBackend.Tier.ROOT;
    }

    @Override
    public boolean isSupported(PrivilegeBackend backend) {
        if (backend == null || !backend.isAvailable()) return false;
        if (backend.getTier() != PrivilegeBackend.Tier.ROOT) return false;

        // Check for presence of kgsl sysfs nodes or root probe availability
        String govPath = findFirstReadablePath(GOVERNOR_PATHS, backend);
        String minFreqPath = findFirstReadablePath(MIN_FREQ_PATHS, backend);
        return govPath != null || minFreqPath != null || backend.getTier() == PrivilegeBackend.Tier.ROOT;
    }

    @Override
    public String snapshot(PrivilegeBackend backend) {
        String govPath = findFirstReadablePath(GOVERNOR_PATHS, backend);
        String minFreqPath = findFirstReadablePath(MIN_FREQ_PATHS, backend);
        String maxFreqPath = findFirstReadablePath(MAX_FREQ_PATHS, backend);

        snapshotGovernor = govPath != null ? backend.executeCommand("cat " + govPath) : null;
        snapshotMinFreq = minFreqPath != null ? backend.executeCommand("cat " + minFreqPath) : null;
        snapshotMaxFreq = maxFreqPath != null ? backend.executeCommand("cat " + maxFreqPath) : null;

        return String.format("gov=%s;min=%s;max=%s",
                snapshotGovernor != null ? snapshotGovernor : "",
                snapshotMinFreq != null ? snapshotMinFreq : "",
                snapshotMaxFreq != null ? snapshotMaxFreq : "");
    }

    @Override
    public boolean apply(PrivilegeBackend backend, String configStr) {
        // configStr format: "governor:minHz:maxHz"
        if (!isSupported(backend)) {
            status = Status.UNSUPPORTED;
            return false;
        }

        snapshot(backend);

        String[] parts = configStr.split(":");
        String targetGov = parts.length > 0 ? parts[0] : null;
        String minHz = parts.length > 1 ? parts[1] : null;
        String maxHz = parts.length > 2 ? parts[2] : null;

        List<Boolean> results = new ArrayList<>();

        if (targetGov != null && !targetGov.isEmpty()) {
            String govWritable = findFirstWritablePath(GOVERNOR_PATHS, backend);
            if (govWritable == null) govWritable = GOVERNOR_PATHS[0];
            String res = backend.executeCommand("echo " + targetGov + " > " + govWritable);
            results.add(!res.startsWith("ERROR"));
        }

        if (minHz != null && !minHz.isEmpty()) {
            String minWritable = findFirstWritablePath(MIN_FREQ_PATHS, backend);
            if (minWritable == null) minWritable = MIN_FREQ_PATHS[0];
            String res = backend.executeCommand("echo " + minHz + " > " + minWritable);
            results.add(!res.startsWith("ERROR"));
        }

        if (maxHz != null && !maxHz.isEmpty()) {
            String maxWritable = findFirstWritablePath(MAX_FREQ_PATHS, backend);
            if (maxWritable == null) maxWritable = MAX_FREQ_PATHS[0];
            String res = backend.executeCommand("echo " + maxHz + " > " + maxWritable);
            results.add(!res.startsWith("ERROR"));
        }

        boolean success = !results.contains(false) && !results.isEmpty();
        status = success ? Status.APPLIED : Status.ERROR;
        return success;
    }

    public boolean checkThermalSafeguard(double currentTempC, PrivilegeBackend backend) {
        if (currentTempC > maxAllowedTempC) {
            // Auto stop / rollback on thermal threshold breach
            rollback(backend);
            status = Status.ERROR;
            return false; // Temperature exceeded
        }
        return true;
    }

    @Override
    public boolean rollback(PrivilegeBackend backend) {
        if (!isSupported(backend)) return false;

        if (snapshotGovernor != null && !snapshotGovernor.isEmpty()) {
            String govPath = findFirstWritablePath(GOVERNOR_PATHS, backend);
            if (govPath == null) govPath = GOVERNOR_PATHS[0];
            backend.executeCommand("echo " + snapshotGovernor + " > " + govPath);
        }

        if (snapshotMinFreq != null && !snapshotMinFreq.isEmpty()) {
            String minPath = findFirstWritablePath(MIN_FREQ_PATHS, backend);
            if (minPath == null) minPath = MIN_FREQ_PATHS[0];
            backend.executeCommand("echo " + snapshotMinFreq + " > " + minPath);
        }

        if (snapshotMaxFreq != null && !snapshotMaxFreq.isEmpty()) {
            String maxPath = findFirstWritablePath(MAX_FREQ_PATHS, backend);
            if (maxPath == null) maxPath = MAX_FREQ_PATHS[0];
            backend.executeCommand("echo " + snapshotMaxFreq + " > " + maxPath);
        }

        status = Status.AVAILABLE;
        return true;
    }

    @Override
    public Status status() {
        return status;
    }
}
