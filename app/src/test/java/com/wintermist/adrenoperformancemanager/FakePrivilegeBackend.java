package com.wintermist.adrenoperformancemanager;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

import java.util.ArrayList;
import java.util.List;

public class FakePrivilegeBackend implements PrivilegeBackend {

    private final Tier tier;
    private final int uid;
    private boolean available = true;
    public final List<String> executedCommands = new ArrayList<>();

    public FakePrivilegeBackend(Tier tier, int uid) {
        this.tier = tier;
        this.uid = uid;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public Tier getTier() {
        return tier;
    }

    @Override
    public int getUid() {
        return uid;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String executeCommand(String command) {
        return executeCommandWithTimeout(command, 5000L);
    }

    @Override
    public String executeCommandWithTimeout(String command, long timeoutMs) {
        executedCommands.add(command);
        return "SUCCESS";
    }
}
