package com.nexttennis.simulator.brain;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.ScoreSnapshot;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.Vector3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerBrainTypesTest {

    // ── WeatherSnapshot ───────────────────────────────────────────────────────

    @Test
    void weatherSnapshotStoresAllFields() {
        WeatherSnapshot ws = new WeatherSnapshot(1.2f, 3.4f, 0.7f);

        assertThat(ws.windDirection()).isEqualTo(1.2f);
        assertThat(ws.windIntensity()).isEqualTo(3.4f);
        assertThat(ws.surfaceWetness()).isEqualTo(0.7f);
    }

    @Test
    void weatherSnapshotCalmFactoryProducesZeroFields() {
        WeatherSnapshot calm = WeatherSnapshot.calm();

        assertThat(calm.windDirection()).isEqualTo(0f);
        assertThat(calm.windIntensity()).isEqualTo(0f);
        assertThat(calm.surfaceWetness()).isEqualTo(0f);
    }

    // ── GameTickState ─────────────────────────────────────────────────────────

    @Test
    void gameTickStateStoresAllFields() {
        CourtPosition ball = pos(0f, 3f, 1.2f);
        CourtPosition self = pos(0f, 10f, 0f);
        CourtPosition opponent = pos(1f, -10f, 0f);
        ScoreSnapshot score = ScoreSnapshot.newBuilder()
                .setCurrentPointsPlayer1(1)
                .setCurrentPointsPlayer2(0)
                .build();
        WeatherSnapshot weather = new WeatherSnapshot(0.5f, 2f, 0.3f);

        GameTickState state = new GameTickState(ball, self, opponent, score, weather, 7);

        assertThat(state.ballPosition()).isEqualTo(ball);
        assertThat(state.selfPosition()).isEqualTo(self);
        assertThat(state.opponentPosition()).isEqualTo(opponent);
        assertThat(state.score()).isEqualTo(score);
        assertThat(state.weather()).isEqualTo(weather);
        assertThat(state.tickIndex()).isEqualTo(7);
    }

    // ── ObservableOpponentState ───────────────────────────────────────────────

    @Test
    void observableOpponentStateStoresAllFields() {
        CourtPosition position = pos(2f, -9f, 0f);
        Vector3 velocity = vec(1.5f, -3f, 0f);

        ObservableOpponentState obs = new ObservableOpponentState(
                position, velocity, ShotPreparation.FOREHAND);

        assertThat(obs.position()).isEqualTo(position);
        assertThat(obs.velocityVector()).isEqualTo(velocity);
        assertThat(obs.visiblePreparation()).isEqualTo(ShotPreparation.FOREHAND);
    }

    @Test
    void observableOpponentStateAcceptsAllShotPreparationValues() {
        CourtPosition pos = pos(0f, 0f, 0f);
        Vector3 vel = vec(0f, 0f, 0f);

        for (ShotPreparation prep : ShotPreparation.values()) {
            ObservableOpponentState obs = new ObservableOpponentState(pos, vel, prep);
            assertThat(obs.visiblePreparation()).isEqualTo(prep);
        }
    }

    // ── TacticalState ─────────────────────────────────────────────────────────

    @Test
    void tacticalStateStoresAllFields() {
        CourtPosition movementTarget = pos(-2f, 10f, 0f);
        CourtPosition targetZone = pos(3f, -8f, 0f);

        TacticalState tactical = new TacticalState(
                movementTarget,
                ShotType.SHOT_TYPE_FOREHAND,
                ShotIntent.SHOT_INTENT_AGGRESSIVE,
                targetZone,
                0.8f);

        assertThat(tactical.movementTarget()).isEqualTo(movementTarget);
        assertThat(tactical.preparedShot()).isEqualTo(ShotType.SHOT_TYPE_FOREHAND);
        assertThat(tactical.shotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_AGGRESSIVE);
        assertThat(tactical.approximateTargetZone()).isEqualTo(targetZone);
        assertThat(tactical.riskLevel()).isEqualTo(0.8f);
    }

    @Test
    void tacticalStateRiskLevelBoundsAreRespectedByCallers() {
        TacticalState low = new TacticalState(
                pos(0f, 0f, 0f), ShotType.SHOT_TYPE_BACKHAND,
                ShotIntent.SHOT_INTENT_DEFENSIVE, pos(0f, 0f, 0f), 0.0f);
        TacticalState high = new TacticalState(
                pos(0f, 0f, 0f), ShotType.SHOT_TYPE_BACKHAND,
                ShotIntent.SHOT_INTENT_DEFENSIVE, pos(0f, 0f, 0f), 1.0f);

        assertThat(low.riskLevel()).isEqualTo(0.0f);
        assertThat(high.riskLevel()).isEqualTo(1.0f);
    }

    // ── PlayerBrain interface ─────────────────────────────────────────────────

    @Test
    void playerBrainInterfaceIsCallableViaLambda() {
        TacticalState expected = new TacticalState(
                pos(0f, 9f, 0f),
                ShotType.SHOT_TYPE_FOREHAND,
                ShotIntent.SHOT_INTENT_NEUTRAL,
                pos(1f, -8f, 0f),
                0.5f);

        PlayerBrain brain = (state, opponent) -> expected;

        GameTickState state = new GameTickState(
                pos(0f, 0f, 1f), pos(0f, 10f, 0f), pos(0f, -10f, 0f),
                ScoreSnapshot.getDefaultInstance(), WeatherSnapshot.calm(), 0);
        ObservableOpponentState opponent = new ObservableOpponentState(
                pos(0f, -10f, 0f), vec(0f, 0f, 0f), ShotPreparation.NONE);

        assertThat(brain.tick(state, opponent)).isEqualTo(expected);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static CourtPosition pos(float x, float y, float z) {
        return CourtPosition.newBuilder().setX(x).setY(y).setZ(z).build();
    }

    private static Vector3 vec(float x, float y, float z) {
        return Vector3.newBuilder().setX(x).setY(y).setZ(z).build();
    }
}
