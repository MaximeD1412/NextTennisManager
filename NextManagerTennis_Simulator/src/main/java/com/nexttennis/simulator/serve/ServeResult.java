package com.nexttennis.simulator.serve;

import com.nexttennis.simulator.proto.Fault;
import com.nexttennis.simulator.proto.Shot;

public sealed interface ServeResult {
    record ServeIn(Shot shot) implements ServeResult {}
    record ServeFault(Fault fault) implements ServeResult {}
}
