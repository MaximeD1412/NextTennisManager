package com.nexttennis.simulator.brain;

public interface PlayerBrain {
    TacticalState tick(GameTickState state, ObservableOpponentState opponent);
}
