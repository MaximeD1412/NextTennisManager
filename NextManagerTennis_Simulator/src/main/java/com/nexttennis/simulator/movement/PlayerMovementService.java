package com.nexttennis.simulator.movement;

import com.nexttennis.simulator.proto.BallFlightSegment;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.PlayerMove;
import com.nexttennis.simulator.proto.ReachResult;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.springframework.stereotype.Service;

@Service
public class PlayerMovementService {

    // Base lateral/forward speed (m/s) at attribute 50
    private static final float BASE_SPEED = 6.0f;

    // Agilité reduces movement time by up to 25% at attribute 99
    private static final float MAX_AGILITY_REDUCTION = 0.25f;

    // Effective fatigue (0–1) increases RequiredTime by up to 30%
    private static final float MAX_FATIGUE_PENALTY = 0.30f;

    // Jeu de jambes adds a stabilisation overhead before the shot (max 0.10s at attribute 1)
    private static final float MAX_STAB_TIME = 0.10f;

    // Margin thresholds (seconds) that determine ReachResult
    private static final float THRESHOLD_COMFORTABLE = 0.3f;
    private static final float THRESHOLD_LATE = 0.0f;
    private static final float THRESHOLD_STRETCHED = -0.3f;
    private static final float THRESHOLD_DESPERATE = -0.6f;

    // Distance from the net to the baseline (metres)
    private static final float BASELINE_Y = 11.89f;

    /**
     * Computes how a player reaches the incoming ball and returns the result.
     *
     * @param player           snapshot of the player (attributes 1–99)
     * @param currentPosition  player's CourtPosition before this movement
     * @param flightSegment    the incoming BALL_FLIGHT_SEGMENT event
     * @param effectiveFatigue combined intra/inter-match fatigue coefficient (0.0–1.0)
     * @return MovementResult containing ReachResult, PLAYER_MOVE event, and post-shot base position
     */
    public MovementResult computeMovement(
            TennisPlayerSnapshot player,
            CourtPosition currentPosition,
            BallFlightSegment flightSegment,
            float effectiveFatigue) {

        CourtPosition landing = flightSegment.getTo();
        float availableTime = flightSegment.getDurationMs() / 1000.0f;
        float requiredTime = computeRequiredTime(player, currentPosition, landing, effectiveFatigue);

        ReachResult reachResult = classifyReachResult(availableTime - requiredTime);
        CourtPosition replacementPosition = computeBasePosition(landing);

        PlayerMove event = PlayerMove.newBuilder()
                .setPlayerId(player.getPlayerId())
                .setFrom(currentPosition)
                .setTo(landing)
                .setAvailableTime(availableTime)
                .setRequiredTime(requiredTime)
                .setSlipped(false) // weather effects added in issue #18
                .build();

        return new MovementResult(reachResult, event, replacementPosition);
    }

    /**
     * Computes RequiredTime using anisotropic movement (different lateral/forward speeds)
     * and modulates it with Agilité, Jeu de jambes, and fatigue.
     * Package-private for direct testing.
     */
    float computeRequiredTime(
            TennisPlayerSnapshot player,
            CourtPosition from,
            CourtPosition to,
            float effectiveFatigue) {

        float dx = Math.abs(to.getX() - from.getX());
        float dy = Math.abs(to.getY() - from.getY());

        float speedX = BASE_SPEED * player.getVitesseLaterale() / 50.0f;
        float speedY = BASE_SPEED * player.getVitesseAvantArriere() / 50.0f;

        // Anisotropic movement time: time = sqrt((dx/vx)² + (dy/vy)²)
        double timeX = (speedX > 0) ? dx / speedX : Double.MAX_VALUE / 2;
        double timeY = (speedY > 0) ? dy / speedY : Double.MAX_VALUE / 2;
        float movementTime = (float) Math.sqrt(timeX * timeX + timeY * timeY);

        // Agilité reduces movement time (acceleration and direction changes)
        float agilityReduction = (player.getAgilite() - 1) / 98.0f * MAX_AGILITY_REDUCTION;

        // Jeu de jambes adds a pre-shot stabilisation overhead
        float stabilizationTime = MAX_STAB_TIME * (1.0f - (player.getJeuDeJambes() - 1) / 98.0f);

        // Fatigue increases the time needed
        float fatiguePenalty = effectiveFatigue * MAX_FATIGUE_PENALTY;

        return movementTime * (1.0f - agilityReduction) * (1.0f + fatiguePenalty) + stabilizationTime;
    }

    private ReachResult classifyReachResult(float margin) {
        if (margin > THRESHOLD_COMFORTABLE) return ReachResult.REACH_RESULT_COMFORTABLE;
        if (margin > THRESHOLD_LATE)        return ReachResult.REACH_RESULT_LATE;
        if (margin > THRESHOLD_STRETCHED)   return ReachResult.REACH_RESULT_STRETCHED;
        if (margin > THRESHOLD_DESPERATE)   return ReachResult.REACH_RESULT_DESPERATE;
        return ReachResult.REACH_RESULT_MISSED;
    }

    /**
     * Determines the player's base (replacement) position after hitting.
     * The player recovers to the centre of their baseline (x=0), on the same y side as the landing position.
     * Placement-quality refinement is deferred to the PointSimulator (issue #15).
     */
    private CourtPosition computeBasePosition(CourtPosition landingPosition) {
        float sideSign = landingPosition.getY() >= 0 ? 1.0f : -1.0f;
        return CourtPosition.newBuilder()
                .setX(0.0f)
                .setY(sideSign * BASELINE_Y)
                .setZ(0.0f)
                .build();
    }
}
