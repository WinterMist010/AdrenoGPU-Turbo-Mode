package com.wintermist.adrenoperformancemanager.privilege;

import android.os.Process;

public class NoneBackend implements PrivilegeBackend {

    @Override
    public Tier getTier() {
        return Tier.NONE;
    }

    @Override
    public int getUid() {
        try {
            return Process.myUid();
        } catch (Throwable ignored) {
            return 10000; // App UID fallback in unit test environment
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String executeCommand(String command) {
        return "ERROR: Unprivileged backend cannot execute commands";
    }

    @Override
    public String executeCommandWithTimeout(String command, long timeoutMs) {
        return executeCommand(command);
    }
}
