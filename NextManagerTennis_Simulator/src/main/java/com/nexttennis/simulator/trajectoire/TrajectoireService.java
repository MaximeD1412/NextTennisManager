package com.nexttennis.simulator.trajectoire;

import com.nexttennis.simulator.proto.BallFlightSegment;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.IssueDuPoint;
import com.nexttennis.simulator.proto.ShotEffect;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.Surface;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class TrajectoireService {
    /*
     * Legacy placeholder.
     *
     * This class currently samples a final landing position first and builds one
     * visual segment from that result. The target simulator model is the opposite:
     * build a physical ShotExecution, send ShotSpec batches to the Rust
     * physics-core, integrate ball flight with drag/Magnus/wind, detect
     * net/tape/bounce interactions, then derive the landing and fault.
     * See NextManagerTennis_Simulator/CONTEXT.md and TRAJECTOIRE_PHYSIQUE_SPEC.md.
     */

    // Gaussian sigma (metres) for landing position spread at HitQuality = 0
    static final float BASE_SIGMA = 2.0f;
    // BOISÉ (frame hit): maximum spread regardless of HitQuality
    static final float BOISE_SIGMA = 4.5f;
    // HitQuality at or above which a fault is classified as FAUTE_NON_FORCÉE
    static final float FAULT_HQ_THRESHOLD = 0.40f;
    // Singles court half-width (metres)
    static final float COURT_HALF_WIDTH = 4.115f;
    // Distance from net to baseline (metres)
    static final float BASELINE_Y = 11.89f;
    // Base ball speed (km/h) per ShotIntent; modulated by HitQuality in computeSpeed()
    static final float BASE_SPEED_AGGRESSIVE_KMH = 150.0f;
    static final float BASE_SPEED_NEUTRAL_KMH    = 120.0f;
    static final float BASE_SPEED_DEFENSIVE_KMH  =  90.0f;

    private final Random random;

    public TrajectoireService() {
        this.random = new Random();
    }

    TrajectoireService(Random random) {
        this.random = random;
    }

    /**
     * Computes the ball's effective landing position, emits BALL_FLIGHT_SEGMENT events,
     * and classifies any resulting fault.
     *
     * @param hitQuality          0.0–1.0 execution coefficient from HitQualityService
     * @param frameHit            true when the ball hits the frame (BOISÉ) — forces maximum variance
     * @param effectiveShotIntent constrained intent from HitQualityService
     * @param shotType            stroke type (used for hit height and segment speed)
     * @param shotEffect          spin applied (propagated to BallFlightSegment)
     * @param playerPosition      CourtPosition of the hitter at time of contact
     * @param targetZone          intended landing CourtPosition chosen by the point loop
     * @param surface             court surface (determines segment count; weather adds more in #18)
     * @return TrajectoireResult with landing position, segments, and fault type (null if in-bounds)
     */
    public TrajectoireResult compute(
            float hitQuality,
            boolean frameHit,
            ShotIntent effectiveShotIntent,
            ShotType shotType,
            ShotEffect shotEffect,
            CourtPosition playerPosition,
            CourtPosition targetZone,
            Surface surface) {

        float sigma = computeSigma(hitQuality, frameHit);
        CourtPosition landing = sampleLandingPosition(targetZone, sigma);
        IssueDuPoint fault = detectFault(landing, playerPosition, hitQuality);
        List<BallFlightSegment> segments = buildSegments(
                playerPosition, landing, shotType, shotEffect, effectiveShotIntent, hitQuality, surface);

        return new TrajectoireResult(landing, segments, fault);
    }

    // Package-private for direct testing.
    float computeSigma(float hitQuality, boolean frameHit) {
        if (frameHit) return BOISE_SIGMA;
        return BASE_SIGMA * (1.0f - hitQuality);
    }

    // Package-private for direct testing.
    CourtPosition sampleLandingPosition(CourtPosition targetZone, float sigma) {
        float x = targetZone.getX() + (float) (random.nextGaussian() * sigma);
        float y = targetZone.getY() + (float) (random.nextGaussian() * sigma);
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(0.0f).build();
    }

    // Package-private for direct testing.
    IssueDuPoint detectFault(CourtPosition landing, CourtPosition playerPosition, float hitQuality) {
        boolean outOfBounds = Math.abs(landing.getX()) > COURT_HALF_WIDTH
                           || Math.abs(landing.getY()) > BASELINE_Y;
        boolean netFault = hitsNet(playerPosition, landing);

        if (!outOfBounds && !netFault) return null;
        return hitQuality >= FAULT_HQ_THRESHOLD
                ? IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE
                : IssueDuPoint.ISSUE_DU_POINT_FAUTE_FORCEE;
    }

    private boolean hitsNet(CourtPosition from, CourtPosition landing) {
        float fromY = from.getY();
        float toY   = landing.getY();
        if (fromY == 0.0f) return false;
        // Ball lands on the hitter's own side → it never cleared the net
        return Math.signum(fromY) == Math.signum(toY);
    }

    // Package-private for direct testing.
    float computeSpeed(ShotIntent intent, float hitQuality) {
        float base = switch (intent) {
            case SHOT_INTENT_AGGRESSIVE -> BASE_SPEED_AGGRESSIVE_KMH;
            case SHOT_INTENT_DEFENSIVE  -> BASE_SPEED_DEFENSIVE_KMH;
            default                     -> BASE_SPEED_NEUTRAL_KMH;
        };
        // Lower HitQuality reduces speed but never below 70% of the base
        return base * (0.7f + 0.3f * hitQuality);
    }

    private List<BallFlightSegment> buildSegments(
            CourtPosition playerPosition,
            CourtPosition landing,
            ShotType shotType,
            ShotEffect shotEffect,
            ShotIntent effectiveShotIntent,
            float hitQuality,
            Surface surface) {

        float hitHeight = computeHitHeight(shotType);
        CourtPosition from = CourtPosition.newBuilder()
                .setX(playerPosition.getX())
                .setY(playerPosition.getY())
                .setZ(hitHeight)
                .build();
        CourtPosition to = CourtPosition.newBuilder()
                .setX(landing.getX())
                .setY(landing.getY())
                .setZ(0.0f)
                .build();

        float speedKmh   = computeSpeed(effectiveShotIntent, hitQuality);
        float durationMs = computeDurationMs(from, to, speedKmh);

        BallFlightSegment segment = BallFlightSegment.newBuilder()
                .setFrom(from)
                .setTo(to)
                .setDurationMs(durationMs)
                .setSpeedKmh(speedKmh)
                .setSpin(shotEffect)
                .build();

        // One segment for all surfaces now; outdoor surfaces gain additional segments
        // when wind effects are wired in issue #18.
        return List.of(segment);
    }

    private float computeHitHeight(ShotType shotType) {
        return switch (shotType) {
            case SHOT_TYPE_SERVE, SHOT_TYPE_SMASH -> 2.5f;
            case SHOT_TYPE_VOLLEY_FOREHAND, SHOT_TYPE_VOLLEY_BACKHAND -> 1.5f;
            default -> 1.0f;
        };
    }

    private float computeDurationMs(CourtPosition from, CourtPosition to, float speedKmh) {
        float dx = to.getX() - from.getX();
        float dy = to.getY() - from.getY();
        float horizontalDistance = (float) Math.sqrt(dx * dx + dy * dy);
        float speedMs = speedKmh / 3.6f;
        return (horizontalDistance / speedMs) * 1000.0f;
    }
}
