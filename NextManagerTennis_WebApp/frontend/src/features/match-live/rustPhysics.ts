import type {
  DebugTrajectory,
  ReplayEvent,
  SceneDimensions,
  TrajectorySample,
} from "./trajectory";
import {
  encodeShotSimulationSpec,
  exampleTopspinForehand,
  type ShotSimulationInput,
} from "./shotExamples";

type RustPhysicsExports = WebAssembly.Exports & {
  memory: WebAssembly.Memory;
  alloc_f32(len: number): number;
  free_f32(ptr: number, len: number): void;
  debug_trajectory_sample_width(): number;
  debug_trajectory_event_width(): number;
  debug_trajectory_spec_width(): number;
  debug_scene_dimension_width(): number;
  debug_scene_dimensions_write(ptr: number): number;
  debug_trajectory_sample_count(sampleMs: number): number;
  debug_trajectory_sample_count_for_spec(specPtr: number): number;
  debug_trajectory_event_count(): number;
  debug_trajectory_event_count_for_spec(specPtr: number): number;
  debug_trajectory_write_samples(ptr: number, sampleMs: number): number;
  debug_trajectory_write_samples_for_spec(ptr: number, specPtr: number): number;
  debug_trajectory_write_events(ptr: number): number;
  debug_trajectory_write_events_for_spec(ptr: number, specPtr: number): number;
};

const WASM_URL = "/physics_debug_wasm.wasm";
const EVENT_KIND_BY_CODE: Record<number, ReplayEvent["kind"]> = {
  1: "net",
  2: "bounce",
};
const SCENE_DIMENSION_KEYS = [
  "baselineYAbsM",
  "singlesHalfWidthM",
  "doublesHalfWidthM",
  "serviceLineYAbsM",
  "netPostXAbsM",
  "netCenterHeightM",
  "netPostHeightM",
  "ballRadiusM",
] as const satisfies readonly (keyof SceneDimensions)[];

let physicsPromise: Promise<RustPhysicsExports> | undefined;

export async function buildRustDebugTrajectory(
  sampleMs = 20,
  input: ShotSimulationInput = exampleTopspinForehand,
): Promise<DebugTrajectory> {
  const physics = await loadRustPhysics();
  const specWidth = physics.debug_trajectory_spec_width();
  const specPtr = physics.alloc_f32(specWidth);

  try {
    const encodedSpec = encodeShotSimulationSpec(input, sampleMs);

    if (encodedSpec.length !== specWidth) {
      throw new Error(`Rust physics spec width mismatch: ${encodedSpec.length} !== ${specWidth}`);
    }

    new Float32Array(physics.memory.buffer, specPtr, specWidth).set(encodedSpec);

    const sampleWidth = physics.debug_trajectory_sample_width();
    const sampleCount = physics.debug_trajectory_sample_count_for_spec(specPtr);
    const samples = readSamples(physics, sampleCount, sampleWidth, specPtr);
    const eventWidth = physics.debug_trajectory_event_width();
    const eventCount = physics.debug_trajectory_event_count_for_spec(specPtr);
    const events = readEvents(physics, eventCount, eventWidth, specPtr);
    const dimensions = readSceneDimensions(physics);

    return {
      sampleMs,
      shotName: input.name,
      dimensions,
      samples,
      events,
      source: "rust-wasm",
    };
  } finally {
    physics.free_f32(specPtr, specWidth);
  }
}

function readSceneDimensions(physics: RustPhysicsExports): SceneDimensions {
  const width = physics.debug_scene_dimension_width();
  const ptr = physics.alloc_f32(width);

  try {
    const written = physics.debug_scene_dimensions_write(ptr);
    const raw = new Float32Array(physics.memory.buffer, ptr, written).slice();

    if (written !== SCENE_DIMENSION_KEYS.length) {
      throw new Error(`Rust physics dimension width mismatch: ${written}`);
    }

    return SCENE_DIMENSION_KEYS.reduce((dimensions, key, index) => {
      dimensions[key] = raw[index];
      return dimensions;
    }, {} as Record<keyof SceneDimensions, number>) as SceneDimensions;
  } finally {
    physics.free_f32(ptr, width);
  }
}

async function loadRustPhysics(): Promise<RustPhysicsExports> {
  physicsPromise ??= instantiateRustPhysics();
  return physicsPromise;
}

async function instantiateRustPhysics(): Promise<RustPhysicsExports> {
  const response = await fetch(WASM_URL);

  if (!response.ok) {
    throw new Error(`Rust physics WASM not found: ${response.status}`);
  }

  const bytes = await response.arrayBuffer();
  const result = await WebAssembly.instantiate(bytes, {});
  return result.instance.exports as RustPhysicsExports;
}

function readSamples(
  physics: RustPhysicsExports,
  sampleCount: number,
  sampleWidth: number,
  specPtr: number,
): TrajectorySample[] {
  const len = sampleCount * sampleWidth;
  const ptr = physics.alloc_f32(len);

  try {
    const written = physics.debug_trajectory_write_samples_for_spec(ptr, specPtr);
    const raw = new Float32Array(physics.memory.buffer, ptr, written * sampleWidth).slice();
    const samples: TrajectorySample[] = [];

    for (let index = 0; index < written; index += 1) {
      const offset = index * sampleWidth;
      samples.push({
        timeMs: raw[offset],
        position: {
          x: raw[offset + 1],
          y: raw[offset + 2],
          z: raw[offset + 3],
        },
        velocity: {
          x: raw[offset + 4],
          y: raw[offset + 5],
          z: raw[offset + 6],
        },
        spinRpm: raw[offset + 7],
        spinAxis: {
          x: raw[offset + 8],
          y: raw[offset + 9],
          z: raw[offset + 10],
        },
      });
    }

    return samples;
  } finally {
    physics.free_f32(ptr, len);
  }
}

function readEvents(
  physics: RustPhysicsExports,
  eventCount: number,
  eventWidth: number,
  specPtr: number,
): ReplayEvent[] {
  if (eventCount === 0) {
    return [];
  }

  const len = eventCount * eventWidth;
  const ptr = physics.alloc_f32(len);

  try {
    const written = physics.debug_trajectory_write_events_for_spec(ptr, specPtr);
    const raw = new Float32Array(physics.memory.buffer, ptr, written * eventWidth).slice();
    const events: ReplayEvent[] = [];

    for (let index = 0; index < written; index += 1) {
      const offset = index * eventWidth;
      const kind = EVENT_KIND_BY_CODE[raw[offset]];

      if (!kind) {
        continue;
      }

      events.push({
        kind,
        timeMs: raw[offset + 1],
        position: {
          x: raw[offset + 2],
          y: raw[offset + 3],
          z: raw[offset + 4],
        },
      });
    }

    return events;
  } finally {
    physics.free_f32(ptr, len);
  }
}
