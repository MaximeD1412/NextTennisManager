package com.nexttennis.simulator.brain;

import com.nexttennis.simulator.proto.CourtPosition;
import com.nexttennis.simulator.proto.PlayerHand;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;

import java.util.Random;

/**
 * Reproduces the original random shot-selection logic (pickShotType, pickTarget) so existing
 * behaviour is preserved while the rally loop delegates decisions to PlayerBrain.
 */
public class StubBrain implements PlayerBrain {

    private static final float COURT_HALF_WIDTH = 4.115f;

    private final TennisPlayerSnapshot player;
    private final Random random;

    public StubBrain(TennisPlayerSnapshot player, Random random) {
        this.player = player;
        this.random = random;
    }

    @Override
    public TacticalState tick(GameTickState state, ObservableOpponentState opponent) {
        return new TacticalState(
                state.selfPosition(),
                pickShotType(state.selfPosition()),
                ShotIntent.SHOT_INTENT_NEUTRAL,
                pickTarget(opponent.position()),
                0.5f);
    }

    private ShotType pickShotType(CourtPosition playerPos) {
        float relX = playerPos.getX();
        boolean facingPlusY = playerPos.getY() < 0;
        boolean rightHanded = player.getDominantHand() != PlayerHand.PLAYER_HAND_LEFT;
        float forehandXSign = facingPlusY
                ? (rightHanded ? 1.0f : -1.0f)
                : (rightHanded ? -1.0f : 1.0f);
        return (relX * forehandXSign >= 0)
                ? ShotType.SHOT_TYPE_FOREHAND
                : ShotType.SHOT_TYPE_BACKHAND;
    }

    private CourtPosition pickTarget(CourtPosition defenderPos) {
        float sideSign = defenderPos.getY() >= 0 ? 1.0f : -1.0f;
        float targetY = sideSign * (7.0f + random.nextFloat() * 3.0f);
        float targetX = (float) (random.nextGaussian() * 1.5f);
        targetX = Math.clamp(targetX, -(COURT_HALF_WIDTH - 0.5f), COURT_HALF_WIDTH - 0.5f);
        return CourtPosition.newBuilder().setX(targetX).setY(targetY).setZ(0).build();
    }
}
