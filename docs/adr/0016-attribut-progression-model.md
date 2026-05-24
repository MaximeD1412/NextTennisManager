# ADR-0016 — Modèle de progression des Attributs TennisPlayer

## Status
Accepted

## Context

The management loop requires a precise formula for how TennisPlayer Attributs change over time — through training (Exercice) and through age (Régression). Several interacting decisions needed to be made simultaneously:

- How does the Note Brute of an Exercice translate into a numerical Attribut delta?
- How do PlayerType, Potentiel, progression phase, Préparateur, and Fatigue combine?
- What does the Potentiel soft cap on the global average mean mechanically?
- Is Régression part of the per-Créneau gain model, or a separate process?

These decisions are difficult to reverse once the simulator contract is implemented and TennisPlayer data is generated, because changing the formula mid-game would invalidate all existing TennisPlayer trajectories.

## Decision

### Gain per Créneau

Attribut gains are applied **per Créneau** as fractional deltas on the 1–99 scale. There is no batching at Semaine end.

The per-Créneau gain formula for a targeted Attribut is:

```
Note_Réelle_delta = Note_Brute_delta × PlayerType_multiplier × Préparateur_multiplier

gain = Note_Réelle_delta
     × Potentiel_base
     × Potentiel_peak           (only during Période de Grosse Progression)
     × diminishing_returns      (only when global average > Potentiel soft cap threshold)
     × Fatigue_coefficient
```

**Note Brute delta** — each targeted Attribut has an independent Note Brute, stored as a numerical delta value in the Exercice catalogue. The star display (★ to ★★★★★) is a non-linear bucketing of this value; the mapping is data-driven via ConfigurationGlobale. Each Exercice may target multiple Attributs, each with its own independent Note Brute — there is no shared "Exercice score" redistributed via weights.

**PlayerType affinity** — each Exercice declares a `HIGH` / `NEUTRAL` / `LOW` affinity per PlayerType, stored in the catalogue. These map to multipliers (tunable via ConfigurationGlobale; defaults approximately 1.3 / 1.0 / 0.7). Applied to the Note Brute delta before any other modulation.

**Note Réelle** — the product of Note_Brute_delta × PlayerType_multiplier × Préparateur_multiplier. This is the value displayed to the User in the UI (orange stars) and the input to the rest of the gain chain. The Préparateur multiplier is included here because what the User sees must equal what drives the gain — hiding the Préparateur effect behind Potentiel/Phase would create a disconnect between displayed and effective values.

**Potentiel_base** — an always-active multiplier derived from the TennisPlayer's Potentiel (1–5★). A 5★ TennisPlayer consistently gains more per Créneau than a 3★ one at every phase of their career.

**Potentiel_peak** — an additional multiplier applied only during the Période de Grosse Progression. Its amplitude is Potentiel-dependent: a 5★ TennisPlayer has a larger peak multiplier than a 3★ one. All values are data-driven; a flat peak for all Potentiel levels is achievable by setting all peak values equal.

Potentiel_base and Potentiel_peak were not collapsed into a single multiplier because they serve different design purposes: Potentiel_base makes high-Potentiel TennisPlayers worth developing across their whole career; Potentiel_peak makes the Grosse Progression window especially high-stakes for the best prospects.

**Diminishing returns** — when the TennisPlayer's global Attribut average exceeds a threshold defined by their Potentiel, gains are reduced by a progressive formula: `diminishing_returns = (cap - current_average) / (cap - threshold)`, approaching zero as the average approaches the hard individual ceiling of 99. The threshold is Potentiel-dependent — a higher Potentiel TennisPlayer can reach a higher average before the slow-down begins. There is no hard ceiling on individual Attributs; the constraint is on the global average.

A hard ceiling per Attribut was rejected because it would allow degenerate builds (maxing one Attribut at 99 while neglecting others with no systemic cost). A soft cap on the global average prevents extreme outliers while preserving specialist profiles.

**Fatigue_coefficient** — a minor linear reduction applied when the TennisPlayer's Fatigue (inter-match) exceeds 60%: `coefficient = 1.0 - 0.25 × max(0, (Fatigue - 0.6) / 0.4)`. At maximum Fatigue (100%), gains are reduced by at most 25%. Thresholds and floor are tunable via ConfigurationGlobale. The effect is intentionally small — the primary risk of high Fatigue is Blessure, not reduced training yield.

### Régression (Saison-end process)

Régression is a **separate Saison-end stochastic process**, independent of per-Créneau gains:

- At the end of each Saison in the Période de Régression, a probability check determines whether each Attribut loses points.
- Both the probability of triggering and the intensity of the loss increase the longer the TennisPlayer has been in the phase.
- Probability and intensity are **category-weighted**: Physique regresses fastest; Technique at an intermediate rate; Mental slowly; Intelligence de jeu quasi-never. All weights are data-driven.
- The net Attribut change at Saison end = sum of per-Créneau gains − Saison-end regression loss. A User who trains intensively can offset regression; one who neglects training cannot.

Régression was not integrated into the per-Créneau gain formula (as a negative multiplier) because: (1) Régression is described as stochastic and hidden — a continuous negative delta would make it detectable through training observation; (2) the Saison-level granularity of Régression matches the narrative ("Attributs decline over Saisons") better than a per-Créneau leak.

## Consequences

- The Exercice catalogue must store, per targeted Attribut: Note Brute delta value and PlayerType affinities (one per PlayerType, three discrete levels).
- The TennisPlayer entity must carry: Potentiel (1–5★), current progression phase, current global average (for soft cap calculation), and time-in-Régression (for regression probability curve).
- The Fatigue state used here is the same inter-match Fatigue already defined in the WebApp context — no new state is introduced.
- All numeric coefficients (PlayerType multipliers, Potentiel_base values, Potentiel_peak values, soft cap thresholds, diminishing returns formula, Fatigue threshold, regression probability curves, category weights) are externalised to ConfigurationGlobale and overridable per Monde in MondeSetting.
- Changing any coefficient post-launch is a game balance action, not a code change — but changes that affect existing TennisPlayer trajectories should be considered carefully.
