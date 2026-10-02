package com.wintermist.adrenoperformancemanager.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ShellUtils {

    public static final int MAX_OUTPUT_BYTES = 64 * 1024; // 64 KB cap
    public static final long DEFAULT_TIMEOUT_MS = 5000L;

    private static final Pattern PACKAGE_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$");

    // Whitelisted command prefixes for Shell/Shizuku execution
    private static final Set<String> ALLOWED_COMMAND_PREFIXES = new HashSet<>(Arrays.asList(
            "device_config put game_overlay",
            "device_config get game_overlay",
            "device_config delete game_overlay",
            "cmd game mode",
            "cmd power set-fixed-performance-mode-enabled",
            "dumpsys thermalservice",
            "dumpsys gfxinfo",
            "dumpsys SurfaceFlinger",
            "dumpsys window",
            "dumpsys usage_stats",
            "cmd thermalservice override-status",
            "cmd thermalservice reset",
            "getprop",
            "setprop debug.hwui.renderer",
            "cat /sys/",
            "[ -w",
            "[ -f",
            "pm grant",
            "pm revoke"
    ));

    public static boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        return PACKAGE_NAME_PATTERN.matcher(packageName.trim()).matches();
    }

    public static boolean isCommandWhitelisted(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        String trimmed = command.trim();

        // Disallow command chaining / execution operators
        if (trimmed.contains(";") || trimmed.contains("&&") || trimmed.contains("||") ||
                trimmed.contains("`") || trimmed.contains("$(") || trimmed.contains("\n")) {
            return false;
        }

        // Check against allowed command prefixes
        for (String prefix : ALLOWED_COMMAND_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String buildGameOverlayCommand(String packageName, int mode, Integer fps, Double downscaleFactor, boolean useAngle) {
        if (!isValidPackageName(packageName)) {
            throw new IllegalArgumentException("Invalid package name: " + packageName);
        }

        StringBuilder config = new StringBuilder();
        config.append("mode=").append(mode);
        if (fps != null && fps > 0) {
            config.append(",fps=").append(fps);
        }
        if (downscaleFactor != null && downscaleFactor > 0.0 && downscaleFactor <= 1.0) {
            config.append(String.format(java.util.Locale.US, ",downscaleFactor=%.2f", downscaleFactor));
        }
        if (useAngle) {
            config.append(",useAngle=true");
        }

        return "device_config put game_overlay " + packageName + " " + config.toString();
    }

    public static String buildGameModeCommand(String packageName, int mode) {
        if (!isValidPackageName(packageName)) {
            throw new IllegalArgumentException("Invalid package name: " + packageName);
        }
        return "cmd game mode " + mode + " " + packageName;
    }

    public static String executeLocalCommand(String command, long timeoutMs) {
        if (!isCommandWhitelisted(command)) {
            return "ERROR: Command not in whitelist: " + command;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Process process = null;
        try {
            final Process proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            process = proc;

            Future<String> future = executor.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        char[] buffer = new char[1024];
                        int read;
                        int totalBytes = 0;
                        while ((read = reader.read(buffer)) != -1) {
                            if (totalBytes + read > MAX_OUTPUT_BYTES) {
                                int remaining = MAX_OUTPUT_BYTES - totalBytes;
                                if (remaining > 0) {
                                    sb.append(buffer, 0, remaining);
                                }
                                sb.append("\n[OUTPUT TRUNCATED]");
                                break;
                            }
                            sb.append(buffer, 0, read);
                            totalBytes += read;
                        }
                    }
                    proc.waitFor();
                    return sb.toString().trim();
                }
            });

            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (process != null) {
                process.destroy();
            }
            return "ERROR: Exec failed or timed out: " + e.getMessage();
        } finally {
            executor.shutdownNow();
        }
    }
}
