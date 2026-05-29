# Prototype Three.js + Rust physics

Question: can the 3D replay consume the Rust physics engine instead of a TypeScript mock?

Answer: yes. The frontend now loads `public/physics_debug_wasm.wasm`, generated from the
`physics-debug-wasm` crate. The frontend sends the example shot from `src/shotExamples.ts` to the
WASM wrapper, the wrapper calls `physics_core::physics::simulate_shot`, converts the returned
`BallFlightSegment` values into regular visual samples, and exposes them as flat `Float32Array`
data for Three.js.

The 3D scene also reads its physical dimensions from Rust/WASM:

- `BALL_RADIUS_M` drives the rendered ball radius.
- `NET_CENTER_HEIGHT_M`, `NET_POST_HEIGHT_M`, and `NET_POST_X_M` drive the net mesh and top cord.
- `SINGLES_HALF_WIDTH_M` and `SERVICE_BOX_DEPTH_M` drive the playable court lines.
- `spin_rpm` and `spin_axis` from each `BallFlightSegment` drive the small seam/dot marker on
  the ball, so the replay can show the spin direction.

Run from `NextManagerTennis_WebApp/frontend`:

```bash
npm run dev
```

`npm run dev` first rebuilds the WASM module, copies it into `public/`, then starts Vite.
The generated `public/physics_debug_wasm.wasm` file is ignored because it is a build artifact.

Current limitation: this is still a fixed debug shot. The next useful step is to pass the real
`ShotSpec` and `PhysicsEnvironment` from the match simulation into the WASM wrapper, or to consume
the same protobuf payload that the Java/Rust boundary already uses.

Example shot:

```ts
export const exampleTopspinForehand = {
  name: "Coup droit lifte croise",
  environment: { surface: SurfaceCode.hard, surfaceWetness: 0 },
  shot: {
    contactPosition: { x: -2.55, y: -10.35, z: 0.95 },
    velocity: { x: 4.7, y: 22.8, z: 3.8 },
    spinRpm: 2600,
    spinAxis: { x: -1, y: 0, z: 0 },
  },
};
```

Coordinate reminder: `x` is left/right, `y` is court length with the net at `0`, and `z` is height.
For a shot travelling from the near baseline to the far side, use a positive `velocity.y`.
