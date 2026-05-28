package com.nexttennis.simulator.trajectoire;

import com.nexttennis.simulator.proto.BallFlightSegment;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.IssueDuPoint;
import com.nexttennis.simulator.proto.ShotEffect;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TrajectoireServiceTest {
    /*
     * Tests for the legacy placeholder trajectory model.
     * They document current behavior only; future physics work should replace
     * landing-first assertions with ShotExecution -> integrated Trajectoire tests.
     */

    // ─── HitQuality → variance ───────────────────────────────────────────────

    @Test
    void higherHitQualitySmallerLandingVariance() {
        CourtPosition target = pos(0, -7);
        int trials = 2000;

        double stdDevLowHq  = landingStdDev(new TrajectoireService(new Random(1)), target, 0.1f, trials);
        double stdDevHighHq = landingStdDev(new TrajectoireService(new Random(1)), target, 0.9f, trials);

        assertThat(stdDevHighHq).isLessThan(stdDevLowHq);
    }

    @Test
    void perfectHitQualityProducesNearZeroVariance() {
        // sigma = BASE_SIGMA * (1 - 1.0) = 0, so every sample is exactly the target
        TrajectoireService service = new TrajectoireService(new Random(0));
        CourtPosition target = pos(2, -6);

        double stdDev = landingStdDev(service, target, 1.0f, 500);

        assertThat(stdDev).isEqualTo(0.0);
    }

    @Test
    void computeSigmaScalesWithHitQuality() {
        TrajectoireService service = new TrajectoireService();

        assertThat(service.computeSigma(0.0f, false)).isEqualTo(TrajectoireService.BASE_SIGMA);
        assertThat(service.computeSigma(1.0f, false)).isEqualTo(0.0f);
        assertThat(service.computeSigma(0.5f, false)).isEqualTo(TrajectoireService.BASE_SIGMA * 0.5f);
    }

    // ─── BOISÉ (frame hit) ───────────────────────────────────────────────────

    @Test
    void boiseSigmaIsMaxRegardlessOfHighHitQuality() {
        TrajectoireService service = new TrajectoireService();

        float sigma = service.computeSigma(1.0f, true);

        assertThat(sigma).isEqualTo(TrajectoireService.BOISE_SIGMA);
    }

    @Test
    void boiseSigmaIsMaxRegardlessOfLowHitQuality() {
        TrajectoireService service = new TrajectoireService();

        float sigma = service.computeSigma(0.0f, true);

        assertThat(sigma).isEqualTo(TrajectoireService.BOISE_SIGMA);
    }

    @Test
    void boiseProducesGreaterDispersionThanPerfectShot() {
        CourtPosition target = pos(0, -7);
        int trials = 2000;

        double stdDevBoise  = landingStdDev(new TrajectoireService(new Random(7)), target, 1.0f, true, trials);
        double stdDevNormal = landingStdDev(new TrajectoireService(new Random(7)), target, 1.0f, false, trials);

        assertThat(stdDevBoise).isGreaterThan(stdDevNormal);
    }

    // ─── Fault classification ────────────────────────────────────────────────

    @Test
    void inBoundsReturnsNullFault() {
        TrajectoireService service = new TrajectoireService();
        CourtPosition landing = pos(0, -6);
        CourtPosition playerPos = pos(0, 11.89f);

        IssueDuPoint fault = service.detectFault(landing, playerPos, 0.5f);

        assertThat(fault).isNull();
    }

    @Test
    void wideOutOfBoundsHighHqIsFauteNonForcee() {
        TrajectoireService service = new TrajectoireService();
        CourtPosition landing = pos(5.0f, -6);  // |x| > COURT_HALF_WIDTH
        CourtPosition playerPos = pos(0, 11.89f);

        IssueDuPoint fault = service.detectFault(landing, playerPos, TrajectoireService.FAULT_HQ_THRESHOLD);

        assertThat(fault).isEqualTo(IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE);
    }

    @Test
    void wideOutOfBoundsLowHqIsFauteForcee() {
        TrajectoireService service = new TrajectoireService();
        CourtPosition landing = pos(5.0f, -6);
        CourtPosition playerPos = pos(0, 11.89f);

        IssueDuPoint fault = service.detectFault(landing, playerPos, TrajectoireService.FAULT_HQ_THRESHOLD - 0.01f);

        assertThat(fault).isEqualTo(IssueDuPoint.ISSUE_DU_POINT_FAUTE_FORCEE);
    }

    @Test
    void longOutOfBoundsHighHqIsFauteNonForcee() {
        TrajectoireService service = new TrajectoireService();
        CourtPosition landing = pos(0, -13);  // |y| > BASELINE_Y
        CourtPosition playerPos = pos(0, 11.89f);

        IssueDuPoint fault = service.detectFault(landing, playerPos, 0.8f);

        assertThat(fault).isEqualTo(IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE);
    }

    @Test
    void netFaultHighHqIsFauteNonForcee() {
        // Ball lands on player's own side (y=+8 when player is at y=+11.89) — never cleared net
        TrajectoireService service = new TrajectoireService();
        CourtPosition landing = pos(0, 8.0f);
        CourtPosition playerPos = pos(0, 11.89f);

        IssueDuPoint fault = service.detectFault(
                landing, playerPos, TrajectoireService.FAULT_HQ_THRESHOLD + 0.1f);

        assertThat(fault).isEqualTo(IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE);
    }

    @Test
    void netFaultLowHqIsFauteForcee() {
        TrajectoireService service = new TrajectoireService();
        CourtPosition landing = pos(0, 5.0f);  // same side (positive) as player
        CourtPosition playerPos = pos(0, 11.89f);

        IssueDuPoint fault = service.detectFault(landing, playerPos, 0.1f);

        assertThat(fault).isEqualTo(IssueDuPoint.ISSUE_DU_POINT_FAUTE_FORCEE);
    }

    @Test
    void oppositeSideLandingIsNotNetFault() {
        // Ball crosses to opponent's side — it cleared the net by definition
        TrajectoireService service = new TrajectoireService();
        CourtPosition landing = pos(0, -6.0f);
        CourtPosition playerPos = pos(0, 11.89f);

        IssueDuPoint fault = service.detectFault(landing, playerPos, 0.1f);

        assertThat(fault).isNull(); // in-bounds, opposite side → cleared net
    }

    @Test
    void netFaultDetectedForBothCourtSides() {
        // Net fault is symmetric: player at negative y, ball lands on negative y
        TrajectoireService service = new TrajectoireService();
        CourtPosition playerPos = pos(0, -11.89f);
        CourtPosition landing   = pos(0, -7.0f);  // same side (negative) as player

        IssueDuPoint fault = service.detectFault(landing, playerPos, 0.5f);

        assertThat(fault).isNotNull();
    }

    // ─── BallFlightSegment fields ─────────────────────────────────────────────

    @Test
    void segmentFromHasPlayerPositionAndHitHeight() {
        TrajectoireService service = new TrajectoireService(new Random(0));
        CourtPosition playerPos = pos(2, 10);

        TrajectoireResult result = compute(service, playerPos, pos(0, -6),
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_TOPSPIN,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.7f, Surface.SURFACE_CLAY);

        BallFlightSegment seg = result.segments().getFirst();
        assertThat(seg.getFrom().getX()).isEqualTo(playerPos.getX());
        assertThat(seg.getFrom().getY()).isEqualTo(playerPos.getY());
        assertThat(seg.getFrom().getZ()).isEqualTo(1.0f); // forehand hit height
    }

    @Test
    void segmentToHasLandingPositionWithZeroHeight() {
        TrajectoireService service = new TrajectoireService(new Random(0));

        TrajectoireResult result = compute(service, pos(0, 10), pos(0, -6),
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, Surface.SURFACE_HARD);

        BallFlightSegment seg = result.segments().getFirst();
        assertThat(seg.getTo().getX()).isEqualTo(result.landingPosition().getX());
        assertThat(seg.getTo().getY()).isEqualTo(result.landingPosition().getY());
        assertThat(seg.getTo().getZ()).isEqualTo(0.0f);
    }

    @Test
    void segmentSpinMatchesShotEffect() {
        TrajectoireService service = new TrajectoireService(new Random(0));

        TrajectoireResult result = compute(service, pos(0, 10), pos(0, -6),
                ShotType.SHOT_TYPE_BACKHAND, ShotEffect.SHOT_EFFECT_SLICE,
                ShotIntent.SHOT_INTENT_DEFENSIVE, 0.5f, Surface.SURFACE_GRASS);

        assertThat(result.segments().getFirst().getSpin()).isEqualTo(ShotEffect.SHOT_EFFECT_SLICE);
    }

    @Test
    void segmentDurationIsPositive() {
        TrajectoireService service = new TrajectoireService(new Random(0));

        TrajectoireResult result = compute(service, pos(0, 10), pos(0, -6),
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_TOPSPIN,
                ShotIntent.SHOT_INTENT_AGGRESSIVE, 0.8f, Surface.SURFACE_CLAY);

        assertThat(result.segments().getFirst().getDurationMs()).isPositive();
    }

    @Test
    void segmentSpeedIsPositive() {
        TrajectoireService service = new TrajectoireService(new Random(0));

        TrajectoireResult result = compute(service, pos(0, 10), pos(0, -6),
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_AGGRESSIVE, 0.9f, Surface.SURFACE_INDOOR_HARD);

        assertThat(result.segments().getFirst().getSpeedKmh()).isPositive();
    }

    @Test
    void serveHitHeightIsHigherThanGroundStroke() {
        TrajectoireService service = new TrajectoireService(new Random(0));
        CourtPosition playerPos = pos(0, 11.89f);

        TrajectoireResult forehand = compute(service, playerPos, pos(0, -6),
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, Surface.SURFACE_HARD);

        TrajectoireResult serve = compute(new TrajectoireService(new Random(0)), playerPos, pos(0, -6),
                ShotType.SHOT_TYPE_SERVE, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, Surface.SURFACE_HARD);

        assertThat(serve.segments().getFirst().getFrom().getZ())
                .isGreaterThan(forehand.segments().getFirst().getFrom().getZ());
    }

    // ─── Segment count per surface ────────────────────────────────────────────

    @Test
    void indoorHardEmitsExactlyOneSegment() {
        TrajectoireService service = new TrajectoireService(new Random(0));

        TrajectoireResult result = compute(service, pos(0, 10), pos(0, -6),
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.7f, Surface.SURFACE_INDOOR_HARD);

        assertThat(result.segments()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = Surface.class, names = {"SURFACE_CLAY", "SURFACE_GRASS", "SURFACE_HARD"})
    void outdoorSurfaceEmitsAtLeastOneSegment(Surface surface) {
        TrajectoireService service = new TrajectoireService(new Random(0));

        TrajectoireResult result = compute(service, pos(0, 10), pos(0, -6),
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 0.7f, surface);

        assertThat(result.segments()).hasSizeGreaterThanOrEqualTo(1);
    }

    // ─── Speed by intent ─────────────────────────────────────────────────────

    @Test
    void aggressiveIntentProducesHigherSpeedThanDefensive() {
        TrajectoireService service = new TrajectoireService();

        float aggressive = service.computeSpeed(ShotIntent.SHOT_INTENT_AGGRESSIVE, 0.8f);
        float defensive  = service.computeSpeed(ShotIntent.SHOT_INTENT_DEFENSIVE, 0.8f);

        assertThat(aggressive).isGreaterThan(defensive);
    }

    @Test
    void higherHitQualityProducesHigherSpeed() {
        TrajectoireService service = new TrajectoireService();

        float highHq = service.computeSpeed(ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f);
        float lowHq  = service.computeSpeed(ShotIntent.SHOT_INTENT_NEUTRAL, 0.0f);

        assertThat(highHq).isGreaterThan(lowHq);
    }

    // ─── No weather dependency ────────────────────────────────────────────────

    @Test
    void noWindDeflectionOnIndoorHard() {
        // Wind effects are zero — landing position is purely the sampled Gaussian offset.
        // With sigma=0 (HQ=1.0) the landing equals the target exactly (no wind, no variance).
        TrajectoireService service = new TrajectoireService(new Random(42));
        CourtPosition target = pos(1.5f, -6.0f);

        TrajectoireResult result = compute(service, pos(0, 11.89f), target,
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, Surface.SURFACE_INDOOR_HARD);

        assertThat(result.landingPosition().getX()).isEqualTo(target.getX());
        assertThat(result.landingPosition().getY()).isEqualTo(target.getY());
    }

    @Test
    void noWindDeflectionOnOutdoorSurface() {
        TrajectoireService service = new TrajectoireService(new Random(42));
        CourtPosition target = pos(1.5f, -6.0f);

        TrajectoireResult result = compute(service, pos(0, 11.89f), target,
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                ShotIntent.SHOT_INTENT_NEUTRAL, 1.0f, Surface.SURFACE_CLAY);

        assertThat(result.landingPosition().getX()).isEqualTo(target.getX());
        assertThat(result.landingPosition().getY()).isEqualTo(target.getY());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private CourtPosition pos(float x, float y) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(0).build();
    }

    private TrajectoireResult compute(
            TrajectoireService service,
            CourtPosition playerPos,
            CourtPosition targetZone,
            ShotType shotType,
            ShotEffect shotEffect,
            ShotIntent intent,
            float hitQuality,
            Surface surface) {

        return service.compute(hitQuality, false, intent, shotType, shotEffect, playerPos, targetZone, surface);
    }

    /** Computes standard deviation of landing positions over N trials. */
    private double landingStdDev(TrajectoireService service, CourtPosition target, float hitQuality, int trials) {
        return landingStdDev(service, target, hitQuality, false, trials);
    }

    private double landingStdDev(
            TrajectoireService service, CourtPosition target, float hitQuality, boolean frameHit, int trials) {

        float sigma = service.computeSigma(hitQuality, frameHit);
        double[] xs = IntStream.range(0, trials)
                .mapToDouble(i -> service.sampleLandingPosition(target, sigma).getX())
                .toArray();

        double mean = java.util.Arrays.stream(xs).average().orElse(0);
        return Math.sqrt(java.util.Arrays.stream(xs)
                .map(x -> (x - mean) * (x - mean))
                .average()
                .orElse(0));
    }
}
