package com.nexttennis.simulator.serve;

import com.nexttennis.simulator.proto.CourtSide;
import com.nexttennis.simulator.proto.Fault;
import com.nexttennis.simulator.proto.Shot;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.springframework.stereotype.Component;

/**
 * Determines whether a serve lands in or faults based on the server's service attributes.
 * fiabiliteService drives the in/out threshold; full trajectory simulation comes later.
 */
@Component
public class ServeSimulator {

    private static final int IN_THRESHOLD = 50;

    public ServeResult simulate(TennisPlayerSnapshot server, CourtSide courtSide, int serveNumber) {
        if (server.getFiabiliteService() >= IN_THRESHOLD) {
            Shot shot = Shot.newBuilder()
                    .setPlayerId(server.getPlayerId())
                    .setShotType(ShotType.SHOT_TYPE_SERVE)
                    .build();
            return new ServeResult.ServeIn(shot);
        } else {
            Fault fault = Fault.newBuilder()
                    .setServerId(server.getPlayerId())
                    .setServeNumber(serveNumber)
                    .build();
            return new ServeResult.ServeFault(fault);
        }
    }
}
