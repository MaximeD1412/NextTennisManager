package com.nexttennis.simulator.point;

import com.nexttennis.simulator.hit.PointContext;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.PointStart;
import com.nexttennis.simulator.proto.Surface;
import com.nexttennis.simulator.proto.SurfaceCoefficients;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import com.nexttennis.simulator.proto.WindVector;

public record PointInput(
        String matchId,
        TennisPlayerSnapshot server,
        TennisPlayerSnapshot receiver,
        Surface surface,
        SurfaceCoefficients surfaceCoefficients,
        WeatherState currentWeather,
        PointStart pointStart,
        CourtPosition serverPosition,
        CourtPosition receiverPosition,
        PointContext pointContext,
        int pointIndex,
        float serverFatigue,
        float receiverFatigue,
        WindVector ventMoyen,
        float surfaceDryingRate,
        boolean precipitationActive
) {}
