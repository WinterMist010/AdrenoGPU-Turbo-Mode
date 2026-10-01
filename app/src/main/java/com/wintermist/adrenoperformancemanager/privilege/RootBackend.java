package com.wintermist.adrenoperformancemanager.privilege;

import com.topjohnwu.superuser.Shell;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;
import java.util.List;

public class RootBackend implements PrivilegeBackend {

    @Override
    public Tier getTier() {
        return Tier.ROOT;
    }

    @Override
    public int getUid() {
        return 0; // Root real UID
    }

    @Override
    public boolean isAvailable() {
        try {
            return Shell.isAppGrantedRoot() == Boolean.TRUE;
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
            return "ERROR: Root access not granted or available";
        }
        try {
            Shell.Result result = Shell.cmd(command).exec();
            List<String> out = result.getOut();
            if (out == null || out.isEmpty()) {
                List<String> err = result.getErr();
                if (err != null && !err.isEmpty()) {
                    return "ERROR: " + String.join("\n", err);
                }
                return "";
            }
            return String.join("\n", out);
        } catch (Exception e) {
            return "ERROR: Root command execution failed: " + e.getMessage();
        }
    }
}
