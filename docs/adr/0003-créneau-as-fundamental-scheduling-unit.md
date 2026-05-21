# ADR-0003 — Créneau as the fundamental scheduling unit (replacing Activité-per-Semaine)

## Status
Accepted

## Context
The original model assigned one Activité per Semaine per TennisPlayer. This was simple but created several problems when the Tournoi system was designed in detail:

1. **Tournois spanning multiple Semaines** (e.g. Grand Chelem = 2 Semaines, 7 Tours) required the concept of a Tour occupying a Semaine — but multiple Tours happen within the same Semaine, so a single Activité could not represent them all.
2. **Post-elimination free time**: a TennisPlayer eliminated in Tour 1 of a 4-Tour Semaine has 3 Tours' worth of time with nothing to do. The 1-Activité model had no way to fill this time differently from the rest of the Semaine.
3. **Plan de Secours**: the fallback plan for early elimination requires a sub-Semaine scheduling unit — it is meaningless at Semaine granularity.
4. **Dynamic states per Créneau**: Fatigue, Moral, and Rythme should accumulate incrementally as the Semaine progresses, not in a batch at the end. A batch model produces implausible results (e.g. a TennisPlayer plays 4 Tours and rests in the same "Semaine Activité").
5. **Contexte-dependent Activités**: different Activités are available depending on whether the TennisPlayer is EN_TOURNOI or HORS_TOURNOI within the same Semaine. A single-Activité model cannot express this variation.

## Decision
Replace the "1 Activité per Semaine" model with **14 Créneaux per Semaine** (7 days × morning + afternoon). Each Créneau receives exactly one Activité. Key rules:

- A Tour occupies exactly 1 Créneau; the immediately following Créneau is mandatory Repos.
- Available Activités depend on the TennisPlayer's Contexte for the Semaine (`EN_TOURNOI` or `HORS_TOURNOI`), resolved once per Semaine from the schedule.
- Dynamic states (Fatigue → Physique, Moral → Mental, Rythme → Technique) update after each Créneau executes, not in batch.
- The 14-Créneau structure is fixed regardless of MondeSetting cadence — cadence controls how fast Créneaux are processed in real time, not how many there are.
- Créneaux execute globally for all TennisPlayers in the Monde in sync: when the Créneau clock ticks (triggered by a Tour being played), all TennisPlayers execute their corresponding Créneau simultaneously.

## Consequences
- The management UI must present a 14-slot weekly grid rather than a single Activité picker. Presets (named full-Semaine plans) mitigate the added complexity for Users who prefer not to micro-manage.
- The data model stores 14 Créneau assignments per TennisPlayer per Semaine, not 1.
- Dynamic state calculations run per Créneau execution, making the simulation incremental within a Semaine.
- The Plan de Secours becomes a first-class feature: a partial Créneau plan for post-elimination slots, separate from the main Semaine plan.
- The 1-Activité-per-Semaine model is explicitly retired. Do not reintroduce it as a simplification — the complexity it removed is load-bearing for the Tournoi and dynamic-state systems.
