# ADR-0004 — Data-driven per-Monde configuration with a fixed simulator contract boundary

## Status
Accepted

## Context
Game balance parameters (Exercice effects, progression rates, Tournoi configuration, etc.) need to be tunable without code changes. Two conflicting pressures exist:

1. **Flexibility** — the design is deliberately not locking in numerical values early. Rebalancing should be cheap: change a config value, not source code.
2. **Simulator contract stability** — the simulation engine (NextManagerTennis_Simulator) consumes TennisPlayer Attributs and must produce deterministic results. If the Attribut model varies between Mondes, the simulator needs per-Monde branches — a maintenance burden and a source of divergence bugs.

## Decision
Split game parameters into two tiers:

- **Tunable parameters** — live in ConfigurationGlobale (server-wide defaults) and can be overridden per Monde in MondeSetting. Includes the full Exercice catalogue (which Exercices exist, which Catégorie they belong to, which Attributs they target and at what weight), progression phase rates, Tournoi config, Activité Fatigue/Moral/Rythme deltas, etc. Exercices are data-driven entities — they can be created, removed, or modified per Monde without code changes. New Mondes are initialised from ConfigurationGlobale; MondeSetting holds only the diff.
- **Fixed parameters (simulator contract)** — *which Attributs exist* and their scale (1–99) are fixed across all Mondes. Dynamic states (Rythme, Fatigue, Moral) are always transmitted to the simulator as normalised coefficients (0.0–1.0), regardless of how they are stored in the WebApp. Stable physical inputs required by the simulator, such as morphology (`height_m`, `standing_reach_m`, `body_mass_kg`, dominant hand), are also part of the fixed contract. The simulator is built against this interface. MondeSetting cannot add, remove, or rescale Attributs, nor change the coefficient contract for dynamic states or morphology fields. Note: ELO was considered as a simulator input but rejected as redundant — Attributs already fully encode player strength. Prestige exists as a meta-game layer and is not a simulator input.

This enables test Mondes for rebalancing experiments without special-casing the simulator.

## Consequences
- Any parameter that should be tunable must be externalised to ConfigurationGlobale from the start — it cannot be added later without a migration.
- The boundary (tunable vs fixed) must be enforced explicitly: the simulator interface is the authoritative definition of the fixed contract. Changes to the Attribut model require a simulator interface version bump.
- The internal Java -> Rust physics boundary (ADR-0024) is not exposed to the WebApp, but it must remain derived from the fixed simulator contract plus tunable physical coefficients. Rust must not introduce a second hidden game-domain contract.
- MondeSetting grows as the parameter surface grows — it is a diff, not a full copy, to avoid duplication with ConfigurationGlobale.
- Test Mondes can run with aggressive values (e.g. 10× progression rates) without affecting production Mondes.
