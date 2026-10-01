package com.wintermist.adrenoperformancemanager;

import com.wintermist.adrenoperformancemanager.feature.FeatureModule;
import com.wintermist.adrenoperformancemanager.feature.GameModeFeature;
import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameModeFeatureTest {

    private GameModeFeature gameModeFeature;
    private FakePrivilegeBackend shellBackend;

    @Before
    public void setUp() {
        gameModeFeature = new GameModeFeature();
        shellBackend = new FakePrivilegeBackend(PrivilegeBackend.Tier.SHELL, 2000);
    }

    @Test
    public void testIsSupported() {
        assertTrue(gameModeFeature.isSupported(shellBackend));

        FakePrivilegeBackend rootBackend = new FakePrivilegeBackend(PrivilegeBackend.Tier.ROOT, 0);
        assertTrue(gameModeFeature.isSupported(rootBackend));

        FakePrivilegeBackend noneBackend = new FakePrivilegeBackend(PrivilegeBackend.Tier.NONE, 10000);
        assertFalse(gameModeFeature.isSupported(noneBackend));
    }

    @Test
    public void testApply_ValidConfig() {
        String config = "com.epicgames.fortnite:2:60:0.8:false";
        boolean applied = gameModeFeature.apply(shellBackend, config);

        assertTrue(applied);
        assertEquals(FeatureModule.Status.APPLIED, gameModeFeature.status());

        assertEquals(2, shellBackend.executedCommands.size());
        assertTrue(shellBackend.executedCommands.get(0).contains("device_config put game_overlay com.epicgames.fortnite mode=2,fps=60,downscaleFactor=0.80"));
        assertFalse(shellBackend.executedCommands.get(0).contains("useAngle"));
        assertTrue(shellBackend.executedCommands.get(1).contains("cmd game mode 2 com.epicgames.fortnite"));
    }

    @Test
    public void testApply_DefaultUseAngleFalse() {
        String config = "com.miHoYo.GenshinImpact:2:120:0.9";
        boolean applied = gameModeFeature.apply(shellBackend, config);

        assertTrue(applied);
        assertFalse(shellBackend.executedCommands.get(0).contains("useAngle"));
    }

    @Test
    public void testApply_InvalidPackageRejected() {
        String config = "invalid;package;name:2:60:1.0:false";
        boolean applied = gameModeFeature.apply(shellBackend, config);

        assertFalse(applied);
        assertEquals(FeatureModule.Status.ERROR, gameModeFeature.status());
    }

    @Test
    public void testRollback() {
        gameModeFeature.apply(shellBackend, "com.example.game:2:60:1.0:false");
        shellBackend.executedCommands.clear();

        boolean rolledBack = gameModeFeature.rollback(shellBackend, "com.example.game:mode=2");
        assertTrue(rolledBack);
        assertEquals(FeatureModule.Status.AVAILABLE, gameModeFeature.status());

        assertTrue(shellBackend.executedCommands.get(0).contains("device_config delete game_overlay com.example.game"));
    }
}
