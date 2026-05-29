package com.nexttennis.simulator.brain;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.ScoreSnapshot;

public record GameTickState(
        CourtPosition ballPosition,
        CourtPosition selfPosition,
        CourtPosition opponentPosition,
        ScoreSnapshot score,
        WeatherSnapshot weather,
        int tickIndex
) {}
