package com.wintermist.adrenoperformancemanager.monitoring;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GpuMonitor {

    private static final String[] GPU_TEMP_SYSFS = {
            "/sys/class/kgsl/kgsl-3d0/gputemperature",
            "/sys/class/kgsl/kgsl-3d0/temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp"
    };

    private static final String[] GPU_FREQ_SYSFS = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/clock_mhz",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/devfreq/kgsl-3d0/cur_freq",
            "/sys/class/devfreq/3d00000.gpu/cur_freq"
    };

    private static final String[] GPU_BUSY_SYSFS = {
            "/sys/class/kgsl/kgsl-3d0/gpubusy"
    };

    public static class MetricsSnapshot {
        public final double temperatureC;
        public final long frequencyMhz;
        public final double busyPercent;
        public final double p95FrameTimeMs;
        public final long timestampMs;

        public MetricsSnapshot(double temperatureC, long frequencyMhz, double busyPercent, double p95FrameTimeMs) {
            this.temperatureC = temperatureC;
            this.frequencyMhz = frequencyMhz;
            this.busyPercent = busyPercent;
            this.p95FrameTimeMs = p95FrameTimeMs;
            this.timestampMs = System.currentTimeMillis();
        }
    }

    public static double readGpuTemperature(PrivilegeBackend backend) {
        // Priority 1: Sysfs read
        for (String path : GPU_TEMP_SYSFS) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line = reader.readLine();
                    if (line != null) {
                        double raw = Double.parseDouble(line.trim().split("\\s+")[0]);
                        return raw > 1000 ? raw / 1000.0 : raw;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Priority 2: Backend cat command
        if (backend != null && backend.isAvailable()) {
            for (String path : GPU_TEMP_SYSFS) {
                String out = backend.executeCommand("cat " + path);
                if (out != null && !out.startsWith("ERROR") && !out.trim().isEmpty()) {
                    try {
                        double raw = Double.parseDouble(out.trim().split("\\s+")[0]);
                        return raw > 1000 ? raw / 1000.0 : raw;
                    } catch (Exception ignored) {
                    }
                }
            }

            // Priority 3: dumpsys thermalservice
            String out = backend.executeCommand("dumpsys thermalservice");
            if (out != null && !out.isEmpty()) {
                Pattern p = Pattern.compile("(?:gpu|GPU|temperature)[^\\d]*([0-9]+(?:\\.[0-9]+)?)");
                Matcher m = p.matcher(out);
                if (m.find()) {
                    try {
                        double val = Double.parseDouble(m.group(1));
                        return val > 1000 ? val / 1000.0 : val;
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return -1.0;
    }

    public static long readGpuFrequency(PrivilegeBackend backend) {
        for (String path : GPU_FREQ_SYSFS) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line = reader.readLine();
                    if (line != null) {
                        long raw = Long.parseLong(line.trim().split("\\s+")[0]);
                        if (raw >= 100_000_000L) return raw / 1_000_000L;
                        if (raw >= 100_000L) return raw / 1_000L;
                        return raw;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (backend != null && backend.isAvailable()) {
            for (String path : GPU_FREQ_SYSFS) {
                String out = backend.executeCommand("cat " + path);
                if (out != null && !out.startsWith("ERROR") && !out.trim().isEmpty()) {
                    try {
                        long raw = Long.parseLong(out.trim().split("\\s+")[0]);
                        if (raw >= 100_000_000L) return raw / 1_000_000L;
                        if (raw >= 100_000L) return raw / 1_000L;
                        return raw;
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return -1;
    }

    public static double readGpuBusy(PrivilegeBackend backend) {
        for (String path : GPU_BUSY_SYSFS) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line = reader.readLine();
                    if (line != null) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            long busy = Long.parseLong(parts[0]);
                            long total = Long.parseLong(parts[1]);
                            if (total > 0) {
                                return (busy * 100.0) / total;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (backend != null && backend.isAvailable()) {
            for (String path : GPU_BUSY_SYSFS) {
                String out = backend.executeCommand("cat " + path);
                if (out != null && !out.startsWith("ERROR") && !out.trim().isEmpty()) {
                    try {
                        String[] parts = out.trim().split("\\s+");
                        if (parts.length >= 2) {
                            long busy = Long.parseLong(parts[0]);
                            long total = Long.parseLong(parts[1]);
                            if (total > 0) {
                                return (busy * 100.0) / total;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return -1.0;
    }

    public static List<Float> parseGfxInfoFramestats(String dumpsysGfxInfoOutput) {
        List<Float> frameTimesMs = new ArrayList<>();
        if (dumpsysGfxInfoOutput == null || dumpsysGfxInfoOutput.isEmpty()) {
            return frameTimesMs;
        }

        boolean inProfileData = false;
        String[] lines = dumpsysGfxInfoOutput.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("---PROFileData---") || trimmed.startsWith("FLAGS,INTENDED_VSYNC")) {
                inProfileData = true;
                continue;
            }
            if (inProfileData) {
                if (trimmed.isEmpty() || trimmed.startsWith("---")) {
                    break;
                }
                String[] tokens = trimmed.split(",");
                if (tokens.length >= 13) {
                    try {
                        long intendedVsync = Long.parseLong(tokens[1]);
                        long frameCompleted = Long.parseLong(tokens[12]);
                        if (frameCompleted > intendedVsync && intendedVsync > 0) {
                            float durationMs = (frameCompleted - intendedVsync) / 1_000_000.0f;
                            frameTimesMs.add(durationMs);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return frameTimesMs;
    }

    public static double calculateP95FrameTime(List<Float> frameTimesMs) {
        if (frameTimesMs == null || frameTimesMs.isEmpty()) {
            return -1.0;
        }

        List<Float> sorted = new ArrayList<>(frameTimesMs);
        Collections.sort(sorted);
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        if (index < 0) index = 0;
        if (index >= sorted.size()) index = sorted.size() - 1;

        return sorted.get(index);
    }

    public static MetricsSnapshot captureSnapshot(PrivilegeBackend backend, String currentPackage) {
        double temp = readGpuTemperature(backend);
        long freq = readGpuFrequency(backend);
        double busy = readGpuBusy(backend);

        double p95 = -1.0;
        if (backend != null && backend.isAvailable() && currentPackage != null && !currentPackage.isEmpty()) {
            String gfxOut = backend.executeCommand("dumpsys gfxinfo " + currentPackage + " framestats");
            List<Float> frameTimes = parseGfxInfoFramestats(gfxOut);
            p95 = calculateP95FrameTime(frameTimes);
        }

        return new MetricsSnapshot(temp, freq, busy, p95);
    }
}
