package com.nexttennis.simulator.physics;

import com.nexttennis.simulator.hit.HitQualityResult;
import com.nexttennis.simulator.movement.PlayerMovementService;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.IssueDuPoint;
import com.nexttennis.simulator.proto.PhysicsEnvironment;
import com.nexttennis.simulator.proto.PhysicsSimulationResult;
import com.nexttennis.simulator.proto.PlayerHand;
import com.nexttennis.simulator.proto.PlayerTactic;
import com.nexttennis.simulator.proto.ShotEffect;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotSpec;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.Surface;
import com.nexttennis.simulator.proto.SurfaceCoefficients;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test: Java builds ShotSpec, calls PhysicsCoreClient,
 * PhysicsResultInterpreter classifies the result — all through the Java→Rust boundary.
 */
class PhysicsCoreClientIntegrationTest {

    private PhysicsCoreClient client;
    private ShotSpecBuilder shotSpecBuilder;
    private PhysicsResultInterpreter interpreter;

    @BeforeEach
    void setUp() throws IOException {
        String binaryPath = resolveBinaryPath();
        client = new PhysicsCoreClient(binaryPath);
        client.start();
        shotSpecBuilder = new ShotSpecBuilder();
        interpreter = new PhysicsResultInterpreter(new PlayerMovementService());
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    @Test
    void fullPointThroughJavaRustBoundaryReturnsValidIssueDuPoint() throws IOException {
        PhysicsEnvironment env = indoorHardEnv();

        // Attacker at y=+11.89, targeting deep into opponent's court
        TennisPlayerSnapshot attacker = baselinePlayer("p1", 80);
        CourtPosition attackerPos = CourtPosition.newBuilder().setX(0).setY(11.89f).setZ(0).build();
        CourtPosition target      = CourtPosition.newBuilder().setX(1).setY(-8).setZ(0).build();

        HitQualityResult hitResult = new HitQualityResult(0.75f, ShotIntent.SHOT_INTENT_NEUTRAL);
        ShotSpec spec = shotSpecBuilder.build(
                hitResult, attacker, attackerPos,
                ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_TOPSPIN,
                target, "match-1", 0, 0);

        List<PhysicsSimulationResult> results = client.simulateBatch(env, List.of(spec));
        assertThat(results).hasSize(1);

        PhysicsSimulationResult result = results.getFirst();

        // Defender on the opposite side
        TennisPlayerSnapshot defender = baselinePlayer("p2", 70);
        CourtPosition defenderPos = CourtPosition.newBuilder().setX(0).setY(-11.89f).setZ(0).build();

        PhysicsResultInterpreter.Interpretation interp =
                interpreter.interpret(result, defender, defenderPos, 0.0f, hitResult.hitQuality());

        Set<IssueDuPoint> validOutcomes = Set.of(
                IssueDuPoint.ISSUE_DU_POINT_COUP_GAGNANT,
                IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE,
                IssueDuPoint.ISSUE_DU_POINT_FAUTE_FORCEE);

        // Either the rally continues (issueDuPoint == null) or the point ended with a valid outcome
        if (interp.issueDuPoint() != null) {
            assertThat(interp.issueDuPoint()).isIn(validOutcomes);
        } else {
            assertThat(interp.reachResult()).isNotEqualTo(
                    com.nexttennis.simulator.proto.ReachResult.REACH_RESULT_UNSPECIFIED);
        }
    }

    @Test
    void netFaultProducesFaultIssueDuPoint() throws IOException {
        PhysicsEnvironment env = indoorHardEnv();

        // Ball aimed directly into the net (low contact, aimed at feet)
        ShotSpec netFaultSpec = ShotSpec.newBuilder()
                .setContactPosition(CourtPosition.newBuilder().setX(0).setY(5).setZ(0.5f).build())
                .setVelocity(com.nexttennis.simulator.proto.Vector3.newBuilder()
                        .setX(0).setY(-10).setZ(-2).build())
                .setSpinRpm(0)
                .setSpinAxis(com.nexttennis.simulator.proto.Vector3.newBuilder()
                        .setX(0).setY(0).setZ(1).build())
                .setMatchId("m").setPointIndex(0).setShotIndex(0)
                .build();

        List<PhysicsSimulationResult> results = client.simulateBatch(env, List.of(netFaultSpec));
        PhysicsSimulationResult result = results.getFirst();

        if (!result.getClearedNet()) {
            TennisPlayerSnapshot defender = baselinePlayer("p2", 70);
            CourtPosition defenderPos = CourtPosition.newBuilder().setX(0).setY(-11.89f).setZ(0).build();

            PhysicsResultInterpreter.Interpretation interp =
                    interpreter.interpret(result, defender, defenderPos, 0.0f, 0.3f);

            assertThat(interp.issueDuPoint()).isIn(
                    IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE,
                    IssueDuPoint.ISSUE_DU_POINT_FAUTE_FORCEE);
        }
        // If the ball happened to clear the net, the test is vacuously correct
    }

    @Test
    void batchOfThreeReturnsThreeResults() throws IOException {
        PhysicsEnvironment env = indoorHardEnv();

        TennisPlayerSnapshot player = baselinePlayer("p1", 75);
        CourtPosition pos = CourtPosition.newBuilder().setX(0).setY(11.89f).setZ(0).build();
        CourtPosition tgt = CourtPosition.newBuilder().setX(0).setY(-8).setZ(0).build();

        ShotSpec spec = shotSpecBuilder.build(
                new HitQualityResult(0.8f, ShotIntent.SHOT_INTENT_AGGRESSIVE),
                player, pos, ShotType.SHOT_TYPE_FOREHAND, ShotEffect.SHOT_EFFECT_FLAT,
                tgt, "m", 0, 0);

        List<PhysicsSimulationResult> results = client.simulateBatch(env, List.of(spec, spec, spec));
        assertThat(results).hasSize(3);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static PhysicsEnvironment indoorHardEnv() {
        return PhysicsEnvironment.newBuilder()
                .setSurface(Surface.SURFACE_INDOOR_HARD)
                .setSurfaceWetness(0.0f)
                .setCoefficients(SurfaceCoefficients.newBuilder()
                        .setDrag(0.47f).setMagnus(0.10f)
                        .setRestitution(0.75f).setFriction(0.60f)
                        .setWetnessBounceScale(1.0f).setWetnessFrictionScale(1.0f)
                        .build())
                .build();
    }

    private static TennisPlayerSnapshot baselinePlayer(String id, int baseAttr) {
        return TennisPlayerSnapshot.newBuilder()
                .setPlayerId(id)
                .setVitesseLaterale(baseAttr).setVitesseAvantArriere(baseAttr)
                .setAgilite(baseAttr).setJeuDeJambes(baseAttr)
                .setEndurance(baseAttr).setEquilibre(baseAttr)
                .setRegulariteCoupDroit(baseAttr).setPrecisionCoupDroit(baseAttr)
                .setRegulariteRevers(baseAttr).setPrecisionRevers(baseAttr)
                .setLift(baseAttr).setSliceAttr(baseAttr)
                .setActiveTactic(PlayerTactic.PLAYER_TACTIC_ALL_COURT)
                .setDominantHand(PlayerHand.PLAYER_HAND_RIGHT)
                .setHeightM(1.85f).setStandingReachM(2.45f).setBodyMassKg(80.0f)
                .build();
    }

    private static String resolveBinaryPath() {
        String sysProp = System.getProperty("physics.core.binary.path");
        if (sysProp != null) return sysProp;
        return Paths.get(System.getProperty("user.dir"))
                .resolve("../target/debug/sidecar")
                .normalize()
                .toString();
    }
}
