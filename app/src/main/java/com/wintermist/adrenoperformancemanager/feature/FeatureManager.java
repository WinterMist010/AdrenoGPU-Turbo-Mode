package com.wintermist.adrenoperformancemanager.feature;

import android.content.Context;
import android.content.SharedPreferences;
import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeatureManager {

    private static final String PREFS_NAME = "apm_feature_snapshots";
    private final Map<String, FeatureModule> modules = new LinkedHashMap<>();
    private final Map<String, String> inMemorySnapshots = new HashMap<>();
    private final SharedPreferences preferences;

    public FeatureManager(Context context) {
        if (context != null) {
            this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        } else {
            this.preferences = null;
        }
    }

    // Constructor for unit tests or memory-only persistence
    public FeatureManager() {
        this.preferences = null;
    }

    public void registerFeature(FeatureModule module) {
        if (module != null) {
            modules.put(module.id(), module);
        }
    }

    public FeatureModule getFeature(String id) {
        return modules.get(id);
    }

    public List<FeatureModule> getAllFeatures() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    public void persistSnapshot(String featureId, String snapshot) {
        inMemorySnapshots.put(featureId, snapshot);
        if (preferences != null) {
            if (snapshot == null) {
                preferences.edit().remove(featureId).apply();
            } else {
                preferences.edit().putString(featureId, snapshot).apply();
            }
        }
    }

    public String getPersistedSnapshot(String featureId) {
        if (preferences != null && preferences.contains(featureId)) {
            return preferences.getString(featureId, null);
        }
        return inMemorySnapshots.get(featureId);
    }

    public void clearPersistedSnapshot(String featureId) {
        inMemorySnapshots.remove(featureId);
        if (preferences != null) {
            preferences.edit().remove(featureId).apply();
        }
    }

    public boolean applyFeature(String id, PrivilegeBackend backend, String config) {
        FeatureModule feature = modules.get(id);
        if (feature == null) return false;

        if (!feature.isSupported(backend)) {
            return false;
        }

        // Take snapshot before applying if not already persisted
        if (getPersistedSnapshot(id) == null) {
            String snap = feature.snapshot(backend);
            if (snap != null) {
                persistSnapshot(id, snap);
            }
        }

        return feature.apply(backend, config);
    }

    public boolean rollbackFeature(String id, PrivilegeBackend backend) {
        FeatureModule feature = modules.get(id);
        if (feature == null) return false;

        boolean success = feature.rollback(backend);
        if (success) {
            clearPersistedSnapshot(id);
        }
        return success;
    }

    public boolean rollbackAll(PrivilegeBackend backend) {
        boolean allSuccess = true;
        for (FeatureModule feature : modules.values()) {
            if (getPersistedSnapshot(feature.id()) != null || feature.status() == FeatureModule.Status.APPLIED) {
                boolean success = feature.rollback(backend);
                if (success) {
                    clearPersistedSnapshot(feature.id());
                } else {
                    allSuccess = false;
                }
            }
        }
        return allSuccess;
    }
}
