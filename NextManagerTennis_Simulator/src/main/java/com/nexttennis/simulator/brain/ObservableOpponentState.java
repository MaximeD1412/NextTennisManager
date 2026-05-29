package com.nexttennis.simulator.brain;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.Vector3;

public record ObservableOpponentState(
        CourtPosition position,
        Vector3 velocityVector,
        ShotPreparation visiblePreparation
) {}
