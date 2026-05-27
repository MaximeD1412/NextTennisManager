package com.nexttennis.simulator.serve;

import com.nexttennis.simulator.proto.CourtSide;
import com.nexttennis.simulator.proto.Fault;
import com.nexttennis.simulator.proto.Shot;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServeSimulatorTest {

    private final ServeSimulator simulator = new ServeSimulator();

    @Test
    void highReliabilityPlayerServesIn() {
        TennisPlayerSnapshot server = TennisPlayerSnapshot.newBuilder()
                .setPlayerId("p1")
                .setName("Ace Player")
                .setPuissanceService(90)
                .setPrecisionService(90)
                .setFiabiliteService(95)
                .build();

        ServeResult result = simulator.simulate(server, CourtSide.COURT_SIDE_DEUCE, 1);

        assertThat(result).isInstanceOf(ServeResult.ServeIn.class);
        Shot shot = ((ServeResult.ServeIn) result).shot();
        assertThat(shot.getPlayerId()).isEqualTo("p1");
        assertThat(shot.getShotType()).isEqualTo(ShotType.SHOT_TYPE_SERVE);
    }

    @Test
    void lowReliabilityPlayerFaults() {
        TennisPlayerSnapshot server = TennisPlayerSnapshot.newBuilder()
                .setPlayerId("p2")
                .setName("Shaky Server")
                .setPuissanceService(30)
                .setPrecisionService(20)
                .setFiabiliteService(5)
                .build();

        ServeResult result = simulator.simulate(server, CourtSide.COURT_SIDE_AD, 1);

        assertThat(result).isInstanceOf(ServeResult.ServeFault.class);
        Fault fault = ((ServeResult.ServeFault) result).fault();
        assertThat(fault.getServerId()).isEqualTo("p2");
        assertThat(fault.getServeNumber()).isEqualTo(1);
    }
}
