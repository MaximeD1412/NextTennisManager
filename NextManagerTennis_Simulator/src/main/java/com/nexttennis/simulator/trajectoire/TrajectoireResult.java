package com.nexttennis.simulator.trajectoire;

import com.nexttennis.simulator.proto.BallFlightSegment;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.IssueDuPoint;

import java.util.List;

/**
 * @param fault null when the ball lands in-bounds; FAUTE_NON_FORCÉE or FAUTE_FORCÉE otherwise
 */
public record TrajectoireResult(
        CourtPosition landingPosition,
        List<BallFlightSegment> segments,
        IssueDuPoint fault
) {}
