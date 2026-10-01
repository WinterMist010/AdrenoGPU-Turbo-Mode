package com.wintermist.adrenoperformancemanager.feature;

public class GameModeConfig {
    public final String packageName;
    public final int mode; // 2 = Performance, 3 = Battery
    public final Integer targetFps;
    public final Double downscaleFactor;
    public final boolean useAngle;

    public GameModeConfig(String packageName, int mode, Integer targetFps, Double downscaleFactor, boolean useAngle) {
        this.packageName = packageName;
        this.mode = mode;
        this.targetFps = targetFps;
        this.downscaleFactor = downscaleFactor;
        this.useAngle = useAngle; // Default false
    }

    public static GameModeConfig createDefault(String packageName) {
        return new GameModeConfig(packageName, 2, 60, 1.0, false);
    }
}
