# Handoff — NextManagerTennis : Entraînement & Progression Model

## Project

**NextManagerTennis** — a multiplayer online tennis management game. Multi-context monorepo at `/mnt/c/Users/dupre/Documents/PROJET_INFO/PROJECT_TENNIS/`.

- Domain glossary: [`NextManagerTennis_WebApp/CONTEXT.md`](../NextManagerTennis_WebApp/CONTEXT.md)
- Context map: [`CONTEXT-MAP.md`](../CONTEXT-MAP.md)
- System-wide ADRs: [`docs/adr/`](../docs/adr/)
- Architecture reference (French, somewhat outdated): [`docs/architecture_jeu_management_tennis.md`](../docs/architecture_jeu_management_tennis.md)
- No production code exists yet — the project is in design/planning phase.

## What happened in this session

This was a `/grill-with-docs` session continuing from [`/tmp/handoff-nextmanagertennis-semaine-loop.md`](/tmp/handoff-nextmanagertennis-semaine-loop.md). All decisions from that prior session are locked and reflected in `CONTEXT.md`.

### Decisions resolved this session

| # | Decision |
|---|---|
| 1 | **HORS_TOURNOI Activités (MVP):** `Entraînement`, `Repos`, `Préparation Mentale`. List is intentionally minimal and tunable — not architecturally load-bearing. |
| 2 | **EN_TOURNOI Activités (MVP):** same three Activités as HORS_TOURNOI (with adjusted effects — higher Fatigue cost or reduced gain, reflecting on-site tournament conditions) + `SparringPartner` (raises Rythme, exclusive to EN_TOURNOI). Exact effect values are tunable. |
| 3 | **Entraînement is a hierarchy, not a single Activité.** Structure: `Activité: Entraînement → Catégorie d'Exercice (e.g. "Service", "Physique", "Mental") → Exercice spécifique`. Each Exercice has a defined Attribut impact profile displayed to the User as star ratings (★ to ★★★★★). A User assigns one Exercice per Créneau d'Entraînement. The underlying model uses numerical gains; stars are the UX representation. |
| 4 | Gains from an Exercice are modulated by: **PlayerType** (primary), **Fatigue** (secondary, minor), **Potentiel** of the TennisPlayer, and **age**. |

### CONTEXT.md changes made this session

- `Activité` entry updated: HORS_TOURNOI and EN_TOURNOI Activité lists are now enumerated.
- **Not yet reflected in CONTEXT.md:** the Exercice hierarchy model (decision 3 above) — needs to be written in as new glossary terms before or at the start of the next session.

### Decision interrupted mid-session (resume here)

**Âge vs Potentiel** — question was asked but not answered before the handoff was triggered.

The question: are **Potentiel** and **âge** the same concept or two independent dimensions?

- **Option A (recommended):** Potentiel is a fixed ceiling on Attributs assigned at TennisPlayer creation. Age is a separate modifier on the *rate* of progression (young = learns fast, old = stagnates or regresses). Enables the "young high-potential rough diamond" archetype — a core management decision.
- **Option B:** Potentiel is purely a function of age. Simpler but removes a distinct strategic lever.

**The user had not yet answered. Start here.**

## What was NOT covered — continue from here (ordered by priority)

### High priority

1. **Âge vs Potentiel** — interrupted, resume immediately (see above).
2. **Exercice model details** — how many Exercices per Catégorie? How is the star rating computed from the underlying Attribut delta map? Is the PlayerType modifier visible to the User (e.g. "★★★★★ for Serve & Volley, ★★★ for Baseline") or hidden?
3. **Wildcard system** — a TennisPlayer who performed well can receive an invitation to a prestigious Tournoi bypassing the 3-Semaine inscription rule. Requires User acceptance. Design deferred from previous session.

### Medium priority

4. **Inscription UI flow** — how does the User browse and register for Tournois? What does the calendar look like? What is the Saison-start inscription window?
5. **Semaine UI** — how does the User see and fill the 14-Créneau grid? How do Presets get applied and overridden?
6. **Rythme numeric details** — scale (0–100?), decay rate, threshold for noticeable Technique malus.

### Low priority / future

7. **EN_CAMP Contexte** — mentioned as a future Contexte type. Design TBD.
8. **Saison lifecycle** — Pré-Saison, Enchères, Marché Libre, Renouvellement sequencing. Not touched yet.
9. **ListeAttente / Ligue promotion** — covered in ADR-0002 but not stress-tested in detail.

## Context for the next agent

- The user is building this solo; no team. Comfortable with deep domain modelling.
- Discussions happen in French; domain terms are a mix of French and English (as in the glossary).
- The user moves fast on details that are "not architecturally important" — don't over-grill on tunable values (Activité effects, exact star ratings). Focus questions on structural decisions that are hard to reverse.
- Keep `CONTEXT.md` as a pure glossary — no implementation details, no spec content.
- When a term is resolved, update `CONTEXT.md` immediately (don't batch).
- ADR threshold: only when all three hold — hard to reverse, surprising without context, real trade-off with named alternatives.
- The Exercice hierarchy (new concept from this session) needs a glossary entry before the grilling continues.

## Suggested skills

- `/grill-with-docs` — continue on Âge vs Potentiel, then Exercice model details, then Wildcard
- `/tdd` — once Entraînement/Exercice model is fully defined, implement the progression domain model with tests
- `/to-issues` — break the training loop design into implementation tickets once the model is locked
