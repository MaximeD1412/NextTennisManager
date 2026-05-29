export type CourtPosition = {
  x: number;
  y: number;
  z: number;
};

export type CourtVelocity = {
  x: number;
  y: number;
  z: number;
};

export type TrajectorySample = {
  timeMs: number;
  position: CourtPosition;
  velocity: CourtVelocity;
  spinRpm: number;
  spinAxis: CourtVelocity;
};

export type ReplayEvent = {
  kind: "net" | "bounce";
  timeMs: number;
  position: CourtPosition;
};

export type SceneDimensions = {
  baselineYAbsM: number;
  singlesHalfWidthM: number;
  doublesHalfWidthM: number;
  serviceLineYAbsM: number;
  netPostXAbsM: number;
  netCenterHeightM: number;
  netPostHeightM: number;
  ballRadiusM: number;
};

export type DebugTrajectory = {
  sampleMs: number;
  shotName: string;
  dimensions: SceneDimensions;
  samples: TrajectorySample[];
  events: ReplayEvent[];
  source: "rust-wasm" | "typescript-fallback";
};

export const fallbackSceneDimensions: SceneDimensions = {
  baselineYAbsM: 11.89,
  singlesHalfWidthM: 4.115,
  doublesHalfWidthM: 5.485,
  serviceLineYAbsM: 6.4,
  netPostXAbsM: 5.029,
  netCenterHeightM: 0.914,
  netPostHeightM: 1.07,
  ballRadiusM: 0.0335,
};

const GRAVITY = 9.81;

type SegmentInput = {
  startTimeMs: number;
  durationMs: number;
  startPosition: CourtPosition;
  startVelocity: CourtVelocity;
  spinRpm: number;
  spinAxis: CourtVelocity;
};

function positionAt(segment: SegmentInput, elapsedSeconds: number): CourtPosition {
  return {
    x: segment.startPosition.x + segment.startVelocity.x * elapsedSeconds,
    y: segment.startPosition.y + segment.startVelocity.y * elapsedSeconds,
    z:
      segment.startPosition.z +
      segment.startVelocity.z * elapsedSeconds -
      0.5 * GRAVITY * elapsedSeconds * elapsedSeconds,
  };
}

function velocityAt(segment: SegmentInput, elapsedSeconds: number): CourtVelocity {
  return {
    x: segment.startVelocity.x,
    y: segment.startVelocity.y,
    z: segment.startVelocity.z - GRAVITY * elapsedSeconds,
  };
}

function pushSegmentSamples(
  samples: TrajectorySample[],
  segment: SegmentInput,
  sampleMs: number,
  includeStart: boolean,
) {
  const firstStep = includeStart ? 0 : sampleMs;

  for (let t = firstStep; t <= segment.durationMs + 0.001; t += sampleMs) {
    const seconds = t / 1000;
    samples.push({
      timeMs: segment.startTimeMs + t,
      position: positionAt(segment, seconds),
      velocity: velocityAt(segment, seconds),
      spinRpm: segment.spinRpm,
      spinAxis: segment.spinAxis,
    });
  }
}

export function buildDebugTrajectory(sampleMs = 20): DebugTrajectory {
  const firstFlight: SegmentInput = {
    startTimeMs: 0,
    durationMs: 860,
    startPosition: { x: -2.65, y: -9.45, z: 1.18 },
    startVelocity: { x: 4.9, y: 20.4, z: 2.75 },
    spinRpm: 1800,
    spinAxis: { x: -1, y: 0, z: 0 },
  };

  const bouncePosition = positionAt(firstFlight, firstFlight.durationMs / 1000);
  const bounceVelocity = velocityAt(firstFlight, firstFlight.durationMs / 1000);

  const secondFlight: SegmentInput = {
    startTimeMs: firstFlight.durationMs,
    durationMs: 720,
    startPosition: { ...bouncePosition, z: 0 },
    startVelocity: {
      x: bounceVelocity.x * 0.42,
      y: bounceVelocity.y * 0.48,
      z: Math.abs(bounceVelocity.z) * 0.58,
    },
    spinRpm: 900,
    spinAxis: firstFlight.spinAxis,
  };

  const samples: TrajectorySample[] = [];
  pushSegmentSamples(samples, firstFlight, sampleMs, true);
  pushSegmentSamples(samples, secondFlight, sampleMs, false);

  const netSample = samples.reduce((closest, sample) => {
    return Math.abs(sample.position.y) < Math.abs(closest.position.y) ? sample : closest;
  }, samples[0]);

  return {
    sampleMs,
    shotName: "Coup debug TypeScript",
    dimensions: fallbackSceneDimensions,
    source: "typescript-fallback",
    samples,
    events: [
      {
        kind: "net",
        timeMs: netSample.timeMs,
        position: netSample.position,
      },
      {
        kind: "bounce",
        timeMs: firstFlight.durationMs,
        position: { ...bouncePosition, z: 0 },
      },
    ],
  };
}

export function sampleAt(samples: TrajectorySample[], timeMs: number): TrajectorySample {
  if (timeMs <= samples[0].timeMs) {
    return samples[0];
  }

  const last = samples[samples.length - 1];
  if (timeMs >= last.timeMs) {
    return last;
  }

  const nextIndex = samples.findIndex((sample) => sample.timeMs >= timeMs);
  const next = samples[nextIndex];
  const previous = samples[nextIndex - 1];
  const span = next.timeMs - previous.timeMs;
  const factor = span === 0 ? 0 : (timeMs - previous.timeMs) / span;

  return {
    timeMs,
    position: {
      x: previous.position.x + (next.position.x - previous.position.x) * factor,
      y: previous.position.y + (next.position.y - previous.position.y) * factor,
      z: previous.position.z + (next.position.z - previous.position.z) * factor,
    },
    velocity: {
      x: previous.velocity.x + (next.velocity.x - previous.velocity.x) * factor,
      y: previous.velocity.y + (next.velocity.y - previous.velocity.y) * factor,
      z: previous.velocity.z + (next.velocity.z - previous.velocity.z) * factor,
    },
    spinRpm: previous.spinRpm + (next.spinRpm - previous.spinRpm) * factor,
    spinAxis: {
      x: previous.spinAxis.x + (next.spinAxis.x - previous.spinAxis.x) * factor,
      y: previous.spinAxis.y + (next.spinAxis.y - previous.spinAxis.y) * factor,
      z: previous.spinAxis.z + (next.spinAxis.z - previous.spinAxis.z) * factor,
    },
  };
}
