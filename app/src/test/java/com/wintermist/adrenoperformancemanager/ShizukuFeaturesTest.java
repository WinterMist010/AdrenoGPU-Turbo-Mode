package com.wintermist.adrenoperformancemanager;

import com.wintermist.adrenoperformancemanager.feature.AotCompileFeature;
import com.wintermist.adrenoperformancemanager.feature.FeatureModule;
import com.wintermist.adrenoperformancemanager.feature.RefreshRateFeature;
import com.wintermist.adrenoperformancemanager.feature.WindowBlurFeature;
import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShizukuFeaturesTest {

    private FakePrivilegeBackend shellBackend;
    private FakePrivilegeBackend unprivilegedBackend;

    @Before
    public void setUp() {
        shellBackend = new FakePrivilegeBackend(PrivilegeBackend.Tier.SHELL, 2000);
        unprivilegedBackend = new FakePrivilegeBackend(PrivilegeBackend.Tier.NONE, 10000);
    }

    @Test
    public void testRefreshRateFeatureSupportAndApply() {
        RefreshRateFeature feature = new RefreshRateFeature();

        assertTrue(feature.isSupported(shellBackend));
        assertFalse(feature.isSupported(unprivilegedBackend));

        boolean applied = feature.apply(shellBackend, "120");
        assertTrue(applied);
        assertEquals(FeatureModule.Status.APPLIED, feature.status());
        assertTrue(shellBackend.executedCommands.contains("settings put system peak_refresh_rate 120"));
        assertTrue(shellBackend.executedCommands.contains("settings put system min_refresh_rate 120"));

        boolean rolledBack = feature.rollback(shellBackend, "60:60");
        assertTrue(rolledBack);
        assertEquals(FeatureModule.Status.AVAILABLE, feature.status());
        assertTrue(shellBackend.executedCommands.contains("settings put system peak_refresh_rate 60"));
        assertTrue(shellBackend.executedCommands.contains("settings put system min_refresh_rate 60"));
    }

    @Test
    public void testWindowBlurFeatureSupportAndApply() {
        WindowBlurFeature feature = new WindowBlurFeature();

        assertTrue(feature.isSupported(shellBackend));
        assertFalse(feature.isSupported(unprivilegedBackend));

        boolean applied = feature.apply(shellBackend, "1");
        assertTrue(applied);
        assertEquals(FeatureModule.Status.APPLIED, feature.status());
        assertTrue(shellBackend.executedCommands.contains("settings put global disable_window_blurs 1"));

        boolean rolledBack = feature.rollback(shellBackend, "0");
        assertTrue(rolledBack);
        assertEquals(FeatureModule.Status.AVAILABLE, feature.status());
        assertTrue(shellBackend.executedCommands.contains("settings delete global disable_window_blurs"));
    }

    @Test
    public void testAotCompileFeature() {
        AotCompileFeature feature = new AotCompileFeature();

        assertTrue(feature.isSupported(shellBackend));
        assertFalse(feature.isSupported(unprivilegedBackend));

        // Invalid package should fail
        assertFalse(feature.apply(shellBackend, "invalid_pkg;rm -rf /"));

        // Success response parsing
        assertTrue(AotCompileFeature.isSuccessResponse("Success"));
        assertTrue(AotCompileFeature.isSuccessResponse("Performing speed-profile compilation for com.example.game\nSuccess"));
        assertFalse(AotCompileFeature.isSuccessResponse("Error: package not found"));
        assertFalse(AotCompileFeature.isSuccessResponse("Failure"));

        // Valid package compile
        assertTrue(feature.apply(shellBackend, "com.example.game"));
        assertEquals(FeatureModule.Status.APPLIED, feature.status());
        assertTrue(shellBackend.executedCommands.contains("cmd package compile -m speed-profile -f com.example.game"));

        // Rollback resets compilation state
        boolean rolledBack = feature.rollback(shellBackend, "com.example.game");
        assertTrue(rolledBack);
        assertEquals(FeatureModule.Status.AVAILABLE, feature.status());
        assertTrue(shellBackend.executedCommands.contains("cmd package compile --reset com.example.game"));
    }
}
