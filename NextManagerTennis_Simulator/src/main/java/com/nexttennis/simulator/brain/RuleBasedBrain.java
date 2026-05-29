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
 *   - Placement            → movement target (coverage of incoming angle bisector)
 *   - Lecture du jeu       → opponent reading (inferred shot candidates + coverage blend)
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

    // Typical cross-court and down-line landing x magnitudes
    private static final float CANDIDATE_CROSS_X = 2.8f;
    private static final float CANDIDATE_LINE_X  = 2.5f;

    // Lecture du jeu thresholds (as fractions of 99)
    private static final float LECTURE_THRESHOLD_LOW  = 1.0f / 3.0f; // ~33 — static only below this
    private static final float LECTURE_THRESHOLD_HIGH = 2.0f / 3.0f; // ~66 — 3 candidates above this

    private final TennisPlayerSnapshot player;

    public RuleBasedBrain(TennisPlayerSnapshot player) {
        this.player = player;
    }

    @Override
    public TacticalState tick(GameTickState state, ObservableOpponentState opponent) {
        CourtPosition movementTarget = computeMovementTarget(state.selfPosition(), state.ballPosition(), opponent);
        ShotType shotType = pickShotType(state.selfPosition(), state.ballPosition());
        ShotIntent shotIntent = tacticToIntent(player.getActiveTactic());
        CourtPosition targetZone = computeTargetZone(opponent.position());
        float riskLevel = computeRiskLevel(shotIntent);
        return new TacticalState(movementTarget, shotType, shotIntent, targetZone, riskLevel);
    }

    /**
     * Recovery position on own baseline. Placement drives the angle-bisector base; Lecture du jeu
     * blends in a predicted-coverage correction derived from inferred opponent shot candidates.
     *
     * Low Lecture du jeu (≤ 1/3): no shot inference — coverage correction uses static opponent x.
     * Medium (1/3 – 2/3): 2 candidate shots weighted by probability.
     * High (> 2/3): 3 candidates with sharper probability distribution.
     */
    private CourtPosition computeMovementTarget(
            CourtPosition selfPos, CourtPosition ballPos, ObservableOpponentState opponent) {

        float placementFactor   = player.getPlacement()    / 99.0f;
        float lectureDeJeuFactor = player.getLectureDuJeu() / 99.0f;

        // Angle-bisector base (Placement-driven, unchanged when Lecture du jeu = 0)
        float bisectorX = ballPos.getX() * placementFactor * 0.5f;

        // Coverage x driven by opponent reading
        float coverageX = computePredictedCoverageX(opponent, lectureDeJeuFactor);

        // Blend: lectureDeJeuFactor=0 → pure bisector; 1 → pure predicted coverage
        float targetX = bisectorX + lectureDeJeuFactor * (coverageX - bisectorX);
        targetX = Math.clamp(targetX, -COURT_SAFE_EDGE, COURT_SAFE_EDGE);

        float baselineSign = selfPos.getY() >= 0 ? 1.0f : -1.0f;
        return CourtPosition.newBuilder()
                .setX(targetX).setY(baselineSign * BASELINE_Y).setZ(0)
                .build();
    }

    /**
     * Returns the x coordinate this player should cover based on opponent reading.
     *
     * Low Lecture du jeu (≤ LECTURE_THRESHOLD_LOW): static opponent position only.
     * Medium: 2 candidate shot landing zones, weighted by type-specific probabilities.
     * High: 3 candidates with sharper distribution.
     */
    private float computePredictedCoverageX(ObservableOpponentState opponent, float lectureDeJeuFactor) {
        float oppX = opponent.position().getX();

        if (lectureDeJeuFactor <= LECTURE_THRESHOLD_LOW) {
            return oppX;
        }

        // Cross-court and down-the-line landing x magnitudes (signed away from / toward oppX)
        float crossX = oppX >= 0 ? -CANDIDATE_CROSS_X :  CANDIDATE_CROSS_X;
        float lineX  = oppX >= 0 ?  CANDIDATE_LINE_X  : -CANDIDATE_LINE_X;

        boolean highLecture = lectureDeJeuFactor > LECTURE_THRESHOLD_HIGH;

        float crossProb, lineProb, middleProb;
        switch (opponent.visiblePreparation()) {
            case FOREHAND -> {
                crossProb  = highLecture ? 0.65f : 0.60f;
                lineProb   = highLecture ? 0.25f : 0.40f;
                middleProb = highLecture ? 0.10f : 0.00f;
            }
            case BACKHAND -> {
                crossProb  = highLecture ? 0.60f : 0.55f;
                lineProb   = highLecture ? 0.30f : 0.45f;
                middleProb = highLecture ? 0.10f : 0.00f;
            }
            case SMASH -> { return 0f; }
            default -> { // NONE
                crossProb  = highLecture ? 0.45f : 0.50f;
                lineProb   = highLecture ? 0.35f : 0.50f;
                middleProb = highLecture ? 0.20f : 0.00f;
            }
        }

        float weighted = crossX * crossProb + lineX * lineProb;
        float total    = crossProb + lineProb + middleProb; // middleX = 0, contributes 0 to weighted
        return weighted / total;
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
