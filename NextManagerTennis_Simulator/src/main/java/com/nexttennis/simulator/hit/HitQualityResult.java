package com.nexttennis.simulator.hit;

import com.nexttennis.simulator.proto.ShotIntent;

public record HitQualityResult(float hitQuality, ShotIntent effectiveShotIntent) {}
