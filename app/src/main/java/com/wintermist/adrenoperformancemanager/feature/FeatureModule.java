package com.wintermist.adrenoperformancemanager.feature;

import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

public interface FeatureModule {

    enum Status {
        UNSUPPORTED("Unsupported on device or privilege level"),
        AVAILABLE("Available to configure"),
        APPLIED("Applied and active"),
        ERROR("Error applying feature");

        private final String description;

        Status(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    String id();
    String name();
    String description();
    PrivilegeBackend.Tier requiredPrivilege();
    boolean isSupported(PrivilegeBackend backend);
    String snapshot(PrivilegeBackend backend);
    boolean apply(PrivilegeBackend backend, String config);
    boolean rollback(PrivilegeBackend backend, String snapshot);
    Status status();
}
