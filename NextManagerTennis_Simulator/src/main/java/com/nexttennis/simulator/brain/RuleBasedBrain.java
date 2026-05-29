package com.nexttennis.simulator.brain;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.PlayerHand;
import com.nexttennis.simulator.proto.PlayerTactic;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;

/**
 * Attribute-driven PlayerBrain. Replaces random shot selection with decisions
 * derived from the player's Intelligence de jeu attributes:
 *   - Placement  → movement target (coverage of incoming angle bisector)
 *   - Construction du point → target depth (positional pressure)
 *   - Vision du court       → target width (angle creation)
 *   - Choix des coups       → risk calibration
 */
public class RuleBasedBrain implements PlayerBrain {

    private static final float COURT_HALF_WIDTH  = 4.115f;
    private static final float COURT_SAFE_EDGE   = COURT_HALF_WIDTH - 0.5f;
    private static final float BASELINE_Y        = 11.89f;

    // Target depth range (y distance from net, on opponent's side)
    private static final float TARGET_MIN_DEPTH  = 7.0f;
    private static final float TARGET_MAX_DEPTH  = 11.0f;

    // Target lateral range (x offset from center)
    private static final float TARGET_MIN_SIDE   = 0.5f;
    private static final float TARGET_MAX_SIDE   = 3.2f;

    private final TennisPlayerSnapshot player;

    public RuleBasedBrain(TennisPlayerSnapshot player) {
        this.player = player;
    }

    @Override
    public TacticalState tick(GameTickState state, ObservableOpponentState opponent) {
        CourtPosition movementTarget = computeMovementTarget(state.selfPosition(), state.ballPosition());
        ShotType shotType = pickShotType(state.selfPosition(), state.ballPosition());
        ShotIntent shotIntent = tacticToIntent(player.getActiveTactic());
        CourtPosition targetZone = computeTargetZone(opponent.position());
        float riskLevel = computeRiskLevel(shotIntent);
        return new TacticalState(movementTarget, shotType, shotIntent, targetZone, riskLevel);
    }

    /**
     * Recovery position on own baseline, tracking the incoming ball's x coordinate
     * proportionally to Placement. High Placement → approaches the angle-bisector;
     * low Placement → drifts toward center.
     *
     * Bisector approximation: after hitting, the optimal coverage x ≈ ball.x * 0.5
     * (splits the two widest angles the opponent can create from ball.x).
     */
    private CourtPosition computeMovementTarget(CourtPosition selfPos, CourtPosition ballPos) {
        float placementFactor = player.getPlacement() / 99.0f;
        float targetX = ballPos.getX() * placementFactor * 0.5f;
        targetX = Math.clamp(targetX, -COURT_SAFE_EDGE, COURT_SAFE_EDGE);
        float baselineSign = selfPos.getY() >= 0 ? 1.0f : -1.0f;
        return CourtPosition.newBuilder()
                .setX(targetX).setY(baselineSign * BASELINE_Y).setZ(0)
                .build();
    }

    /**
     * Forehand / backhand from the ball's x position relative to the player,
     * accounting for dominant hand and which baseline the player occupies.
     */
    private ShotType pickShotType(CourtPosition playerPos, CourtPosition ballPos) {
        float relX = ballPos.getX() - playerPos.getX();
        boolean facingPlusY = playerPos.getY() < 0;
        boolean rightHanded = player.getDominantHand() != PlayerHand.PLAYER_HAND_LEFT;
        float forehandXSign = facingPlusY
                ? (rightHanded ? 1.0f : -1.0f)
                : (rightHanded ? -1.0f : 1.0f);
        return (relX * forehandXSign >= 0)
                ? ShotType.SHOT_TYPE_FOREHAND
                : ShotType.SHOT_TYPE_BACKHAND;
    }

    /**
     * Target zone derived from player attributes rather than raw random.
     *
     * Construction du point controls depth: higher → deeper target (more positional pressure).
     * Vision du court controls width: higher → wider angle (more geometric exploitation).
     * Direction: cross-court (away from opponent's current x) to maximise displacement.
     */
    private CourtPosition computeTargetZone(CourtPosition opponentPos) {
        float constructionFactor = player.getConstructionDuPoint() / 99.0f;
        float visionFactor = player.getVisionDuCourt() / 99.0f;

        float depth = TARGET_MIN_DEPTH + constructionFactor * (TARGET_MAX_DEPTH - TARGET_MIN_DEPTH);
        float opponentSideSign = opponentPos.getY() >= 0 ? 1.0f : -1.0f;
        float targetY = opponentSideSign * depth;

        // Aim away from the opponent (cross-court when they are off-centre)
        float sideSign = opponentPos.getX() >= 0 ? -1.0f : 1.0f;
        float targetX = sideSign * (TARGET_MIN_SIDE + visionFactor * (TARGET_MAX_SIDE - TARGET_MIN_SIDE));
        targetX = Math.clamp(targetX, -COURT_SAFE_EDGE, COURT_SAFE_EDGE);

        return CourtPosition.newBuilder().setX(targetX).setY(targetY).setZ(0).build();
    }

    private ShotIntent tacticToIntent(PlayerTactic tactic) {
        return switch (tactic) {
            case PLAYER_TACTIC_AGGRESSIVE_BASELINE,
                 PLAYER_TACTIC_NET_RUSHER,
                 PLAYER_TACTIC_SERVE_AND_VOLLEY -> ShotIntent.SHOT_INTENT_AGGRESSIVE;
            case PLAYER_TACTIC_COUNTER_PUNCHER,
                 PLAYER_TACTIC_ALL_COURT        -> ShotIntent.SHOT_INTENT_NEUTRAL;
            case PLAYER_TACTIC_DEFENSIVE         -> ShotIntent.SHOT_INTENT_DEFENSIVE;
            default                              -> ShotIntent.SHOT_INTENT_NEUTRAL;
        };
    }

    /**
     * Risk calibrated around tactical intent.
     * Choix des coups shifts the base risk: better shot selection → more precise risk-taking
     * (slightly bolder when aggressive, slightly safer when defensive).
     */
    private float computeRiskLevel(ShotIntent intent) {
        float base = switch (intent) {
            case SHOT_INTENT_AGGRESSIVE -> 0.7f;
            case SHOT_INTENT_NEUTRAL    -> 0.5f;
            default                     -> 0.2f;
        };
        float choixAdjust = (player.getChoixDesCoups() - 50) / 98.0f * 0.10f;
        return Math.clamp(base + choixAdjust, 0.0f, 1.0f);
    }
}
