# ADR-0009 — Bi-circuit Club managing both ATP and WTA simultaneously

## Status
Accepted

## Context
The game needs to decide whether a Club manages TennisPlayers from one circuit only (mono-circuit) or from both the ATP (male) and WTA (female) circuits simultaneously (bi-circuit).

The naive choice is mono-circuit: the User picks a circuit at Club creation, all TennisPlayers and Tournois are from that circuit, and the management loop is homogeneous. This is the model used by the vast majority of sports management games.

The alternative is bi-circuit: a single Club manages up to 4 ATP TennisPlayers + 4 WTA TennisPlayers (separate limits per sexe), with the same Personnel working across both circuits. The Ligue contains two independent ranking leaderboards (one per circuit) within the same competitive group. Onboarding provides 2 ATP + 2 WTA TennisPlayers.

## Decision
**Bi-circuit.** A Club manages both ATP and WTA TennisPlayers simultaneously.

Key constraints that follow from this decision:

- **Roster limits are per sexe**: 4 contracted TennisPlayers per sexe (8 total); Centre de Formation holds up to 4 juniors per sexe (8 total).
- **Personnel is shared**: Scouts, Préparateurs, Médecin, Kiné, and other Personnel work across both circuits. A Scout's Tier 1 regional sweep returns candidates of both sexes; Tier 2 investigations target a specific TennisPlayer regardless of sexe.
- **Ligue rankings are circuit-separated**: one ATP ranking table and one WTA ranking table coexist within each Ligue. Points in each circuit are scoped to the Ligue and reset independently on Ligue change.
- **Renouvellement is a single Club-level act**: a User is either active or AFK for the whole Club — there is no per-circuit activity status.
- **Tournois are circuit-separated**: ATP Tournois and WTA Tournois run on independent calendars. A Club's TennisPlayers can be competing in both simultaneously in the same Semaine.
- **IP note**: "ATP" and "WTA" are working names. Circuit names, tournament names, and category names will use fictional equivalents before launch to avoid trademark issues.

## Consequences
- The management surface is approximately doubled: up to 8 adult TennisPlayers + 8 juniors, two Tournoi calendars, two ranking tables. This is the intended scope — the game is explicitly ambitious.
- The same Personnel deployment system covers both circuits without modification. A Scout can investigate a WTA TennisPlayer and an ATP TennisPlayer in the same Saison (via separate missions, since a Scout handles one mission at a time).
- Sponsor Objectifs (résultats sportifs, classement) can reference either or both circuits — the data-driven Objectif model accommodates this without structural change.
- Budget is single and shared across both circuits — a Club's financial position reflects the combined wage bill and prize money from ATP and WTA TennisPlayers.
- Mono-circuit as an alternative was considered and rejected: it would halve the management scope but also halve the strategic depth. The shared-Personnel model is the core design argument for bi-circuit — the same investment in a Kiné or Scout benefits both sides of the roster, creating cross-circuit resource allocation decisions that mono-circuit cannot produce.
