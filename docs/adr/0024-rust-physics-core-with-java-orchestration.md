# ADR-0024 - Rust physics-core with Java simulator orchestration

## Status
Accepted

## Context

ADR-0011 establishes that point outcomes must emerge from 3D physics rather than direct `in/out` probabilities. The next design constraint is Monte Carlo: tactical AI and calibration will need to simulate many candidate Coups and many possible futures.

Three runtime shapes were considered:

1. **Full Java simulator** - fastest to integrate with the current worker, RabbitMQ, Protobuf and domain code. Good enough for a first deterministic implementation, but the physics loop may become the hot path once Monte Carlo is used heavily.
2. **Full Rust simulator** - strong performance and memory control, but it would force match orchestration, player state, fatigue, mental, tactical AI and event publishing into a lower-level runtime where the project has less existing infrastructure.
3. **Java orchestration with a Rust physics-core** - Java keeps the match/AI/domain responsibilities. Rust owns the dense, deterministic, batchable ball physics.

## Decision

Use **Java for simulator orchestration** and a **Rust `physics-core` for ball physics**.

Java remains responsible for:

- match, point, score and service order;
- TennisPlayer snapshots, Attributs, Fatigue, Moral, Rythme and tactics;
- tactical AI, candidate generation and candidate scoring;
- Monte Carlo orchestration at match/point/decision level;
- RabbitMQ job handling, Redis live state, replay/event publishing and final stats;
- converting domain concepts into physical inputs.

Rust is responsible for:

- integrating ball trajectories in 3D;
- gravity, drag, Magnus, wind and spin effects;
- net, posts, band/tape, service let and around-net detection;
- bounce physics by surface and wetness;
- compact physical events: `BallFlightSegment`, `Bounce`, `NetInteraction`, `ServeLet`;
- batch simulation for Monte Carlo candidate evaluation.

The boundary is intentionally narrow:

```text
Java:
  ShotPlan + player/context state
    -> ShotExecution / ShotSpec batch

Rust physics-core:
  simulate_batch(PhysicsEnvironment, ShotSpec[], seed)
    -> PhysicsSimulationResult[]

Java:
  score results, update rally state, emit events
```

Rust must not know high-level game concepts such as Attribut names, Mental, Tactique, Score, Tournoi, Ligue, Club or User. Java may pass already-derived physical coefficients, but the derivation from game domain to physical values belongs to Java.

## Integration Rule

The production boundary is **batch-first**. Java must not call Rust once per tiny physics step or once per candidate when a batch is available.

Allowed:

```text
simulate_batch(env, 500 shot_specs, seed)
simulate_batch(env, 10_000 monte_carlo_variants, seed)
```

Avoid:

```text
for each candidate:
  simulate_one(candidate)
```

The first integration should favor a long-lived Rust process or sidecar using Protobuf messages over a local transport. This keeps the Rust core independently testable and avoids early JNI/Panama complexity. If profiling proves the process boundary too expensive, the same contract can later be implemented by a native library without changing the simulator domain model.

## Determinism

Java owns the semantic seed for the Match/Point/Coup. Rust receives explicit seeds and must not use wall-clock time, global random state, unordered maps for outcome-critical iteration, or non-deterministic parallel reductions.

The determinism guarantee is scoped to a specific simulator version, physics-core version, compiler/build profile and target platform. Changing the Rust physics algorithm, compiler flags, SIMD strategy or floating-point behavior can change trajectories and requires a simulator version bump under ADR-0006.

Replay files store emitted physical events/results, not an instruction to recompute physics with an arbitrary future binary.

## Python Role

Python remains a calibration and analysis tool, not the runtime source of truth.

Use Python for:

- plotting trajectories;
- fitting drag/Magnus/bounce coefficients;
- analysing distributions;
- offline experiments and notebooks.

Do not use Python as the live match simulator or as the official Monte Carlo runtime.

## Consequences

- `shared/proto` must eventually generate Rust types as well as Java and TypeScript types.
- The physics contract must include `ShotSpec`, `PhysicsEnvironment`, batch request/response messages and physical result events.
- The Java simulator worker remains the deployment unit routed by RabbitMQ, but it includes or talks to a Rust physics-core.
- The first Java implementation can remain as a legacy placeholder only while the Rust core is being introduced. New physical behavior should target Rust, not deepen the landing-first Java model.
- Golden tests must cover the boundary: same seed + same batch input + same physics-core version = same result.
