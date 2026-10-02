package com.wintermist.adrenoperformancemanager.capability;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class CapabilityReporter {

    public static class SysfsProbe {
        public final String path;
        public final boolean exists;
        public final boolean readable;
        public final boolean writable;

        public SysfsProbe(String path) {
            this.path = path;
            File f = new File(path);
            this.exists = f.exists();
            this.readable = f.canRead();
            this.writable = f.canWrite();
        }
    }

    private static final String[] KGSL_PATHS_TO_PROBE = {
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_governors",
            "/sys/class/kgsl/kgsl-3d0/pwrlevel",
            "/sys/class/kgsl/kgsl-3d0/gputemperature",
            "/dev/kgsl-3d0"
    };

    public Map<String, SysfsProbe> probeSysfsNodes() {
        Map<String, SysfsProbe> map = new LinkedHashMap<>();
        for (String path : KGSL_PATHS_TO_PROBE) {
            map.put(path, new SysfsProbe(path));
        }
        return map;
    }

    public String generateReport(PrivilegeBackend activeBackend, String gpuModel) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ADRENO PERFORMANCE MANAGER CAPABILITY REPORT ===\n");
        sb.append("GPU Model: ").append(gpuModel != null ? gpuModel : "Unknown").append("\n");
        sb.append("Active Tier: ").append(activeBackend.getTier().getDescription()).append("\n");
        sb.append("Real UID: ").append(activeBackend.getUid()).append("\n");
        sb.append("Backend Available: ").append(activeBackend.isAvailable()).append("\n\n");

        sb.append("--- SYSFS PROBE RESULTS ---\n");
        Map<String, SysfsProbe> probes = probeSysfsNodes();
        for (Map.Entry<String, SysfsProbe> entry : probes.entrySet()) {
            SysfsProbe p = entry.getValue();
            sb.append(p.path).append(" -> ")
                    .append("exists: ").append(p.exists)
                    .append(", read: ").append(p.readable)
                    .append(", write: ").append(p.writable)
                    .append("\n");
        }

        sb.append("\n--- NOTES ---\n");
        if (activeBackend.getTier() == PrivilegeBackend.Tier.SHELL) {
            sb.append("Shell privilege cannot write kgsl sysfs, change governors, or alter vendor thermal mitigation.\n");
        }

        return sb.toString();
    }
}
