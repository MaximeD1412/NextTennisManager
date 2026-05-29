package com.nexttennis.simulator.brain;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.PlayerHand;
import com.nexttennis.simulator.proto.PlayerTactic;
import com.nexttennis.simulator.proto.ScoreSnapshot;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import com.nexttennis.simulator.proto.Vector3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedBrainTest {

    // ── Movement target (Placement) ───────────────────────────────────────────

    @Test
    void highPlacementTracksIncomingBallXBetterThanLowPlacement() {
        // Ball is at x=3 (right side). High Placement should target closer to the
        // angle-bisector (ball.x * 0.5 = 1.5) than low Placement (near 0).
        GameTickState state = tickState(ball(3f, -5f, 1f), self(0f, 10f, 0f));
        ObservableOpponentState opp = opp(pos(-1f, -10f, 0f));

        TacticalState high = brain(playerWith().placement(90)).tick(state, opp);
        TacticalState low  = brain(playerWith().placement(10)).tick(state, opp);

        assertThat(high.movementTarget().getX()).isGreaterThan(low.movementTarget().getX());
    }

    @Test
    void lowPlacementStaysNearCenterWhenBallIsWide() {
        GameTickState state = tickState(ball(4f, -5f, 1f), self(0f, 10f, 0f));
        ObservableOpponentState opp = opp(pos(0f, -10f, 0f));

        TacticalState result = brain(playerWith().placement(5)).tick(state, opp);

        // Low-Placement player should stay close to center
        assertThat(Math.abs(result.movementTarget().getX())).isLessThan(0.25f);
    }

    @Test
    void movementTargetYIsOnOwnBaseline() {
        // Player at positive-y baseline: movement target y should remain at +BASELINE_Y
        GameTickState state = tickState(ball(2f, -5f, 1f), self(0f, 11.89f, 0f));
        ObservableOpponentState opp = opp(pos(0f, -10f, 0f));

        TacticalState result = brain(playerWith().placement(50)).tick(state, opp);

        assertThat(result.movementTarget().getY()).isCloseTo(11.89f, offset(0.01f));
    }

    @Test
    void movementTargetYIsOnNegativeBaselineWhenPlayerIsAtNegativeY() {
        GameTickState state = tickState(ball(2f, 5f, 1f), self(0f, -11.89f, 0f));
        ObservableOpponentState opp = opp(pos(0f, 10f, 0f));

        TacticalState result = brain(playerWith().placement(50)).tick(state, opp);

        assertThat(result.movementTarget().getY()).isCloseTo(-11.89f, offset(0.01f));
    }

    // ── Shot type (forehand / backhand) ───────────────────────────────────────

    @Test
    void rightHandedPlayerAtPositiveYHitsForehandWhenBallIsToTheLeft() {
        // Facing negative-y (toward net): right-hand forehand side is negative-x
        GameTickState state = tickState(ball(-2f, 5f, 1f), self(0f, 10f, 0f));
        ObservableOpponentState opp = opp(pos(0f, -10f, 0f));

        TacticalState result = brain(playerWith().hand(PlayerHand.PLAYER_HAND_RIGHT)).tick(state, opp);

        assertThat(result.preparedShot()).isEqualTo(ShotType.SHOT_TYPE_FOREHAND);
    }

    @Test
    void rightHandedPlayerAtPositiveYHitsBackhandWhenBallIsToTheRight() {
        GameTickState state = tickState(ball(2f, 5f, 1f), self(0f, 10f, 0f));
        ObservableOpponentState opp = opp(pos(0f, -10f, 0f));

        TacticalState result = brain(playerWith().hand(PlayerHand.PLAYER_HAND_RIGHT)).tick(state, opp);

        assertThat(result.preparedShot()).isEqualTo(ShotType.SHOT_TYPE_BACKHAND);
    }

    @Test
    void leftHandedPlayerAtPositiveYHitsForehandWhenBallIsToTheRight() {
        // Left-hander's forehand is on the positive-x side when facing negative-y
        GameTickState state = tickState(ball(2f, 5f, 1f), self(0f, 10f, 0f));
        ObservableOpponentState opp = opp(pos(0f, -10f, 0f));

        TacticalState result = brain(playerWith().hand(PlayerHand.PLAYER_HAND_LEFT)).tick(state, opp);

        assertThat(result.preparedShot()).isEqualTo(ShotType.SHOT_TYPE_FOREHAND);
    }

    // ── Shot intent (Tactique) ────────────────────────────────────────────────

    @Test
    void aggressiveBaselineTacticProducesAggressiveIntent() {
        TacticalState result = brain(playerWith().tactic(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE))
                .tick(anyState(), anyOpp());
        assertThat(result.shotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_AGGRESSIVE);
    }

    @Test
    void defensiveTacticProducesDefensiveIntent() {
        TacticalState result = brain(playerWith().tactic(PlayerTactic.PLAYER_TACTIC_DEFENSIVE))
                .tick(anyState(), anyOpp());
        assertThat(result.shotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_DEFENSIVE);
    }

    @Test
    void counterPuncherTacticProducesNeutralIntent() {
        TacticalState result = brain(playerWith().tactic(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER))
                .tick(anyState(), anyOpp());
        assertThat(result.shotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_NEUTRAL);
    }

    @Test
    void netRusherAndServeAndVolleyTacticProduceAggressiveIntent() {
        assertThat(brain(playerWith().tactic(PlayerTactic.PLAYER_TACTIC_NET_RUSHER))
                .tick(anyState(), anyOpp()).shotIntent())
                .isEqualTo(ShotIntent.SHOT_INTENT_AGGRESSIVE);

        assertThat(brain(playerWith().tactic(PlayerTactic.PLAYER_TACTIC_SERVE_AND_VOLLEY))
                .tick(anyState(), anyOpp()).shotIntent())
                .isEqualTo(ShotIntent.SHOT_INTENT_AGGRESSIVE);
    }

    // ── Target zone (Construction du point + Vision du court) ─────────────────

    @Test
    void highConstructionDuPointTargetsDeeperThanLowConstruction() {
        // Opponent at negative-y side: target y should be negative and deeper for high construction
        ObservableOpponentState opp = opp(pos(0f, -10f, 0f));

        TacticalState highResult = brain(playerWith().constructionDuPoint(90)).tick(anyState(), opp);
        TacticalState lowResult  = brain(playerWith().constructionDuPoint(10)).tick(anyState(), opp);

        assertThat(Math.abs(highResult.approximateTargetZone().getY()))
                .isGreaterThan(Math.abs(lowResult.approximateTargetZone().getY()));
    }

    @Test
    void highVisionDuCourtCreatesWiderAngleThanLowVision() {
        // Opponent at x=2 (right side): brain aims cross-court (negative x)
        ObservableOpponentState opp = opp(pos(2f, -10f, 0f));

        TacticalState highResult = brain(playerWith().visionDuCourt(90)).tick(anyState(), opp);
        TacticalState lowResult  = brain(playerWith().visionDuCourt(10)).tick(anyState(), opp);

        // High vision creates a wider (more extreme) x angle
        assertThat(Math.abs(highResult.approximateTargetZone().getX()))
                .isGreaterThan(Math.abs(lowResult.approximateTargetZone().getX()));
    }

    @Test
    void targetZoneIsCrossCourt() {
        // Opponent at x=2 → brain should aim toward negative x (away from opponent)
        ObservableOpponentState opp = opp(pos(2f, -10f, 0f));

        TacticalState result = brain(playerWith().visionDuCourt(50)).tick(anyState(), opp);

        assertThat(result.approximateTargetZone().getX()).isNegative();
    }

    @Test
    void targetZoneIsOnOpponentSide() {
        ObservableOpponentState opp = opp(pos(0f, -10f, 0f));

        TacticalState result = brain(playerWith()).tick(anyState(), opp);

        assertThat(result.approximateTargetZone().getY()).isNegative();
    }

    // ── Risk level ────────────────────────────────────────────────────────────

    @Test
    void aggressiveIntentProducesHigherRiskThanDefensive() {
        TacticalState aggressive = brain(playerWith().tactic(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE))
                .tick(anyState(), anyOpp());
        TacticalState defensive = brain(playerWith().tactic(PlayerTactic.PLAYER_TACTIC_DEFENSIVE))
                .tick(anyState(), anyOpp());

        assertThat(aggressive.riskLevel()).isGreaterThan(defensive.riskLevel());
    }

    @Test
    void riskLevelIsWithinBounds() {
        for (PlayerTactic tactic : PlayerTactic.values()) {
            if (tactic == PlayerTactic.UNRECOGNIZED || tactic == PlayerTactic.PLAYER_TACTIC_UNSPECIFIED) continue;
            TacticalState result = brain(playerWith().tactic(tactic)).tick(anyState(), anyOpp());
            assertThat(result.riskLevel()).isBetween(0.0f, 1.0f);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static RuleBasedBrain brain(PlayerBuilder pb) {
        return new RuleBasedBrain(pb.build());
    }

    private static PlayerBuilder playerWith() {
        return new PlayerBuilder();
    }

    private static GameTickState tickState(CourtPosition ball, CourtPosition self) {
        return new GameTickState(
                ball, self, pos(0f, -10f, 0f),
                ScoreSnapshot.getDefaultInstance(), WeatherSnapshot.calm(), 0);
    }

    private static GameTickState anyState() {
        return tickState(ball(0f, -5f, 1f), self(0f, 10f, 0f));
    }

    private static ObservableOpponentState opp(CourtPosition position) {
        return new ObservableOpponentState(position, Vector3.getDefaultInstance(), ShotPreparation.NONE);
    }

    private static ObservableOpponentState anyOpp() {
        return opp(pos(0f, -10f, 0f));
    }

    private static CourtPosition ball(float x, float y, float z) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(z).build();
    }

    private static CourtPosition self(float x, float y, float z) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(z).build();
    }

    private static CourtPosition pos(float x, float y, float z) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(z).build();
    }

    private static org.assertj.core.data.Offset<Float> offset(float v) {
        return org.assertj.core.data.Offset.offset(v);
    }

    /** Fluent builder for TennisPlayerSnapshot focused on attributes used by RuleBasedBrain. */
    private static class PlayerBuilder {
        private int placement = 50;
        private int constructionDuPoint = 50;
        private int visionDuCourt = 50;
        private int choixDesCoups = 50;
        private PlayerHand hand = PlayerHand.PLAYER_HAND_RIGHT;
        private PlayerTactic tactic = PlayerTactic.PLAYER_TACTIC_ALL_COURT;

        PlayerBuilder placement(int v)           { this.placement = v;           return this; }
        PlayerBuilder constructionDuPoint(int v) { this.constructionDuPoint = v; return this; }
        PlayerBuilder visionDuCourt(int v)       { this.visionDuCourt = v;       return this; }
        PlayerBuilder hand(PlayerHand h)         { this.hand = h;                return this; }
        PlayerBuilder tactic(PlayerTactic t)     { this.tactic = t;              return this; }

        TennisPlayerSnapshot build() {
            return TennisPlayerSnapshot.newBuilder()
                    .setPlacement(placement)
                    .setConstructionDuPoint(constructionDuPoint)
                    .setVisionDuCourt(visionDuCourt)
                    .setChoixDesCoups(choixDesCoups)
                    .setDominantHand(hand)
                    .setActiveTactic(tactic)
                    .build();
        }
    }
}
