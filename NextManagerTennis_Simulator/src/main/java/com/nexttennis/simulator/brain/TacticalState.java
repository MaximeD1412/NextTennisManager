package com.nexttennis.simulator.brain;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;

public record TacticalState(
        CourtPosition movementTarget,
        ShotType preparedShot,
        ShotIntent shotIntent,
        CourtPosition approximateTargetZone,
        float riskLevel,
        boolean approachingNet
) {}
