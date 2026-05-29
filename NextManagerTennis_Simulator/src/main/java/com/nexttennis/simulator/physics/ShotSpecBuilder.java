package com.nexttennis.simulator.physics;

import com.nexttennis.simulator.hit.HitQualityResult;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.ShotEffect;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotSpec;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import com.nexttennis.simulator.proto.Vector3;
import org.springframework.stereotype.Component;

/**
 * Converts game-domain hit data into a physical ShotSpec for the Rust physics-core.
 *
 * Responsibilities (stays in Java per the physics boundary contract):
 *  - game Attribut → spin_rpm
 *  - ShotIntent → velocity magnitude
 *  - player morphology → contact height
 */
@Component
public class ShotSpecBuilder {

    private static final float GRAVITY = 9.81f;

    // Base shot speeds (m/s) per ShotIntent, modulated by HitQuality
    static final float BASE_SPEED_AGGRESSIVE_MS = 41.7f; // ~150 km/h
    static final float BASE_SPEED_NEUTRAL_MS    = 33.3f; // ~120 km/h
    static final float BASE_SPEED_DEFENSIVE_MS  = 25.0f; //  ~90 km/h

    // Base spin RPM per ShotEffect before player-attribute scaling
    static final float SPIN_RPM_TOPSPIN = 2500.0f;
    static final float SPIN_RPM_LIFT    = 3500.0f;
    static final float SPIN_RPM_SLICE   = 1500.0f;

    public ShotSpec build(
            HitQualityResult hitResult,
            TennisPlayerSnapshot player,
            CourtPosition playerPosition,
            ShotType shotType,
            ShotEffect shotEffect,
            CourtPosition targetZone,
            String matchId,
            int pointIndex,
            int shotIndex) {

        float hitHeight = computeHitHeight(shotType, player);
        CourtPosition contact = CourtPosition.newBuilder()
                .setX(playerPosition.getX())
                .setY(playerPosition.getY())
                .setZ(hitHeight)
                .build();

        Vector3 velocity = computeVelocity(contact, targetZone,
                hitResult.effectiveShotIntent(), hitResult.hitQuality());
        float spinRpm   = computeSpinRpm(shotEffect, player, hitResult.hitQuality());
        Vector3 spinAxis = computeSpinAxis(contact, targetZone, shotEffect);

        return ShotSpec.newBuilder()
                .setContactPosition(contact)
                .setVelocity(velocity)
                .setSpinRpm(spinRpm)
                .setSpinAxis(spinAxis)
                .setMatchId(matchId)
                .setPointIndex(pointIndex)
                .setShotIndex(shotIndex)
                .build();
    }

    float computeHitHeight(ShotType shotType, TennisPlayerSnapshot player) {
        return switch (shotType) {
            case SHOT_TYPE_SERVE, SHOT_TYPE_SMASH ->
                    player.getStandingReachM() > 0 ? player.getStandingReachM() : 2.5f;
            case SHOT_TYPE_VOLLEY_FOREHAND, SHOT_TYPE_VOLLEY_BACKHAND -> 1.5f;
            default -> 1.0f;
        };
    }

    Vector3 computeVelocity(CourtPosition from, CourtPosition to,
                             ShotIntent intent, float hitQuality) {
        float speedMs = baseSpeed(intent) * (0.7f + 0.3f * hitQuality);

        float dx = to.getX() - from.getX();
        float dy = to.getY() - from.getY();
        float horizontalDist = (float) Math.sqrt(dx * dx + dy * dy);

        if (horizontalDist < 0.01f) {
            return Vector3.newBuilder().setX(0).setY(speedMs).setZ(1.0f).build();
        }

        float tFlight = horizontalDist / speedMs;
        // vz that produces a parabolic arc landing at ground level (z=0)
        float vz = (0.0f - from.getZ()) / tFlight + 0.5f * GRAVITY * tFlight;

        float scale = speedMs / horizontalDist;
        return Vector3.newBuilder()
                .setX(dx * scale)
                .setY(dy * scale)
                .setZ(vz)
                .build();
    }

    float computeSpinRpm(ShotEffect shotEffect, TennisPlayerSnapshot player, float hitQuality) {
        float base = switch (shotEffect) {
            case SHOT_EFFECT_TOPSPIN -> SPIN_RPM_TOPSPIN;
            case SHOT_EFFECT_LIFT    -> SPIN_RPM_LIFT;
            case SHOT_EFFECT_SLICE   -> SPIN_RPM_SLICE;
            default                  -> 0.0f;
        };
        // Player lift/slice attributes scale the effective spin
        float attrScale = switch (shotEffect) {
            case SHOT_EFFECT_TOPSPIN -> player.getLift() / 99.0f;
            case SHOT_EFFECT_SLICE   -> player.getSliceAttr() / 99.0f;
            default                  -> 1.0f;
        };
        return base * (0.5f + 0.5f * attrScale) * hitQuality;
    }

    Vector3 computeSpinAxis(CourtPosition from, CourtPosition to, ShotEffect shotEffect) {
        // Spin axis is perpendicular to the direction of travel and horizontal.
        // For +y motion: topspin axis = [-1,0,0] (cross(omega,v) → downward Magnus).
        // For -y motion: signs flip.
        float sign = (to.getY() - from.getY()) >= 0 ? 1.0f : -1.0f;
        return switch (shotEffect) {
            case SHOT_EFFECT_TOPSPIN, SHOT_EFFECT_LIFT ->
                    Vector3.newBuilder().setX(-sign).setY(0).setZ(0).build();
            case SHOT_EFFECT_SLICE ->
                    Vector3.newBuilder().setX(sign).setY(0).setZ(0).build();
            default -> Vector3.newBuilder().setX(0).setY(0).setZ(1).build();
        };
    }

    private static float baseSpeed(ShotIntent intent) {
        return switch (intent) {
            case SHOT_INTENT_AGGRESSIVE -> BASE_SPEED_AGGRESSIVE_MS;
            case SHOT_INTENT_DEFENSIVE  -> BASE_SPEED_DEFENSIVE_MS;
            default                     -> BASE_SPEED_NEUTRAL_MS;
        };
    }
}
