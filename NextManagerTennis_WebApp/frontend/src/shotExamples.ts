export const SurfaceCode = {
  clay: 1,
  grass: 2,
  hard: 3,
  indoorHard: 4,
} as const;

export type ShotSimulationInput = {
  name: string;
  environment: {
    surface: (typeof SurfaceCode)[keyof typeof SurfaceCode];
    surfaceWetness: number;
    drag: number;
    magnus: number;
    restitution: number;
    friction: number;
    wetnessBounceScale: number;
    wetnessFrictionScale: number;
  };
  shot: {
    contactPosition: {
      x: number;
      y: number;
      z: number;
    };
    velocity: {
      x: number;
      y: number;
      z: number;
    };
    spinRpm: number;
    spinAxis: {
      x: number;
      y: number;
      z: number;
    };
    pointIndex: number;
    shotIndex: number;
  };
};

export const exampleTopspinForehand: ShotSimulationInput = {
  name: "Coup droit lifte croise",
  environment: {
    surface: SurfaceCode.hard,
    surfaceWetness: 0,
    drag: 0.47,
    magnus: 0.1,
    restitution: 0.75,
    friction: 0.6,
    wetnessBounceScale: 1,
    wetnessFrictionScale: 1,
  },
  shot: {
    contactPosition: {
      x: -2.55,
      y: -10.35,
      z: 0.95,
    },
    velocity: {
      x: 4.7,
      y: 22.8,
      z: 3.8,
    },
    spinRpm: 2600,
    spinAxis: {
      x: -1,
      y: 0,
      z: 0,
    },
    pointIndex: 1,
    shotIndex: 3,
  },
};

export function encodeShotSimulationSpec(
  input: ShotSimulationInput,
  sampleMs: number,
): Float32Array {
  return new Float32Array([
    sampleMs,
    input.environment.surface,
    input.environment.surfaceWetness,
    input.environment.drag,
    input.environment.magnus,
    input.environment.restitution,
    input.environment.friction,
    input.environment.wetnessBounceScale,
    input.environment.wetnessFrictionScale,
    input.shot.contactPosition.x,
    input.shot.contactPosition.y,
    input.shot.contactPosition.z,
    input.shot.velocity.x,
    input.shot.velocity.y,
    input.shot.velocity.z,
    input.shot.spinRpm,
    input.shot.spinAxis.x,
    input.shot.spinAxis.y,
    input.shot.spinAxis.z,
    input.shot.pointIndex,
    input.shot.shotIndex,
  ]);
}
