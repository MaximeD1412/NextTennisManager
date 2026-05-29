package com.nexttennis.simulator.physics;

import com.nexttennis.simulator.movement.MovementResult;
import com.nexttennis.simulator.movement.PlayerMovementService;
import com.nexttennis.simulator.proto.BallFlightSegment;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.IssueDuPoint;
import com.nexttennis.simulator.proto.PhysicsSimulationResult;
import com.nexttennis.simulator.proto.ReachResult;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.springframework.stereotype.Component;

/**
 * Interprets a PhysicsSimulationResult at the Java side of the physics boundary:
 *  - detects faults (net, out of bounds)
 *  - delegates defender movement computation to PlayerMovementService
 *  - classifies IssueDuPoint when the rally ends on this shot
 */
@Component
public class PhysicsResultInterpreter {

    static final float COURT_HALF_WIDTH = 4.115f;
    static final float BASELINE_Y       = 11.89f;
    static final float FAULT_HQ_THRESHOLD = 0.40f;

    private final PlayerMovementService playerMovementService;

    public PhysicsResultInterpreter(PlayerMovementService playerMovementService) {
        this.playerMovementService = playerMovementService;
    }

    public record Interpretation(
            ReachResult reachResult,
            IssueDuPoint issueDuPoint // null → rally continues
    ) {}

    /**
     * Interprets the physics result for the shot receiver.
     *
     * @param result           Rust physics output for the shot
     * @param defender         snapshot of the player who must return the ball
     * @param defenderPosition defender's current CourtPosition
     * @param effectiveFatigue combined fatigue coefficient (0–1) for the defender
     * @param attackerHitQuality HQ of the shot (used to classify fault type)
     */
    public Interpretation interpret(
            PhysicsSimulationResult result,
            TennisPlayerSnapshot defender,
            CourtPosition defenderPosition,
            float effectiveFatigue,
            float attackerHitQuality) {

        // 1. Net fault
        if (!result.getClearedNet()) {
            return new Interpretation(
                    ReachResult.REACH_RESULT_UNSPECIFIED,
                    classifyFault(attackerHitQuality));
        }

        // 2. Out of bounds
        CourtPosition landing = result.getLandingPosition();
        if (landing != null && isOutOfBounds(landing)) {
            return new Interpretation(
                    ReachResult.REACH_RESULT_UNSPECIFIED,
                    classifyFault(attackerHitQuality));
        }

        // 3. No usable landing data (should not happen with a valid sidecar response)
        if (result.getBallFlightSegmentsCount() == 0) {
            return new Interpretation(ReachResult.REACH_RESULT_MISSED,
                    IssueDuPoint.ISSUE_DU_POINT_COUP_GAGNANT);
        }

        // 4. Compute defender reach using the pre-bounce flight segment
        BallFlightSegment primarySegment = result.getBallFlightSegments(0);
        MovementResult movement = playerMovementService.computeMovement(
                defender, defenderPosition, primarySegment, effectiveFatigue);

        if (movement.reachResult() == ReachResult.REACH_RESULT_MISSED) {
            return new Interpretation(
                    ReachResult.REACH_RESULT_MISSED,
                    IssueDuPoint.ISSUE_DU_POINT_COUP_GAGNANT);
        }

        // Rally continues
        return new Interpretation(movement.reachResult(), null);
    }

    IssueDuPoint classifyFault(float hitQuality) {
        return hitQuality >= FAULT_HQ_THRESHOLD
                ? IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE
                : IssueDuPoint.ISSUE_DU_POINT_FAUTE_FORCEE;
    }

    private boolean isOutOfBounds(CourtPosition pos) {
        return Math.abs(pos.getX()) > COURT_HALF_WIDTH
                || Math.abs(pos.getY()) > BASELINE_Y;
    }
}
