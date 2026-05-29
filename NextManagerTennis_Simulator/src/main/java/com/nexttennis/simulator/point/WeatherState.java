package com.nexttennis.simulator.point;

import com.nexttennis.simulator.proto.WindVector;

public record WeatherState(WindVector wind, float surfaceWetness) {

    public static WeatherState zero() {
        return new WeatherState(
                WindVector.newBuilder().setDirection(0).setIntensity(0).build(),
                0.0f);
    }
}
