# ADR-0006 — Versioned simulator workers routed via RabbitMQ

## Status
Accepted

## Context
The simulation engine (NextManagerTennis_Simulator) is a separate Java worker that receives jobs from Spring Boot via RabbitMQ. Different Mondes may run different simulator versions — for example, a test Monde on a new version while production Mondes remain on the stable version. A simulator version may also need to change mid-Saison in an emergency (broken simulation behaviour), though Pré-Saison is the preferred window for planned upgrades.

Three routing alternatives were considered:

1. **Embedded versioning** — all simulator versions compiled into a single worker, with a version parameter selecting the algorithm at runtime. Simple to deploy, but versions cannot scale independently and old code accumulates indefinitely in the codebase.
2. **Versioned queues per worker** — each simulator version is a separate deployable worker subscribed to its own RabbitMQ queue. Spring Boot routes the job to the correct queue based on the Monde's configured version. No shared process, independently scalable and retirable.
3. **Simulator registry / API gateway** — a dedicated service that maps version identifiers to worker endpoints, with Spring Boot querying the registry at dispatch time. Maximum flexibility, but introduces a service with no additional value over RabbitMQ's native routing for this use case.

## Decision
Use **versioned RabbitMQ queues** (option 2).

- Each simulator version is a separate Java worker deployment subscribing to a version-specific queue: `simulator.v1.jobs`, `simulator.v2.jobs`, etc.
- `MondeSetting` carries a `simulatorVersion` field that Spring Boot reads at job dispatch time to determine the target queue.
- Each `Match` record persists the `simulatorVersion` used at simulation time — not only in replay metadata, but on the `matches` table directly. This ensures matches without a replay (Batch/NONE mode) remain traceable.
- Version changes are made by updating `MondeSetting.simulatorVersion`. The preferred window is Pré-Saison; mid-Saison changes are permitted for critical fixes but result in intra-Saison stats that are not comparable across the version boundary.
- Retiring a version: drain its queue, then stop and remove the worker deployment.

The simulator contract defined in ADR-0004 (Attributs on a 1–99 scale, dynamic states as 0.0–1.0 coefficients, stable physical inputs such as morphology, and match environment coefficients) remains fixed across all versions. A new simulator version may interpret those inputs differently (algorithm changes, balance changes), but must consume the same interface. Changes to the interface itself require a simulator contract version bump, which is a separate concern from the worker routing version.

## Consequences
- Each active simulator version requires a running worker process. The number of simultaneously active versions should be kept small (typically 1–2: current stable + candidate under test).
- Stats from Matches simulated across different versions within the same Saison are not directly comparable. The persisted `simulatorVersion` on each Match allows filtering or flagging when this occurs.
- Deploying a new simulator version does not require any change to Spring Boot or the domain model — only a new worker deployment and a MondeSetting update.
- In a Kubernetes environment, each simulator version maps naturally to a separate `Deployment`. Scaling a version up or down is a replica count change. Retiring a version is `scale to 0`.
