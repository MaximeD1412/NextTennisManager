package com.nexttennis.simulator.serve;

import com.nexttennis.simulator.proto.Fault;
import com.nexttennis.simulator.proto.ServeStart;
import com.nexttennis.simulator.proto.Shot;

public sealed interface ServeResult {
    ServeStart serveStart();

    record ServeIn(ServeStart serveStart, Shot shot) implements ServeResult {}
    record ServeFault(ServeStart serveStart, Fault fault) implements ServeResult {}
}
