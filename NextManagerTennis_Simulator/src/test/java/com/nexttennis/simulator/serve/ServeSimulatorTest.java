package com.nexttennis.simulator.serve;

import com.nexttennis.simulator.proto.CourtSide;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ServeSimulatorTest {

    @Test
    void serveStartPopulatedBeforeFirstServe() {
        ServeSimulator simulator = new ServeSimulator(new Random(42));
        TennisPlayerSnapshot server = player("srv-1", 50, 80, 60);

        ServeResult result = simulator.simulate(server, CourtSide.COURT_SIDE_DEUCE, 1);

        assertThat(result.serveStart().getServerId()).isEqualTo("srv-1");
        assertThat(result.serveStart().getServeNumber()).isEqualTo(1);
        assertThat(result.serveStart().getCourtSide()).isEqualTo(CourtSide.COURT_SIDE_DEUCE);
    }

    @Test
    void serveStartReflectsSecondServeContext() {
        ServeSimulator simulator = new ServeSimulator(new Random(42));
        TennisPlayerSnapshot server = player("p2", 50, 80, 60);

        ServeResult result = simulator.simulate(server, CourtSide.COURT_SIDE_AD, 2);

        assertThat(result.serveStart().getServeNumber()).isEqualTo(2);
        assertThat(result.serveStart().getCourtSide()).isEqualTo(CourtSide.COURT_SIDE_AD);
    }

    @Test
    void highReliabilityFirstServeGoesInNearlyAlways() {
        ServeSimulator simulator = new ServeSimulator(new Random(0));
        TennisPlayerSnapshot server = player("p1", 99, 80, 50);

        long inCount = IntStream.range(0, 1000)
                .mapToObj(i -> simulator.simulate(server, CourtSide.COURT_SIDE_DEUCE, 1))
                .filter(r -> r instanceof ServeResult.ServeIn)
                .count();

        // P(in)=0.99 over 1000 trials: threshold 970 is >3σ below the mean 990
        assertThat(inCount).isGreaterThan(969);
    }

    @Test
    void lowReliabilityFirstServeFaultsMostOfTheTime() {
        ServeSimulator simulator = new ServeSimulator(new Random(0));
        TennisPlayerSnapshot server = player("p1", 1, 80, 50);

        long faultCount = IntStream.range(0, 1000)
                .mapToObj(i -> simulator.simulate(server, CourtSide.COURT_SIDE_DEUCE, 1))
                .filter(r -> r instanceof ServeResult.ServeFault)
                .count();

        assertThat(faultCount).isGreaterThan(969);
    }

    @Test
    void secondServeUsesSecondServiceAttribute() {
        // fiabiliteService=99 (first serve in ~99% of the time)
        // secondService=1   (second serve in ~1% of the time)
        TennisPlayerSnapshot server = TennisPlayerSnapshot.newBuilder()
                .setPlayerId("p1").setName("Test")
                .setFiabiliteService(99)
                .setSecondService(1)
                .setPrecisionService(80)
                .build();

        ServeSimulator firstSim = new ServeSimulator(new Random(0));
        long firstIn = IntStream.range(0, 200)
                .mapToObj(i -> firstSim.simulate(server, CourtSide.COURT_SIDE_DEUCE, 1))
                .filter(r -> r instanceof ServeResult.ServeIn)
                .count();

        ServeSimulator secondSim = new ServeSimulator(new Random(0));
        long secondIn = IntStream.range(0, 200)
                .mapToObj(i -> secondSim.simulate(server, CourtSide.COURT_SIDE_DEUCE, 2))
                .filter(r -> r instanceof ServeResult.ServeIn)
                .count();

        assertThat(firstIn).isGreaterThan(180);
        assertThat(secondIn).isLessThan(20);
    }

    @Test
    void serveInContainsShotWithServeTypeAndTargetPosition() {
        ServeSimulator simulator = new ServeSimulator(new Random(0));
        TennisPlayerSnapshot server = player("p1", 99, 80, 60);

        ServeResult.ServeIn serveIn = IntStream.range(0, 20)
                .mapToObj(i -> simulator.simulate(server, CourtSide.COURT_SIDE_DEUCE, 1))
                .filter(r -> r instanceof ServeResult.ServeIn)
                .map(r -> (ServeResult.ServeIn) r)
                .findFirst()
                .orElseThrow();

        assertThat(serveIn.shot().getPlayerId()).isEqualTo("p1");
        assertThat(serveIn.shot().getShotType()).isEqualTo(ShotType.SHOT_TYPE_SERVE);
        assertThat(serveIn.shot().hasTargetPosition()).isTrue();
    }

    @Test
    void faultContainsCorrectServeNumberAndLandingPosition() {
        ServeSimulator simulator = new ServeSimulator(new Random(0));
        TennisPlayerSnapshot server = player("p2", 1, 80, 60);

        ServeResult.ServeFault serveFault = IntStream.range(0, 20)
                .mapToObj(i -> simulator.simulate(server, CourtSide.COURT_SIDE_DEUCE, 2))
                .filter(r -> r instanceof ServeResult.ServeFault)
                .map(r -> (ServeResult.ServeFault) r)
                .findFirst()
                .orElseThrow();

        assertThat(serveFault.fault().getServerId()).isEqualTo("p2");
        assertThat(serveFault.fault().getServeNumber()).isEqualTo(2);
        assertThat(serveFault.fault().hasLandingPosition()).isTrue();
    }

    @Test
    void deuceCourtTargetsPositiveX() {
        // precisionService=99 → sigma≈0, so x ≈ DEUCE_TARGET_X (2.06) regardless of in/out
        ServeSimulator simulator = new ServeSimulator(new Random(42));
        TennisPlayerSnapshot server = player("p1", 50, 99, 50);

        float x = landingX(simulator.simulate(server, CourtSide.COURT_SIDE_DEUCE, 1));

        assertThat(x).isPositive();
    }

    @Test
    void adCourtTargetsNegativeX() {
        ServeSimulator simulator = new ServeSimulator(new Random(42));
        TennisPlayerSnapshot server = player("p1", 50, 99, 50);

        float x = landingX(simulator.simulate(server, CourtSide.COURT_SIDE_AD, 1));

        assertThat(x).isNegative();
    }

    @Test
    void highPrecisionProducesLessPositionVarianceThanLow() {
        TennisPlayerSnapshot highPrec = player("p1", 99, 99, 50);
        TennisPlayerSnapshot lowPrec  = player("p1", 99, 1,  50);

        double highVar = xVariance(highPrec, CourtSide.COURT_SIDE_DEUCE, 300);
        double lowVar  = xVariance(lowPrec,  CourtSide.COURT_SIDE_DEUCE, 300);

        assertThat(highVar).isLessThan(lowVar);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private TennisPlayerSnapshot player(String id, int fiabilite, int precision, int secondService) {
        return TennisPlayerSnapshot.newBuilder()
                .setPlayerId(id).setName("Test")
                .setFiabiliteService(fiabilite)
                .setPrecisionService(precision)
                .setSecondService(secondService)
                .build();
    }

    private float landingX(ServeResult result) {
        return switch (result) {
            case ServeResult.ServeIn si -> si.shot().getTargetPosition().getX();
            case ServeResult.ServeFault sf -> sf.fault().getLandingPosition().getX();
        };
    }

    private double xVariance(TennisPlayerSnapshot server, CourtSide side, int trials) {
        ServeSimulator sim = new ServeSimulator(new Random(0));
        double[] xs = IntStream.range(0, trials)
                .mapToDouble(i -> landingX(sim.simulate(server, side, 1)))
                .toArray();
        double mean = Arrays.stream(xs).average().orElse(0);
        return Arrays.stream(xs).map(x -> (x - mean) * (x - mean)).average().orElse(0);
    }
}
