package com.wintermist.adrenoperformancemanager.service;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;

import com.wintermist.adrenoperformancemanager.feature.RootKgslFeature;
import com.wintermist.adrenoperformancemanager.monitoring.GpuMonitor;
import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;

public class ForegroundMonitorService extends Service {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static PrivilegeBackend activeBackend;
    private static RootKgslFeature kgslFeature;

    public static void setThermalSafeguardParams(PrivilegeBackend backend, RootKgslFeature feature) {
        activeBackend = backend;
        kgslFeature = feature;
    }

    private final Runnable thermalRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeBackend != null && kgslFeature != null && activeBackend.isAvailable()) {
                double temp = GpuMonitor.readGpuTemperature(activeBackend);
                if (temp > 0) {
                    kgslFeature.checkThermalSafeguard(temp, activeBackend);
                }
            }
            handler.postDelayed(this, 3000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler.post(thermalRunnable);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(thermalRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static String detectForegroundPackage(Context context, PrivilegeBackend backend) {
        // Priority 1: UsageStatsManager if permission granted
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm != null) {
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 10000, now);
            if (events != null) {
                UsageEvents.Event event = new UsageEvents.Event();
                String lastPkg = null;
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastPkg = event.getPackageName();
                    }
                }
                if (lastPkg != null && ShellUtils.isValidPackageName(lastPkg)) {
                    return lastPkg;
                }
            }
        }

        // Priority 2: dumpsys via PrivilegeBackend (Shizuku or Root)
        if (backend != null && backend.isAvailable()) {
            String out = backend.executeCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'");
            if (out != null && !out.isEmpty()) {
                for (String line : out.split("\n")) {
                    if (line.contains("/")) {
                        String[] parts = line.split("/");
                        for (String token : parts[0].split("\\s+")) {
                            if (ShellUtils.isValidPackageName(token)) {
                                return token;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    public static boolean grantWriteSecureSettings(PrivilegeBackend backend, String packageName) {
        if (backend == null || !backend.isAvailable()) return false;
        if (!ShellUtils.isValidPackageName(packageName)) return false;

        String cmd = "pm grant " + packageName + " android.permission.WRITE_SECURE_SETTINGS";
        String res = backend.executeCommand(cmd);
        return !res.startsWith("ERROR");
    }
}
