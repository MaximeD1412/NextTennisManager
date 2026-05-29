package com.nexttennis.simulator.point;

import com.nexttennis.simulator.brain.StubBrain;
import com.nexttennis.simulator.hit.HitQualityService;
import com.nexttennis.simulator.hit.PointContext;
import com.nexttennis.simulator.movement.PlayerMovementService;
import com.nexttennis.simulator.physics.PhysicsCoreClient;
import com.nexttennis.simulator.physics.PhysicsResultInterpreter;
import com.nexttennis.simulator.physics.ShotSpecBuilder;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.CourtSide;
import com.nexttennis.simulator.proto.IssueDuPoint;
import com.nexttennis.simulator.proto.PlayerHand;
import com.nexttennis.simulator.proto.PlayerTactic;
import com.nexttennis.simulator.proto.PointStart;
import com.nexttennis.simulator.proto.ScoreSnapshot;
import com.nexttennis.simulator.proto.Surface;
import com.nexttennis.simulator.proto.SurfaceCoefficients;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import com.nexttennis.simulator.proto.WindVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the full point simulation loop.
 * Requires the compiled Rust physics-core sidecar binary.
 */
class PointSimulatorIntegrationTest {

    private static final Set<IssueDuPoint> VALID_OUTCOMES = Set.of(
            IssueDuPoint.ISSUE_DU_POINT_ACE,
            IssueDuPoint.ISSUE_DU_POINT_DOUBLE_FAUTE,
            IssueDuPoint.ISSUE_DU_POINT_COUP_GAGNANT,
            IssueDuPoint.ISSUE_DU_POINT_FAUTE_NON_FORCEE,
            IssueDuPoint.ISSUE_DU_POINT_FAUTE_FORCEE);

    private PhysicsCoreClient physicsCoreClient;
    private PointSimulator pointSimulator;

    @BeforeEach
    void setUp() throws IOException {
        physicsCoreClient = new PhysicsCoreClient(resolveBinaryPath());
        physicsCoreClient.start();
        pointSimulator = new PointSimulator(
                physicsCoreClient,
                new ShotSpecBuilder(),
                new HitQualityService(),
                new PhysicsResultInterpreter(new PlayerMovementService()),
                new Random(42));
    }

    @AfterEach
    void tearDown() {
        physicsCoreClient.stop();
    }

    @Test
    void completePointProducesValidIssueDuPoint() throws IOException {
        PointInput input = buildInput(allRounderPlayer("srv"), allRounderPlayer("rcv"),
                CourtSide.COURT_SIDE_DEUCE);

        PointResult result = pointSimulator.simulate(input);

        assertThat(result.pointStart()).isNotNull();
        assertThat(result.pointEnd()).isNotNull();
        assertThat(result.pointEnd().getIssueDuPoint()).isIn(VALID_OUTCOMES);
        assertThat(result.pointEnd().getWinnerId()).isIn("srv", "rcv");
        assertThat(result.pointEnd().getRallyLength()).isGreaterThanOrEqualTo(0);
        assertThat(result.weatherAfter()).isNotNull();
    }

    @Test
    void adSideDeliveryProducesValidIssueDuPoint() throws IOException {
        PointInput input = buildInput(allRounderPlayer("srv"), allRounderPlayer("rcv"),
                CourtSide.COURT_SIDE_AD);

        PointResult result = pointSimulator.simulate(input);

        assertThat(result.pointEnd().getIssueDuPoint()).isIn(VALID_OUTCOMES);
    }

    @Test
    void weatherStateIsReturnedAndSurfaceWetnessIsNonNegative() throws IOException {
        PointInput input = buildInput(allRounderPlayer("srv"), allRounderPlayer("rcv"),
                CourtSide.COURT_SIDE_DEUCE);

        PointResult result = pointSimulator.simulate(input);

        assertThat(result.weatherAfter().surfaceWetness()).isGreaterThanOrEqualTo(0.0f);
    }

    @Test
    void zeroWeatherInputsProduceZeroWeatherInOutput() throws IOException {
        // Zero VENT_MOYEN, zero initial wetness, no precipitation → no weather effects
        PointInput input = buildInput(allRounderPlayer("srv"), allRounderPlayer("rcv"),
                CourtSide.COURT_SIDE_DEUCE);

        PointResult result = pointSimulator.simulate(input);

        assertThat(result.weatherAfter().wind().getIntensity()).isEqualTo(0.0f);
        assertThat(result.weatherAfter().surfaceWetness()).isEqualTo(0.0f);
    }

    @Test
    void consecutivePointsProduceWindIncrements() throws IOException {
        WindVector ventMoyen = WindVector.newBuilder().setDirection(1.0f).setIntensity(5.0f).build();
        WeatherState weather = new WeatherState(ventMoyen, 0.5f);

        float maxDeltaPerPoint = PointSimulator.WIND_INTENSITY_DELTA_FRACTION
                * ventMoyen.getIntensity() + 0.001f;

        for (int i = 0; i < 10; i++) {
            float before = weather.wind().getIntensity();
            PointInput input = buildInputWithWeather(
                    allRounderPlayer("srv"), allRounderPlayer("rcv"),
                    CourtSide.COURT_SIDE_DEUCE, weather, ventMoyen, 0.01f, false);

            PointResult result = pointSimulator.simulate(input);
            float after = result.weatherAfter().wind().getIntensity();

            assertThat(Math.abs(after - before)).isLessThanOrEqualTo(maxDeltaPerPoint);
            weather = result.weatherAfter();
        }
    }

    @Test
    void doubleFaultWinnerIsReceiver() throws IOException {
        // Very weak server (fiabiliteService=1, secondService=1) → likely double fault over many trials
        TennisPlayerSnapshot weakServer = TennisPlayerSnapshot.newBuilder()
                .setPlayerId("weak-srv")
                .setFiabiliteService(1).setSecondService(1).setPrecisionService(1)
                .setVitesseLaterale(50).setVitesseAvantArriere(50)
                .setAgilite(50).setJeuDeJambes(50)
                .setStandingReachM(2.45f).setHeightM(1.85f).setBodyMassKg(80)
                .setActiveTactic(PlayerTactic.PLAYER_TACTIC_ALL_COURT)
                .setDominantHand(PlayerHand.PLAYER_HAND_RIGHT)
                .build();

        // Run many points; at least one double fault should appear
        boolean doubleFaultSeen = false;
        for (int i = 0; i < 50; i++) {
            PointInput input = buildInput(weakServer, allRounderPlayer("rcv"),
                    CourtSide.COURT_SIDE_DEUCE);
            PointResult result = pointSimulator.simulate(input);
            if (result.pointEnd().getIssueDuPoint() == IssueDuPoint.ISSUE_DU_POINT_DOUBLE_FAUTE) {
                assertThat(result.pointEnd().getWinnerId()).isEqualTo("rcv");
                assertThat(result.pointEnd().getRallyLength()).isEqualTo(0);
                doubleFaultSeen = true;
                break;
            }
        }
        assertThat(doubleFaultSeen).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PointInput buildInput(
            TennisPlayerSnapshot server,
            TennisPlayerSnapshot receiver,
            CourtSide courtSide) {
        return buildInputWithWeather(server, receiver, courtSide,
                WeatherState.zero(),
                WindVector.newBuilder().setDirection(0).setIntensity(0).build(),
                0.0f, false);
    }

    private PointInput buildInputWithWeather(
            TennisPlayerSnapshot server,
            TennisPlayerSnapshot receiver,
            CourtSide courtSide,
            WeatherState currentWeather,
            WindVector ventMoyen,
            float surfaceDryingRate,
            boolean precipitationActive) {

        PointStart pointStart = PointStart.newBuilder()
                .setSetNumber(1).setGameNumber(1).setPointNumber(1)
                .setServerId(server.getPlayerId())
                .setCourtSide(courtSide)
                .setScore(ScoreSnapshot.getDefaultInstance())
                .build();

        return new PointInput(
                "match-1",
                server, receiver,
                Surface.SURFACE_INDOOR_HARD, indoorCoefficients(),
                currentWeather, pointStart,
                pos(0, PointSimulator.BASELINE_Y),
                pos(0, -PointSimulator.BASELINE_Y),
                PointContext.neutral(),
                0,
                0.0f, 0.0f,
                ventMoyen, surfaceDryingRate, precipitationActive,
                new StubBrain(server, new Random(42)),
                new StubBrain(receiver, new Random(42)));
    }

    private static SurfaceCoefficients indoorCoefficients() {
        return SurfaceCoefficients.newBuilder()
                .setDrag(0.47f).setMagnus(0.10f)
                .setRestitution(0.75f).setFriction(0.60f)
                .setWetnessBounceScale(1.0f).setWetnessFrictionScale(1.0f)
                .build();
    }

    private static TennisPlayerSnapshot allRounderPlayer(String id) {
        return TennisPlayerSnapshot.newBuilder()
                .setPlayerId(id)
                .setVitesseLaterale(70).setVitesseAvantArriere(70)
                .setAgilite(70).setJeuDeJambes(70).setEndurance(70).setEquilibre(70)
                .setFiabiliteService(75).setPrecisionService(75).setSecondService(65)
                .setRegulariteCoupDroit(70).setPrecisionCoupDroit(70)
                .setRegulariteRevers(70).setPrecisionRevers(70)
                .setLift(70).setSliceAttr(70)
                .setClutch(50).setConcentration(70).setConfiance(70).setCombativite(70)
                .setStandingReachM(2.45f).setHeightM(1.85f).setBodyMassKg(80)
                .setActiveTactic(PlayerTactic.PLAYER_TACTIC_ALL_COURT)
                .setDominantHand(PlayerHand.PLAYER_HAND_RIGHT)
                .build();
    }

    private static CourtPosition pos(float x, float y) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(0).build();
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
