package com.wintermist.adrenoperformancemanager.service;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;

public class ForegroundMonitorService extends Service {

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
