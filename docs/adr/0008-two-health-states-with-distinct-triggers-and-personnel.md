# ADR-0008 — Two distinct health states with independent triggers and dedicated Personnel

## Status
Accepted

## Context
TennisPlayers need a health system that creates strategic pressure without blocking participation (frustrating blocks were explicitly rejected). Two design concerns:

1. **Variety** — a single generic "injury" state with one trigger would be solved by one Personnel type. Multiple independent states create richer decisions.
2. **Fairness** — some health problems should be preventable through good management; others should be unavoidable (requiring mitigation, not prevention).

The original design had a single Blessure triggered by elevated Fatigue, with a combined Médecin/Kiné role. This was replaced during design grilling.

## Decision
Two independent health states, each with its own trigger, its own Attribut malus profile, and its own dedicated Personnel type.

### Maladie
- **Trigger**: pure random chance — independent of Fatigue, calendar density, or any other game state.
- **Contagion**: if untreated, other TennisPlayers in the same Club risk becoming sick. No contagion from Blessure.
- **Personnel response**: Médecin — accelerates recovery (a high-quality Médecin can resolve a minor illness in as few as 2 Créneaux) and reduces contagion probability.
- **Design intent**: unavoidable, insurance-like. The User cannot prevent Maladie — they can only limit its duration and spread. A Médecin is the mitigation tool.

### Blessure
- **Trigger**: probabilistic, driven by elevated Fatigue at the time of a Match. Higher Fatigue = higher probability of injury during a Tour.
- **Contagion**: none.
- **Personnel response**: Kiné — accelerates recovery (reduces the number of Créneaux before the Attribut malus clears).
- **Design intent**: avoidable through Fatigue management. A Blessure is a consequence of poor planning — the User had the tools (Repos Créneaux, Activité choices) to prevent it. A Kiné shortens the penalty but does not remove the management lesson.

Neither state prevents Tournoi participation — both apply only in-match Attribut malus.

## Consequences
- The two-role split (Médecin vs Kiné) means a Club must invest in two separate Personnel slots to cover both health vectors. A Club that cannot afford both must accept higher risk on one axis.
- Maladie contagion creates a Club-wide risk from a single untreated player — relevant when a Club has multiple TennisPlayers in concurrent Tournois in the same Semaine.
- "Coach Principal" as a Personnel type was considered and rejected: the User already fulfils that role through Activité assignment, Tactique setting, and Exercice selection. See avoided terms.
- Fatigue remains the sole risk driver for Blessure; it is not a factor in Maladie probability. This keeps the two health states conceptually clean and their mitigation strategies distinct.
