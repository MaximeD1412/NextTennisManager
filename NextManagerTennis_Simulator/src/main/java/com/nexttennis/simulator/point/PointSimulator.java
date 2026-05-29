package com.nexttennis.simulator.point;

import com.nexttennis.simulator.brain.GameTickState;
import com.nexttennis.simulator.brain.ObservableOpponentState;
import com.nexttennis.simulator.brain.PlayerBrain;
import com.nexttennis.simulator.brain.ShotPreparation;
import com.nexttennis.simulator.brain.TacticalState;
import com.nexttennis.simulator.brain.WeatherSnapshot;
import com.nexttennis.simulator.hit.HitQualityResult;
import com.nexttennis.simulator.hit.HitQualityService;
import com.nexttennis.simulator.physics.PhysicsCoreClient;
import com.nexttennis.simulator.physics.PhysicsResultInterpreter;
import com.nexttennis.simulator.physics.ShotSpecBuilder;
import com.nexttennis.simulator.proto.BallFlightSegment;
import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.CourtSide;
import com.nexttennis.simulator.proto.IssueDuPoint;
import com.nexttennis.simulator.proto.PhysicsEnvironment;
import com.nexttennis.simulator.proto.PhysicsSimulationResult;
import com.nexttennis.simulator.proto.PointEnd;
import com.nexttennis.simulator.proto.ReachResult;
import com.nexttennis.simulator.proto.ScoreSnapshot;
import com.nexttennis.simulator.proto.ShotEffect;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotSpec;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import com.nexttennis.simulator.proto.Vector3;
import com.nexttennis.simulator.proto.WindVector;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Orchestrates a single tennis point from POINT_START to POINT_END.
 *
 * Responsibilities:
 *  - Weather state evolution between points (wind drift, surface wetness decay)
 *  - Serve phase: first serve → optional second serve → DOUBLE_FAUTE or ACE or rally entry
 *  - Rally phase: alternating shots via PhysicsCoreClient until IssueDuPoint is resolved
 */
@Component
public class PointSimulator {

    // Court geometry constants
    static final float SERVICE_LINE_Y   = 6.40f;
    static final float COURT_HALF_WIDTH = 4.115f;
    static final float BASELINE_Y       = 11.89f;
    static final float SERVICE_BOX_CENTER_X = 2.06f;

    static final int MAX_RALLY_SHOTS = 200;

    // Wind evolution: max direction change per point (radians)
    static final float WIND_DIRECTION_DELTA = 0.15f;
    // Wind evolution: max intensity change as fraction of ventMoyen intensity per point
    static final float WIND_INTENSITY_DELTA_FRACTION = 0.10f;

    private final PhysicsCoreClient physicsCoreClient;
    private final ShotSpecBuilder shotSpecBuilder;
    private final HitQualityService hitQualityService;
    private final PhysicsResultInterpreter physicsResultInterpreter;
    private final Random random;

    public PointSimulator(
            PhysicsCoreClient physicsCoreClient,
            ShotSpecBuilder shotSpecBuilder,
            HitQualityService hitQualityService,
            PhysicsResultInterpreter physicsResultInterpreter) {
        this(physicsCoreClient, shotSpecBuilder, hitQualityService,
                physicsResultInterpreter, new Random());
    }

    PointSimulator(
            PhysicsCoreClient physicsCoreClient,
            ShotSpecBuilder shotSpecBuilder,
            HitQualityService hitQualityService,
            PhysicsResultInterpreter physicsResultInterpreter,
            Random random) {
        this.physicsCoreClient = physicsCoreClient;
        this.shotSpecBuilder = shotSpecBuilder;
        this.hitQualityService = hitQualityService;
        this.physicsResultInterpreter = physicsResultInterpreter;
        this.random = random;
    }

    /**
     * Simulates a complete point. Evolves weather before the point begins, then runs the
     * serve–rally loop until an IssueDuPoint is produced.
     *
     * @param input all context needed for the point (players, surface, weather, score)
     * @return PointStart event, PointEnd event, and evolved weather state for the next point
     */
    public PointResult simulate(PointInput input) throws IOException {
        WeatherState evolvedWeather = evolveWeather(
                input.currentWeather(), input.ventMoyen(),
                input.surfaceDryingRate(), input.precipitationActive());

        PhysicsEnvironment env = PhysicsEnvironment.newBuilder()
                .setSurface(input.surface())
                .setSurfaceWetness(evolvedWeather.surfaceWetness())
                .setWind(evolvedWeather.wind())
                .setCoefficients(input.surfaceCoefficients())
                .build();

        int shotIndex = 0;

        // Serve phase: try first serve, then second serve on fault
        for (int serveNumber = 1; serveNumber <= 2; serveNumber++) {
            ShotEffect serveEffect = (serveNumber == 1)
                    ? ShotEffect.SHOT_EFFECT_FLAT
                    : ShotEffect.SHOT_EFFECT_TOPSPIN;

            HitQualityResult serveHq = hitQualityService.compute(
                    ReachResult.REACH_RESULT_COMFORTABLE,
                    input.serverFatigue(),
                    input.server(),
                    ShotType.SHOT_TYPE_SERVE,
                    input.pointContext());

            CourtPosition serveTarget = serveTarget(
                    input.pointStart().getCourtSide(), input.serverPosition());
            ShotSpec serveSpec = shotSpecBuilder.build(
                    serveHq, input.server(), input.serverPosition(),
                    ShotType.SHOT_TYPE_SERVE, serveEffect,
                    serveTarget, input.matchId(), input.pointIndex(), shotIndex++);

            PhysicsSimulationResult servePhysics =
                    physicsCoreClient.simulateBatch(env, List.of(serveSpec)).getFirst();

            if (isServeFault(servePhysics, input.serverPosition())) {
                if (serveNumber == 2) {
                    return new PointResult(input.pointStart(), pointEnd(
                            IssueDuPoint.ISSUE_DU_POINT_DOUBLE_FAUTE,
                            input.receiver().getPlayerId(),
                            input.pointStart().getScore(), 0), evolvedWeather);
                }
                continue;
            }

            // Serve landed in — check whether the receiver can reach it
            PhysicsResultInterpreter.Interpretation serveInterp = physicsResultInterpreter.interpret(
                    servePhysics, input.receiver(), input.receiverPosition(),
                    input.receiverFatigue(), serveHq.hitQuality());

            if (serveInterp.issueDuPoint() == IssueDuPoint.ISSUE_DU_POINT_COUP_GAGNANT) {
                // Receiver could not reach the serve → ACE
                return new PointResult(input.pointStart(), pointEnd(
                        IssueDuPoint.ISSUE_DU_POINT_ACE,
                        input.server().getPlayerId(),
                        input.pointStart().getScore(), 1), evolvedWeather);
            }

            if (serveInterp.issueDuPoint() != null) {
                // Serve cleared net and landed in, but caused a fault classification (edge case)
                return new PointResult(input.pointStart(), pointEnd(
                        serveInterp.issueDuPoint(),
                        input.receiver().getPlayerId(),
                        input.pointStart().getScore(), 1), evolvedWeather);
            }

            // Receiver can return — enter the rally
            CourtPosition receiverAtBall = servePhysics.getLandingPosition() != null
                    ? servePhysics.getLandingPosition()
                    : input.receiverPosition();

            return simulateRally(input, env, evolvedWeather, shotIndex,
                    input.receiver(), input.server(),
                    receiverAtBall, input.serverPosition(),
                    input.receiverFatigue(), input.serverFatigue(),
                    serveInterp.reachResult(), 1,
                    servePhysics, input.receiverBrain(), input.serverBrain());
        }

        // Unreachable: the for-loop always returns on serveNumber == 2
        return new PointResult(input.pointStart(), pointEnd(
                IssueDuPoint.ISSUE_DU_POINT_DOUBLE_FAUTE,
                input.receiver().getPlayerId(),
                input.pointStart().getScore(), 0), evolvedWeather);
    }

    private PointResult simulateRally(
            PointInput input,
            PhysicsEnvironment env,
            WeatherState evolvedWeather,
            int shotIndex,
            TennisPlayerSnapshot attacker,
            TennisPlayerSnapshot defender,
            CourtPosition attackerPos,
            CourtPosition defenderPos,
            float attackerFatigue,
            float defenderFatigue,
            ReachResult currentReach,
            int rallyLength,
            PhysicsSimulationResult incomingResult,
            PlayerBrain attackerBrain,
            PlayerBrain defenderBrain) throws IOException {

        WeatherSnapshot weatherSnapshot = new WeatherSnapshot(
                evolvedWeather.wind().getDirection(),
                evolvedWeather.wind().getIntensity(),
                evolvedWeather.surfaceWetness());

        for (int i = 0; i < MAX_RALLY_SHOTS; i++) {
            TacticalState attackerContact = tickThroughFlight(
                    incomingResult, attacker, defender, attackerPos, defenderPos,
                    attackerBrain, defenderBrain,
                    input.pointStart().getScore(), weatherSnapshot);

            ShotType shotType = attackerContact.preparedShot();
            CourtPosition target = attackerContact.approximateTargetZone();

            HitQualityResult hq = hitQualityService.compute(
                    currentReach, attackerFatigue, attacker, shotType, input.pointContext());

            ShotEffect shotEffect = pickShotEffect(hq.effectiveShotIntent());

            ShotSpec spec = shotSpecBuilder.build(
                    hq, attacker, attackerPos, shotType, shotEffect,
                    target, input.matchId(), input.pointIndex(), shotIndex++);

            PhysicsSimulationResult physResult =
                    physicsCoreClient.simulateBatch(env, List.of(spec)).getFirst();

            PhysicsResultInterpreter.Interpretation interp = physicsResultInterpreter.interpret(
                    physResult, defender, defenderPos, defenderFatigue, hq.hitQuality());

            rallyLength++;

            if (interp.issueDuPoint() != null) {
                String winnerId = (interp.issueDuPoint() == IssueDuPoint.ISSUE_DU_POINT_COUP_GAGNANT)
                        ? attacker.getPlayerId()
                        : defender.getPlayerId();
                return new PointResult(input.pointStart(), pointEnd(
                        interp.issueDuPoint(), winnerId,
                        input.pointStart().getScore(), rallyLength), evolvedWeather);
            }

            // Swap attacker and defender for next shot.
            // Attacker returns to their baseline center; defender (now attacker) stays on their side.
            float attackerBaseline = attackerPos.getY() >= 0 ? BASELINE_Y : -BASELINE_Y;
            CourtPosition nextAttackerPos = defenderPos;
            CourtPosition nextDefenderPos = CourtPosition.newBuilder()
                    .setX(0).setY(attackerBaseline).setZ(0).build();

            TennisPlayerSnapshot nextAttacker = defender;
            TennisPlayerSnapshot nextDefender = attacker;
            float nextAttackerFatigue = defenderFatigue;
            float nextDefenderFatigue = attackerFatigue;
            PlayerBrain nextAttackerBrain = defenderBrain;
            PlayerBrain nextDefenderBrain = attackerBrain;

            attacker = nextAttacker;
            defender = nextDefender;
            attackerPos = nextAttackerPos;
            defenderPos = nextDefenderPos;
            attackerFatigue = nextAttackerFatigue;
            defenderFatigue = nextDefenderFatigue;
            attackerBrain = nextAttackerBrain;
            defenderBrain = nextDefenderBrain;
            currentReach = interp.reachResult();
            incomingResult = physResult;
        }

        // Safety fallback: max rally length exceeded
        return new PointResult(input.pointStart(), pointEnd(
                IssueDuPoint.ISSUE_DU_POINT_COUP_GAGNANT,
                attacker.getPlayerId(),
                input.pointStart().getScore(), MAX_RALLY_SHOTS), evolvedWeather);
    }

    /**
     * Advances tick-by-tick through the incoming ball's flight segments, calling both brains
     * each tick. Returns the attacker's TacticalState at the contact (last) tick, which is
     * used to construct the next ShotSpec.
     *
     * Brains are called in fixed lexicographic order by playerId each tick for determinism.
     */
    private TacticalState tickThroughFlight(
            PhysicsSimulationResult incomingResult,
            TennisPlayerSnapshot attacker,
            TennisPlayerSnapshot defender,
            CourtPosition attackerPos,
            CourtPosition defenderPos,
            PlayerBrain attackerBrain,
            PlayerBrain defenderBrain,
            ScoreSnapshot score,
            WeatherSnapshot weather) {

        List<BallFlightSegment> segments = incomingResult.getBallFlightSegmentsList();
        List<CourtPosition> positions;
        if (segments.isEmpty()) {
            CourtPosition fallback = incomingResult.hasLandingPosition()
                    ? incomingResult.getLandingPosition() : attackerPos;
            positions = List.of(fallback);
        } else {
            positions = segments.stream().map(BallFlightSegment::getTo).toList();
        }

        boolean attackerFirst = attacker.getPlayerId().compareTo(defender.getPlayerId()) <= 0;
        TacticalState attackerTactical = null;

        for (int t = 0; t < positions.size(); t++) {
            CourtPosition ball = positions.get(t);
            GameTickState attackerState = new GameTickState(
                    ball, attackerPos, defenderPos, score, weather, t);
            GameTickState defenderState = new GameTickState(
                    ball, defenderPos, attackerPos, score, weather, t);
            ObservableOpponentState attackerSeesOpp = new ObservableOpponentState(
                    defenderPos, Vector3.getDefaultInstance(), ShotPreparation.NONE);
            ObservableOpponentState defenderSeesOpp = new ObservableOpponentState(
                    attackerPos, Vector3.getDefaultInstance(), ShotPreparation.NONE);

            if (attackerFirst) {
                attackerTactical = attackerBrain.tick(attackerState, attackerSeesOpp);
                defenderBrain.tick(defenderState, defenderSeesOpp);
            } else {
                defenderBrain.tick(defenderState, defenderSeesOpp);
                attackerTactical = attackerBrain.tick(attackerState, attackerSeesOpp);
            }
        }

        return attackerTactical;
    }

    /**
     * Evolves weather between points.
     * Wind drifts in small bounded increments around ventMoyen.
     * Wetness decays by surfaceDryingRate when precipitation is inactive.
     * Zero ventMoyen intensity produces no wind effect.
     */
    WeatherState evolveWeather(
            WeatherState current,
            WindVector ventMoyen,
            float surfaceDryingRate,
            boolean precipitationActive) {

        WindVector newWind;
        if (ventMoyen.getIntensity() <= 0.0f) {
            newWind = WindVector.newBuilder().setDirection(0).setIntensity(0).build();
        } else {
            float dirDelta = (random.nextFloat() * 2f - 1f) * WIND_DIRECTION_DELTA;
            float newDirection = current.wind().getDirection() + dirDelta;

            float intDelta = (random.nextFloat() * 2f - 1f)
                    * WIND_INTENSITY_DELTA_FRACTION * ventMoyen.getIntensity();
            float newIntensity = Math.clamp(
                    current.wind().getIntensity() + intDelta,
                    0.0f, ventMoyen.getIntensity() * 2.0f);

            newWind = WindVector.newBuilder()
                    .setDirection(newDirection)
                    .setIntensity(newIntensity)
                    .build();
        }

        float newWetness = precipitationActive
                ? current.surfaceWetness()
                : Math.max(0.0f, current.surfaceWetness() - surfaceDryingRate);

        return new WeatherState(newWind, newWetness);
    }

    /**
     * Returns true if the serve result constitutes a fault: ball did not clear the net,
     * or the landing position is outside the service zone (between net and service line,
     * on the opponent's side, within singles width).
     */
    boolean isServeFault(PhysicsSimulationResult result, CourtPosition serverPosition) {
        if (!result.getClearedNet()) return true;
        if (!result.hasLandingPosition()) return true;
        CourtPosition landing = result.getLandingPosition();

        // Determine service zone bounds: from net (y=0) to service line on opponent's side
        float serverSideSign = serverPosition.getY() >= 0 ? 1.0f : -1.0f;
        float zoneFarY = -serverSideSign * SERVICE_LINE_Y; // service line on opponent's side
        float lo = Math.min(0.0f, zoneFarY);
        float hi = Math.max(0.0f, zoneFarY);

        return landing.getY() < lo || landing.getY() > hi
                || Math.abs(landing.getX()) > COURT_HALF_WIDTH;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Computes the serve target centre for a given court side.
     * The target x sign is relative to the server's y-side so that DEUCE/AD mapping
     * is consistent regardless of which baseline the server occupies.
     */
    private CourtPosition serveTarget(CourtSide courtSide, CourtPosition serverPosition) {
        float serverSideSign = serverPosition.getY() >= 0 ? 1.0f : -1.0f;
        // DEUCE: target x is +serverSideSign * SERVICE_BOX_CENTER_X
        // AD:    target x is −serverSideSign * SERVICE_BOX_CENTER_X
        float targetX = (courtSide == CourtSide.COURT_SIDE_DEUCE ? serverSideSign : -serverSideSign)
                * SERVICE_BOX_CENTER_X;
        float targetY = -serverSideSign * (SERVICE_LINE_Y / 2.0f); // midpoint of service zone
        return CourtPosition.newBuilder().setX(targetX).setY(targetY).setZ(0).build();
    }

    private ShotEffect pickShotEffect(ShotIntent effectiveIntent) {
        return effectiveIntent == ShotIntent.SHOT_INTENT_DEFENSIVE
                ? ShotEffect.SHOT_EFFECT_SLICE
                : ShotEffect.SHOT_EFFECT_TOPSPIN;
    }

    private PointEnd pointEnd(
            IssueDuPoint issue, String winnerId, ScoreSnapshot score, int rallyLength) {
        return PointEnd.newBuilder()
                .setIssueDuPoint(issue)
                .setWinnerId(winnerId)
                .setScore(score)
                .setRallyLength(rallyLength)
                .build();
    }
}
