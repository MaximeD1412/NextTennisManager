package com.nexttennis.simulator.serve;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.CourtSide;
import com.nexttennis.simulator.proto.Fault;
import com.nexttennis.simulator.proto.ServeStart;
import com.nexttennis.simulator.proto.Shot;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class ServeSimulator {
    /*
     * Legacy placeholder.
     *
     * This class still decides service in/out with direct probability and a sampled
     * landing point. It must not be used as the target model for future work.
     * The service model documented in NextManagerTennis_Simulator/CONTEXT.md and
     * TRAJECTOIRE_PHYSIQUE_SPEC.md replaces this with physical ShotSpec batches
     * handled by the Rust physics-core: contact height from player morphology,
     * initial velocity, spin, net/tape, bounce and service-box validation.
     */

    // Legacy simplification. Deuce/ad is relative to server side and should not
    // be treated as a universal x sign in the physical service model.
    private static final float DEUCE_TARGET_X = 2.06f;
    private static final float AD_TARGET_X = -2.06f;
    private static final float TARGET_Y = 3.2f;
    // Maximum sigma (metres) when precisionService = 1
    private static final float PRECISION_SCALE = 2.0f;

    private final Random random;

    public ServeSimulator() {
        this(new Random());
    }

    ServeSimulator(Random random) {
        this.random = random;
    }

    public ServeResult simulate(TennisPlayerSnapshot server, CourtSide courtSide, int serveNumber) {
        ServeStart serveStart = ServeStart.newBuilder()
                .setServeNumber(serveNumber)
                .setCourtSide(courtSide)
                .setServerId(server.getPlayerId())
                .build();

        int fiabilite = serveNumber == 1
                ? server.getFiabiliteService()
                : server.getSecondService();
        double inProbability = fiabilite / 100.0;

        float targetX = courtSide == CourtSide.COURT_SIDE_DEUCE ? DEUCE_TARGET_X : AD_TARGET_X;
        float sigma = (100 - server.getPrecisionService()) / 100.0f * PRECISION_SCALE;

        float landingX = targetX + (float) (random.nextGaussian() * sigma);
        float landingY = TARGET_Y + (float) (random.nextGaussian() * sigma);

        CourtPosition landingPos = CourtPosition.newBuilder()
                .setX(landingX)
                .setY(landingY)
                .setZ(0f)
                .build();

        if (random.nextDouble() < inProbability) {
            Shot shot = Shot.newBuilder()
                    .setPlayerId(server.getPlayerId())
                    .setShotType(ShotType.SHOT_TYPE_SERVE)
                    .setTargetPosition(landingPos)
                    .build();
            return new ServeResult.ServeIn(serveStart, shot);
        } else {
            Fault fault = Fault.newBuilder()
                    .setServerId(server.getPlayerId())
                    .setServeNumber(serveNumber)
                    .setLandingPosition(landingPos)
                    .build();
            return new ServeResult.ServeFault(serveStart, fault);
        }
    }
}
