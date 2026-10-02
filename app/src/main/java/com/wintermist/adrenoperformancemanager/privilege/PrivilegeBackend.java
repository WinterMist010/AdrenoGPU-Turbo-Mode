package com.wintermist.adrenoperformancemanager.privilege;

public interface PrivilegeBackend {

    enum Tier {
        ROOT(1, "Tier 1: Root (kgsl sysfs & JNI control)"),
        SHELL(2, "Tier 2: Shell / Shizuku (Game mode, policy & load tools)"),
        NONE(3, "Tier 3: Unprivileged (Diagnostics & reporting only)");

        private final int level;
        private final String description;

        Tier(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }

    Tier getTier();
    int getUid();
    boolean isAvailable();
    String executeCommand(String command);
    String executeCommandWithTimeout(String command, long timeoutMs);
}
