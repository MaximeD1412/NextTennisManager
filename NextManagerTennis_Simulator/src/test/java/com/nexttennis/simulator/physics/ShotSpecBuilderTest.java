package com.nexttennis.simulator.physics;

import com.nexttennis.simulator.hit.HitQualityResult;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.PlayerHand;
import com.nexttennis.simulator.proto.PlayerTactic;
import com.nexttennis.simulator.proto.ShotEffect;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotSpec;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import com.nexttennis.simulator.proto.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShotSpecBuilderTest {

    private ShotSpecBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ShotSpecBuilder();
    }

    // ── Contact position ──────────────────────────────────────────────────────

    @Test
    void contactPositionXYMatchesPlayerPosition() {
        ShotSpec spec = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.8f, pos(3, 10), pos(0, -7));

        assertThat(spec.getContactPosition().getX()).isEqualTo(3.0f);
        assertThat(spec.getContactPosition().getY()).isEqualTo(10.0f);
    }

    @Test
    void serveContactHeightUsesStandingReach() {
        TennisPlayerSnapshot player = player(75, 2.5f);
        ShotSpec spec = builder.build(
                new HitQualityResult(0.8f, ShotIntent.SHOT_INTENT_AGGRESSIVE),
                player, pos(0, -11.89f),
                ShotType.SHOT_TYPE_SERVE, ShotEffect.SHOT_EFFECT_FLAT,
                pos(1, 5), "m", 0, 0);

        assertThat(spec.getContactPosition().getZ()).isEqualTo(2.5f);
    }

    @Test
    void groundstrokeContactHeightIsOneMetre() {
        ShotSpec spec = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.8f, pos(0, 10), pos(0, -7));

        assertThat(spec.getContactPosition().getZ()).isEqualTo(1.0f);
    }

    @Test
    void volleyContactHeightIsHigherThanGroundstroke() {
        ShotSpec volley = build(ShotType.SHOT_TYPE_VOLLEY_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.8f, pos(0, 3), pos(0, -5));
        ShotSpec groundstroke = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.8f, pos(0, 10), pos(0, -7));

        assertThat(volley.getContactPosition().getZ())
                .isGreaterThan(groundstroke.getContactPosition().getZ());
    }

    // ── Velocity ──────────────────────────────────────────────────────────────

    @Test
    void velocityDirectsTowardsTarget() {
        // attacker at y=+10, targeting y=-7 → vy must be negative
        ShotSpec spec = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.8f, pos(0, 10), pos(0, -7));

        assertThat(spec.getVelocity().getY()).isNegative();
    }

    @Test
    void aggressiveIntentProducesHigherSpeedThanDefensive() {
        ShotSpec aggressive = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_AGGRESSIVE, 0.8f, pos(0, 10), pos(0, -7));
        ShotSpec defensive = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_DEFENSIVE, 0.8f, pos(0, 10), pos(0, -7));

        float aggressiveSpeed = horizontalSpeed(aggressive.getVelocity());
        float defensiveSpeed  = horizontalSpeed(defensive.getVelocity());
        assertThat(aggressiveSpeed).isGreaterThan(defensiveSpeed);
    }

    @Test
    void higherHitQualityProducesHigherSpeed() {
        ShotSpec highHq = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, pos(0, 10), pos(0, -7));
        ShotSpec lowHq = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.0f, pos(0, 10), pos(0, -7));

        assertThat(horizontalSpeed(highHq.getVelocity()))
                .isGreaterThan(horizontalSpeed(lowHq.getVelocity()));
    }

    // ── Spin ──────────────────────────────────────────────────────────────────

    @Test
    void flatShotHasZeroSpinRpm() {
        ShotSpec spec = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.8f, pos(0, 10), pos(0, -7));

        assertThat(spec.getSpinRpm()).isZero();
    }

    @Test
    void topspinHasHigherSpinThanSlice() {
        ShotSpec topspin = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_TOPSPIN,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, pos(0, 10), pos(0, -7));
        ShotSpec slice = build(ShotType.SHOT_TYPE_BACKHAND, ShotEffect.SHOT_EFFECT_SLICE,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, pos(0, 10), pos(0, -7));

        assertThat(topspin.getSpinRpm()).isGreaterThan(slice.getSpinRpm());
    }

    @Test
    void higherHitQualityProducesMoreSpin() {
        ShotSpec highHq = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_TOPSPIN,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, pos(0, 10), pos(0, -7));
        ShotSpec lowHq = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_TOPSPIN,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.2f, pos(0, 10), pos(0, -7));

        assertThat(highHq.getSpinRpm()).isGreaterThan(lowHq.getSpinRpm());
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    @Test
    void matchMetadataPassedThrough() {
        ShotSpec spec = build(ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.8f, pos(0, 10), pos(0, -7));

        assertThat(spec.getMatchId()).isEqualTo("test-match");
        assertThat(spec.getPointIndex()).isEqualTo(3);
        assertThat(spec.getShotIndex()).isEqualTo(7);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ShotSpec build(ShotType type, ShotEffect effect, ShotIntent intent,
                            float hitQuality, CourtPosition playerPos, CourtPosition target) {
        return builder.build(
                new HitQualityResult(hitQuality, intent),
                player(75, 2.4f),
                playerPos, type, effect, target,
                "test-match", 3, 7);
    }

    private static CourtPosition pos(float x, float y) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(0).build();
    }

    private static TennisPlayerSnapshot player(int attr, float standingReach) {
        return TennisPlayerSnapshot.newBuilder()
                .setPlayerId("p")
                .setVitesseLaterale(attr).setVitesseAvantArriere(attr)
                .setAgilite(attr).setJeuDeJambes(attr)
                .setLift(attr).setSliceAttr(attr)
                .setActiveTactic(PlayerTactic.PLAYER_TACTIC_ALL_COURT)
                .setDominantHand(PlayerHand.PLAYER_HAND_RIGHT)
                .setHeightM(1.85f).setStandingReachM(standingReach).setBodyMassKg(80.0f)
                .build();
    }

    private static float horizontalSpeed(Vector3 v) {
        return (float) Math.sqrt(v.getX() * v.getX() + v.getY() * v.getY());
    }
}
