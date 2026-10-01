package com.wintermist.adrenoperformancemanager.privilege;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import com.wintermist.adrenoperformancemanager.IUserService;
import com.wintermist.adrenoperformancemanager.shizuku.ShizukuUserService;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;
import rikka.shizuku.Shizuku;

public class ShizukuBackend implements PrivilegeBackend {

    public interface OnConnectionListener {
        void onServiceConnected();
        void onServiceDisconnected();
    }

    private final Context context;
    private IUserService userService;
    private boolean isBound = false;
    private OnConnectionListener connectionListener;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            userService = IUserService.Stub.asInterface(service);
            isBound = true;
            if (connectionListener != null) {
                connectionListener.onServiceConnected();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            userService = null;
            isBound = false;
            if (connectionListener != null) {
                connectionListener.onServiceDisconnected();
            }
        }
    };

    private final Shizuku.UserServiceArgs userServiceArgs;

    public ShizukuBackend(Context context) {
        this.context = context.getApplicationContext();
        this.userServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(this.context, ShizukuUserService.class)
        ).daemon(false).processNameSuffix("service").debuggable(false).version(1);
    }

    public void setOnConnectionListener(OnConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void bindService() {
        if (!isAvailable()) return;
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection);
        } catch (Exception ignored) {
        }
    }

    public void unbindService() {
        if (isBound) {
            try {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true);
            } catch (Exception ignored) {
            }
            isBound = false;
            userService = null;
        }
    }

    @Override
    public Tier getTier() {
        return Tier.SHELL;
    }

    @Override
    public int getUid() {
        if (userService != null) {
            try {
                return userService.getUid();
            } catch (Exception ignored) {
            }
        }
        return 2000; // AID_SHELL standard UID
    }

    @Override
    public boolean isAvailable() {
        try {
            return Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String executeCommand(String command) {
        return executeCommandWithTimeout(command, ShellUtils.DEFAULT_TIMEOUT_MS);
    }

    @Override
    public String executeCommandWithTimeout(String command, long timeoutMs) {
        if (!isAvailable()) {
            return "ERROR: Shizuku binder not running or permission denied";
        }

        if (userService != null) {
            try {
                return userService.execCommandWithTimeout(command, timeoutMs);
            } catch (Exception e) {
                return "ERROR: Shizuku service command failed: " + e.getMessage();
            }
        }

        return "ERROR: Shizuku service not bound or disconnected";
    }
}
