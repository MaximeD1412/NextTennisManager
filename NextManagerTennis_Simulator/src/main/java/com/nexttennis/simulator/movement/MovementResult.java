package com.nexttennis.simulator.movement;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.PlayerMove;
import com.nexttennis.simulator.proto.ReachResult;

public record MovementResult(
        ReachResult reachResult,
        PlayerMove event,
        CourtPosition replacementPosition
) {}
