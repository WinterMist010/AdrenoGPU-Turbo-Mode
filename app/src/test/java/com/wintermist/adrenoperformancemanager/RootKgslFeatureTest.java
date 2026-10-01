package com.wintermist.adrenoperformancemanager;

import com.wintermist.adrenoperformancemanager.feature.FeatureModule;
import com.wintermist.adrenoperformancemanager.feature.FixedPerformanceModeFeature;
import com.wintermist.adrenoperformancemanager.feature.RootKgslFeature;
import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class RootKgslFeatureTest {

    private RootKgslFeature rootKgslFeature;
    private FixedPerformanceModeFeature fixedPerformanceFeature;
    private FakePrivilegeBackend rootBackend;
    private FakePrivilegeBackend shellBackend;

    @Before
    public void setUp() {
        rootKgslFeature = new RootKgslFeature();
        fixedPerformanceFeature = new FixedPerformanceModeFeature();
        rootBackend = new FakePrivilegeBackend(PrivilegeBackend.Tier.ROOT, 0);
        shellBackend = new FakePrivilegeBackend(PrivilegeBackend.Tier.SHELL, 2000);
    }

    @Test
    public void testRootKgslPrivilegeRequirement() {
        assertFalse(rootKgslFeature.isSupported(shellBackend));
        assertTrue(rootKgslFeature.isSupported(rootBackend));
    }

    @Test
    public void testThermalSafeguardTrigger() {
        rootKgslFeature.setMaxAllowedTempC(80.0);

        // Temp within limit -> safe
        boolean safe = rootKgslFeature.checkThermalSafeguard(75.5, rootBackend);
        assertTrue(safe);

        // Temp exceeds limit -> rollback triggered, returns false
        boolean unsafe = rootKgslFeature.checkThermalSafeguard(85.0, rootBackend);
        assertFalse(unsafe);
        assertEquals(FeatureModule.Status.ERROR, rootKgslFeature.status());
    }

    @Test
    public void testFixedPerformanceModeToggle() {
        assertTrue(fixedPerformanceFeature.isSupported(shellBackend));

        boolean applied = fixedPerformanceFeature.apply(shellBackend, "true");
        assertTrue(applied);
        assertEquals(FeatureModule.Status.APPLIED, fixedPerformanceFeature.status());
        assertTrue(shellBackend.executedCommands.contains("cmd power set-fixed-performance-mode-enabled true"));

        boolean rolledBack = fixedPerformanceFeature.rollback(shellBackend);
        assertTrue(rolledBack);
        assertEquals(FeatureModule.Status.AVAILABLE, fixedPerformanceFeature.status());
        assertTrue(shellBackend.executedCommands.contains("cmd power set-fixed-performance-mode-enabled false"));
    }
}
