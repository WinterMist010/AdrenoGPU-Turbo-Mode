package com.wintermist.adrenoperformancemanager;

import com.wintermist.adrenoperformancemanager.feature.FeatureManager;
import com.wintermist.adrenoperformancemanager.feature.FeatureModule;
import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeatureModuleTest {

    private FeatureManager featureManager;
    private FakePrivilegeBackend shellBackend;

    private static class DummyFeature implements FeatureModule {
        private String currentConfig = "default";
        private Status status = Status.AVAILABLE;

        @Override
        public String id() {
            return "dummy_feature";
        }

        @Override
        public String name() {
            return "Dummy Feature";
        }

        @Override
        public String description() {
            return "A dummy feature for unit testing";
        }

        @Override
        public PrivilegeBackend.Tier requiredPrivilege() {
            return PrivilegeBackend.Tier.SHELL;
        }

        @Override
        public boolean isSupported(PrivilegeBackend backend) {
            return backend.getTier().getLevel() <= requiredPrivilege().getLevel();
        }

        @Override
        public String snapshot(PrivilegeBackend backend) {
            return currentConfig;
        }

        @Override
        public boolean apply(PrivilegeBackend backend, String config) {
            this.currentConfig = config;
            this.status = Status.APPLIED;
            return true;
        }

        @Override
        public boolean rollback(PrivilegeBackend backend) {
            this.currentConfig = "default";
            this.status = Status.AVAILABLE;
            return true;
        }

        @Override
        public Status status() {
            return status;
        }

        public String getCurrentConfig() {
            return currentConfig;
        }
    }

    @Before
    public void setUp() {
        featureManager = new FeatureManager();
        shellBackend = new FakePrivilegeBackend(PrivilegeBackend.Tier.SHELL, 2000);
    }

    @Test
    public void testFeatureLifecycleAndPersistence() {
        DummyFeature feature = new DummyFeature();
        featureManager.registerFeature(feature);

        assertEquals("dummy_feature", featureManager.getFeature("dummy_feature").id());

        // Apply new configuration
        boolean applied = featureManager.applyFeature("dummy_feature", shellBackend, "custom_config");
        assertTrue(applied);
        assertEquals("custom_config", feature.getCurrentConfig());
        assertEquals(FeatureModule.Status.APPLIED, feature.status());

        // Verify snapshot was taken and persisted
        assertEquals("default", featureManager.getPersistedSnapshot("dummy_feature"));

        // Rollback feature
        boolean rolledBack = featureManager.rollbackFeature("dummy_feature", shellBackend);
        assertTrue(rolledBack);
        assertEquals("default", feature.getCurrentConfig());
        assertEquals(FeatureModule.Status.AVAILABLE, feature.status());
        assertNull(featureManager.getPersistedSnapshot("dummy_feature"));
    }

    @Test
    public void testRollbackAll() {
        DummyFeature feature1 = new DummyFeature();
        featureManager.registerFeature(feature1);

        featureManager.applyFeature("dummy_feature", shellBackend, "modified_value");

        boolean allRolledBack = featureManager.rollbackAll(shellBackend);
        assertTrue(allRolledBack);
        assertEquals("default", feature1.getCurrentConfig());
        assertNull(featureManager.getPersistedSnapshot("dummy_feature"));
    }
}
