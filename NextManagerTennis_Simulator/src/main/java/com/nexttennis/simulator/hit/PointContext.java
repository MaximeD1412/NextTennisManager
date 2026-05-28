package com.nexttennis.simulator.hit;

/**
 * Score context for a single point — used by mental-attribute modifiers
 * (Clutch, Concentration, Combativité).
 */
public record PointContext(
        boolean isBreakPoint,
        boolean isTiebreak,
        boolean isMatchPoint,
        boolean isScoreUnfavorable,
        int matchPointsPlayed) {

    public static PointContext neutral() {
        return new PointContext(false, false, false, false, 0);
    }
}
