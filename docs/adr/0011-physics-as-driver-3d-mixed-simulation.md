# ADR-0011 — Physics as driver for point resolution and 3D court model

## Status
Accepted

## Context

When designing the simulation engine, two approaches were considered for resolving point outcomes:

1. **Attributs-as-driver (probabilistic)** — Attributs serve as weights in a direct probability calculation; the winner of a point is drawn from a distribution without modeling ball physics. Simple to implement; does not produce physical stats (distance covered, ball speed) and cannot support visual replay.
2. **Physics-as-driver** — the court is modeled geometrically; ball trajectories are computed in 3D; whether a player can reach the ball is determined by comparing AvailableTime vs RequiredTime. Attributs feed into the physical parameters (movement speed, reaction delay, shot precision) rather than directly into a probability formula.

The 3D vs 2D question was also considered. 2D was ruled out because the replay format (`BALL_FLIGHT_SEGMENT` with `startZ/endZ/peakHeight`, see architecture doc section 19) is already defined in 3D — starting in 2D and adding z later would be a rewrite, not an extension.

A reach-only termination model (points end only when a player cannot reach the ball) was considered and rejected: in real tennis, the majority of points end on faults and execution errors, not on unreachable winners. Reach-only makes high-endurance players disproportionately dominant and removes the skill-execution dimension from the simulation.

The first version of this ADR mentioned an independent fault-probability check. That created an ambiguity with the physical model: a point could end because a separate probability fired, even if the generated trajectory would have been valid. This is no longer the model. Execution errors are represented as physical noise applied before trajectory integration.

## Decision

The simulator uses **physics as the primary driver** of point outcomes. The court is modeled in **3D** (x, y, z). Points are resolved through an Échange of alternating Coups.

A point can end through physical consequences:

- the opponent has `ReachResult = MISSED`;
- the integrated Trajectoire lands out;
- the integrated Trajectoire hits the filet and does not pass;
- a service lands outside the correct service box;
- a service touches the bande, passes, and becomes a let rather than a completed point.

HitQuality remains the execution-quality coefficient, but it does **not** trigger a separate `in/out` probability. HitQuality controls the physical noise applied to ShotExecution: launch direction, launch angle, speed, spin, timing, contact point and target dispersion. The Trajectoire then determines whether the Coup is valid.

Physical parameters are tunable via ConfigurationGlobale/MondeSetting; they are not part of the fixed simulator contract (which only fixes the Attribut interface and stable physical inputs, per ADR-0004). Runtime physics for candidate evaluation and Monte Carlo belongs to the Rust `physics-core` behind the Java simulator worker (ADR-0024). Python remains an offline calibration and analysis tool, not the official simulator runtime.

## Consequences

- The simulator must model court geometry, ball trajectory physics, player movement kinematics, net interaction, bounce physics, and shot execution quality. This is significantly more complex to implement than a probabilistic model.
- A Python calibration pipeline is required before beta testing to validate that simulated match statistics (ace rate, unforced error rate, rally length distribution, etc.) fall within plausible ranges. These experiments calibrate the Java/Rust simulator; they do not replace it.
- All future simulator versions must maintain the 3D model and physical trajectory termination as the baseline. A version that drops to 2D or reintroduces direct point-outcome probabilities would require a new simulator contract version (per ADR-0006).
- Physical parameters that do not belong to the fixed Attribut interface (ball speed ranges, spin effects per surface, execution-noise curves, bounce coefficients, net-tape coefficients) must be externalised to ConfigurationGlobale from the start.
- The physical integration hot path should target Rust rather than deepening the current landing-first Java placeholder.
