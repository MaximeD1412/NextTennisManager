package com.nexttennis.simulator.movement;

import com.nexttennis.simulator.proto.BallFlightSegment;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.PlayerMove;
import com.nexttennis.simulator.proto.ReachResult;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerMovementServiceTest {

    private final PlayerMovementService service = new PlayerMovementService();

    private TennisPlayerSnapshot player(int vitesseLaterale, int vitesseAvantArriere, int agilite, int jeuDeJambes) {
        return TennisPlayerSnapshot.newBuilder()
                .setPlayerId("p1")
                .setVitesseLaterale(vitesseLaterale)
                .setVitesseAvantArriere(vitesseAvantArriere)
                .setAgilite(agilite)
                .setJeuDeJambes(jeuDeJambes)
                .setPlacement(50)
                .build();
    }

    private CourtPosition pos(float x, float y) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(0).build();
    }

    private BallFlightSegment flight(CourtPosition to, float durationMs) {
        return BallFlightSegment.newBuilder()
                .setFrom(pos(0, -11))
                .setTo(to)
                .setDurationMs(durationMs)
                .build();
    }

    @Test
    void requiredTimeDecreasesAsPhysicalAttributesIncrease() {
        CourtPosition from = pos(0, 11.89f);
        CourtPosition landing = pos(4, 7.0f);

        float weakRequired = service.computeRequiredTime(player(20, 20, 20, 20), from, landing, 0.0f);
        float strongRequired = service.computeRequiredTime(player(90, 90, 90, 90), from, landing, 0.0f);

        assertThat(strongRequired).isLessThan(weakRequired);
    }

    @Test
    void producesComfortableWhenMarginIsLarge() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(0, 11.89f);
        CourtPosition landing = pos(0.5f, 10.0f);

        MovementResult result = service.computeMovement(p, from, flight(landing, 3000), 0.0f);

        assertThat(result.reachResult()).isEqualTo(ReachResult.REACH_RESULT_COMFORTABLE);
    }

    @Test
    void producesLateWhenMarginIsSmallPositive() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(0, 11.89f);
        CourtPosition landing = pos(4, 8.0f);

        float requiredTime = service.computeRequiredTime(p, from, landing, 0.0f);
        float availableMs = (requiredTime + 0.15f) * 1000f;

        MovementResult result = service.computeMovement(p, from, flight(landing, availableMs), 0.0f);

        assertThat(result.reachResult()).isEqualTo(ReachResult.REACH_RESULT_LATE);
    }

    @Test
    void producesStretchedWhenMarginIsSmallNegative() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(0, 11.89f);
        CourtPosition landing = pos(4, 8.0f);

        float requiredTime = service.computeRequiredTime(p, from, landing, 0.0f);
        float availableMs = (requiredTime - 0.15f) * 1000f;

        MovementResult result = service.computeMovement(p, from, flight(landing, availableMs), 0.0f);

        assertThat(result.reachResult()).isEqualTo(ReachResult.REACH_RESULT_STRETCHED);
    }

    @Test
    void producesDesperateWhenMarginIsVeryNegative() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(0, 11.89f);
        CourtPosition landing = pos(4, 8.0f);

        float requiredTime = service.computeRequiredTime(p, from, landing, 0.0f);
        float availableMs = (requiredTime - 0.45f) * 1000f;

        MovementResult result = service.computeMovement(p, from, flight(landing, availableMs), 0.0f);

        assertThat(result.reachResult()).isEqualTo(ReachResult.REACH_RESULT_DESPERATE);
    }

    @Test
    void producesMissedWhenMarginIsExtremelyNegative() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(0, 11.89f);
        CourtPosition landing = pos(4, 8.0f);

        float requiredTime = service.computeRequiredTime(p, from, landing, 0.0f);
        float availableMs = (requiredTime - 0.8f) * 1000f;

        MovementResult result = service.computeMovement(p, from, flight(landing, availableMs), 0.0f);

        assertThat(result.reachResult()).isEqualTo(ReachResult.REACH_RESULT_MISSED);
    }

    @Test
    void replacementPositionIsBaselineNotLandingPosition() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(0, 11.89f);
        CourtPosition landing = pos(4, 7.0f);

        MovementResult result = service.computeMovement(p, from, flight(landing, 2000), 0.0f);

        CourtPosition replacement = result.replacementPosition();
        assertThat(replacement.getX()).isEqualTo(0.0f);
        assertThat(replacement.getY()).isGreaterThan(10.0f);
        assertThat(replacement.getZ()).isEqualTo(0.0f);
    }

    @Test
    void playerMoveEventHasCorrectFields() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(2, 10.0f);
        CourtPosition landing = pos(4, 8.0f);
        BallFlightSegment seg = flight(landing, 1500);

        MovementResult result = service.computeMovement(p, from, seg, 0.0f);

        PlayerMove event = result.event();
        assertThat(event.getPlayerId()).isEqualTo("p1");
        assertThat(event.getFrom().getX()).isEqualTo(from.getX());
        assertThat(event.getFrom().getY()).isEqualTo(from.getY());
        assertThat(event.getTo().getX()).isEqualTo(landing.getX());
        assertThat(event.getTo().getY()).isEqualTo(landing.getY());
        assertThat(event.getAvailableTime()).isEqualTo(1.5f);
        assertThat(event.getRequiredTime()).isPositive();
        assertThat(event.getSlipped()).isFalse();
    }

    @Test
    void weatherEffectsAreZero() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(0, 11.89f);
        CourtPosition landing = pos(3, 8.0f);

        MovementResult result = service.computeMovement(p, from, flight(landing, 2000), 0.0f);

        assertThat(result.event().getSlipped()).isFalse();
    }

    @Test
    void replacementPositionUsesCorrectSideForNegativeY() {
        TennisPlayerSnapshot p = player(50, 50, 50, 50);
        CourtPosition from = pos(0, -11.89f);
        CourtPosition landing = pos(2, -7.0f);

        MovementResult result = service.computeMovement(p, from, flight(landing, 2000), 0.0f);

        assertThat(result.replacementPosition().getY()).isLessThan(-10.0f);
    }
}
