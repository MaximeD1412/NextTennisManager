package com.nexttennis.simulator.brain;

public record WeatherSnapshot(float windDirection, float windIntensity, float surfaceWetness) {

    public static WeatherSnapshot calm() {
        return new WeatherSnapshot(0f, 0f, 0f);
    }
}
