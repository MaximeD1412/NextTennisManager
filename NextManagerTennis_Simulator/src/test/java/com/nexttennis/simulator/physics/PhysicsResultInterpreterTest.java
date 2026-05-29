package com.nexttennis.simulator.physics;

import com.nexttennis.simulator.movement.PlayerMovementService;
import com.nexttennis.simulator.proto.BallFlightSegment;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.IssueDuPoint;
import com.nexttennis.simulator.proto.PhysicsSimulationResult;
import com.nexttennis.simulator.proto.PlayerHand;
import com.nexttennis.simulator.proto.PlayerTactic;
import com.nexttennis.simulator.proto.ReachResult;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhysicsResultInterpreterTest {

    private PhysicsResultInterpreter interpreter;

    @BeforeEach
    void setUp() {
        interpreter = new PhysicsResultInterpreter(new PlayerMovementService());
    }

    // ── Fault classification ──────────────────────────────────────────────────

    @Test
    void netFaultHighHqIsFauteNonForcee() {
        assertThat(interpreter.classifyFault(PhysicsResultInterpreter.FAULT_HQ_THRESHOLD))
                .isEqualTo(IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE);
    }

    @Test
    void netFaultLowHqIsFauteForcee() {
        assertThat(interpreter.classifyFault(PhysicsResultInterpreter.FAULT_HQ_THRESHOLD - 0.01f))
                .isEqualTo(IssueDuPoint.ISSUE_DU_POINT_FAUTE_FORCEE);
    }

    // ── Net fault interpretation ──────────────────────────────────────────────

    @Test
    void resultThatDidNotClearNetProducesFault() {
        PhysicsSimulationResult result = PhysicsSimulationResult.newBuilder()
                .setClearedNet(false)
                .build();

        PhysicsResultInterpreter.Interpretation interp = interpreter.interpret(
                result, defender(), pos(0, -11.89f), 0.0f, 0.6f);

        assertThat(interp.issueDuPoint()).isEqualTo(IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE);
    }

    // ── Out-of-bounds interpretation ─────────────────────────────────────────

    @Test
    void ballLandingWideIsAFault() {
        PhysicsSimulationResult result = PhysicsSimulationResult.newBuilder()
                .setClearedNet(true)
                .setLandingPosition(pos(PhysicsResultInterpreter.COURT_HALF_WIDTH + 0.1f, -6))
                .addBallFlightSegments(segment(pos(0, 11), pos(5, -6), 500))
                .build();

        PhysicsResultInterpreter.Interpretation interp = interpreter.interpret(
                result, defender(), pos(0, -11.89f), 0.0f, 0.5f);

        assertThat(interp.issueDuPoint()).isNotNull();
    }

    @Test
    void ballLandingLongIsAFault() {
        PhysicsSimulationResult result = PhysicsSimulationResult.newBuilder()
                .setClearedNet(true)
                .setLandingPosition(pos(0, -(PhysicsResultInterpreter.BASELINE_Y + 0.1f)))
                .addBallFlightSegments(segment(pos(0, 11), pos(0, -13), 600))
                .build();

        PhysicsResultInterpreter.Interpretation interp = interpreter.interpret(
                result, defender(), pos(0, -11.89f), 0.0f, 0.5f);

        assertThat(interp.issueDuPoint()).isNotNull();
    }

    // ── In-bounds: reach result ───────────────────────────────────────────────

    @Test
    void inBoundsBallWithPlentyOfTimeIsComfortableReach() {
        // Segment 2000ms → defender at baseline has ample time
        PhysicsSimulationResult result = PhysicsSimulationResult.newBuilder()
                .setClearedNet(true)
                .setLandingPosition(pos(0, -6))
                .addBallFlightSegments(segment(pos(0, 11), pos(0, -6), 2000))
                .build();

        PhysicsResultInterpreter.Interpretation interp = interpreter.interpret(
                result, fastDefender(), pos(0, -11.89f), 0.0f, 0.8f);

        assertThat(interp.issueDuPoint()).isNull();
        assertThat(interp.reachResult()).isIn(
                ReachResult.REACH_RESULT_COMFORTABLE,
                ReachResult.REACH_RESULT_LATE);
    }

    @Test
    void inBoundsBallWithNoTimeIsMissed() {
        // Segment 1ms → impossible to reach
        PhysicsSimulationResult result = PhysicsSimulationResult.newBuilder()
                .setClearedNet(true)
                .setLandingPosition(pos(4, -6))
                .addBallFlightSegments(segment(pos(0, 11), pos(4, -6), 1))
                .build();

        PhysicsResultInterpreter.Interpretation interp = interpreter.interpret(
                result, defender(), pos(0, -11.89f), 0.0f, 0.9f);

        assertThat(interp.reachResult()).isEqualTo(ReachResult.REACH_RESULT_MISSED);
        assertThat(interp.issueDuPoint()).isEqualTo(IssueDuPoint.ISSUE_DU_POINT_COUP_GAGNANT);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static CourtPosition pos(float x, float y) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(0).build();
    }

    private static BallFlightSegment segment(CourtPosition from, CourtPosition to, float durationMs) {
        return BallFlightSegment.newBuilder()
                .setFrom(from).setTo(to).setDurationMs(durationMs).setSpeedKmh(100)
                .build();
    }

    private static TennisPlayerSnapshot defender() {
        return player(50);
    }

    private static TennisPlayerSnapshot fastDefender() {
        return player(99);
    }

    private static TennisPlayerSnapshot player(int speed) {
        return TennisPlayerSnapshot.newBuilder()
                .setPlayerId("p")
                .setVitesseLaterale(speed).setVitesseAvantArriere(speed)
                .setAgilite(speed).setJeuDeJambes(speed)
                .setActiveTactic(PlayerTactic.PLAYER_TACTIC_ALL_COURT)
                .setDominantHand(PlayerHand.PLAYER_HAND_RIGHT)
                .setHeightM(1.85f).setStandingReachM(2.45f).setBodyMassKg(80.0f)
                .build();
    }
}
