# ADR-0011 — Physics as driver for point resolution, 3D court, mixed termination model

## Status
Accepted

## Context

When designing the simulation engine, two approaches were considered for resolving point outcomes:

1. **Attributs-as-driver (probabilistic)** — Attributs serve as weights in a direct probability calculation; the winner of a point is drawn from a distribution without modeling ball physics. Simple to implement; does not produce physical stats (distance covered, ball speed) and cannot support visual replay.
2. **Physics-as-driver** — the court is modeled geometrically; ball trajectories are computed in 3D; whether a player can reach the ball is determined by comparing AvailableTime vs RequiredTime. Attributs feed into the physical parameters (movement speed, reaction delay, shot precision) rather than directly into a probability formula.

The 3D vs 2D question was also considered. 2D was ruled out because the replay format (`BALL_FLIGHT_SEGMENT` with `startZ/endZ/peakHeight`, see architecture doc section 19) is already defined in 3D — starting in 2D and adding z later would be a rewrite, not an extension.

A reach-only termination model (points end only when a player cannot reach the ball) was considered and rejected: in real tennis, the majority of points end on unforced errors, not on unreachable winners. Reach-only makes high-endurance players disproportionately dominant and removes the skill-execution dimension from the simulation.

## Decision

The simulator uses **physics as the primary driver** of point outcomes. The court is modeled in **3D** (x, y, z). Points are resolved through a Rally of alternating Shots using a **mixed termination model**: a point ends either when a player's ReachResult is MISSED (opponent cannot reach the ball), or when an independent ErrorProbability check triggers (the player reached the ball but executed poorly). Both conditions operate simultaneously at every Shot. Physical parameters are tunable via ConfigurationGlobale/MondeSetting; they are not part of the fixed simulator contract (which only fixes the Attribut interface, per ADR-0004). Calibration of physical parameters against expected real-world distributions happens through a Python Monte Carlo pipeline post-implementation.

## Consequences

- The simulator must model court geometry, ball trajectory physics, player movement kinematics, and shot execution quality. This is significantly more complex to implement than a probabilistic model.
- A Python calibration pipeline is required before beta testing to validate that simulated match statistics (ace rate, unforced error rate, rally length distribution, etc.) fall within plausible ranges.
- All future simulator versions must maintain the 3D model and the mixed termination contract as the baseline. A version that drops to 2D or removes ErrorProbability would require a new simulator contract version (per ADR-0006).
- Physical parameters that do not belong to the fixed Attribut interface (ball speed ranges, spin effects per surface, ErrorProbability curves) must be externalised to ConfigurationGlobale from the start.
