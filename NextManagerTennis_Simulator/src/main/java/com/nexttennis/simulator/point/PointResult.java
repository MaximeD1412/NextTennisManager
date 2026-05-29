package com.nexttennis.simulator.point;

import com.nexttennis.simulator.proto.PointEnd;
import com.nexttennis.simulator.proto.PointStart;

public record PointResult(
        PointStart pointStart,
        PointEnd pointEnd,
        WeatherState weatherAfter
) {}
