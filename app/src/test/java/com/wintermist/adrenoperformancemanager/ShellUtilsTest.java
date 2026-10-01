package com.wintermist.adrenoperformancemanager;

import com.wintermist.adrenoperformancemanager.utils.ShellUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShellUtilsTest {

    @Test
    public void testIsValidPackageName_Valid() {
        assertTrue(ShellUtils.isValidPackageName("com.epicgames.fortnite"));
        assertTrue(ShellUtils.isValidPackageName("com.miHoYo.GenshinImpact"));
        assertTrue(ShellUtils.isValidPackageName("a.b.c"));
    }

    @Test
    public void testIsValidPackageName_Invalid() {
        assertFalse(ShellUtils.isValidPackageName("invalid;rm -rf /"));
        assertFalse(ShellUtils.isValidPackageName("package name with spaces"));
        assertFalse(ShellUtils.isValidPackageName(""));
        assertFalse(ShellUtils.isValidPackageName(null));
        assertFalse(ShellUtils.isValidPackageName("com.example;echo 123"));
    }

    @Test
    public void testIsCommandWhitelisted() {
        assertTrue(ShellUtils.isCommandWhitelisted("device_config put game_overlay com.example mode=2,fps=60"));
        assertTrue(ShellUtils.isCommandWhitelisted("device_config delete game_overlay com.example"));
        assertTrue(ShellUtils.isCommandWhitelisted("cmd game mode 2 com.example"));
        assertTrue(ShellUtils.isCommandWhitelisted("dumpsys thermalservice"));
        assertTrue(ShellUtils.isCommandWhitelisted("cmd thermalservice reset"));
        assertTrue(ShellUtils.isCommandWhitelisted("dumpsys gfxinfo com.example framestats"));

        assertFalse(ShellUtils.isCommandWhitelisted("rm -rf /"));
        assertFalse(ShellUtils.isCommandWhitelisted("reboot"));
        assertFalse(ShellUtils.isCommandWhitelisted("su -c reboot"));
        assertFalse(ShellUtils.isCommandWhitelisted(null));
    }

    @Test
    public void testBuildGameOverlayCommand() {
        String cmd = ShellUtils.buildGameOverlayCommand("com.example.game", 2, 60, 0.8, false);
        assertEquals("device_config put game_overlay com.example.game mode=2,fps=60,downscaleFactor=0.80", cmd);

        String cmdWithAngle = ShellUtils.buildGameOverlayCommand("com.example.game", 2, 120, null, true);
        assertEquals("device_config put game_overlay com.example.game mode=2,fps=120,useAngle=true", cmdWithAngle);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildGameOverlayCommand_InvalidPackageThrows() {
        ShellUtils.buildGameOverlayCommand("bad package;name", 2, 60, 1.0, false);
    }

    @Test
    public void testBuildGameModeCommand() {
        String cmd = ShellUtils.buildGameModeCommand("com.example.game", 2);
        assertEquals("cmd game mode 2 com.example.game", cmd);
    }
}
