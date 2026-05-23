# ADR-0005 — Personne as the unified entity for TennisPlayer and Personnel

## Status
Accepted

## Context
The game needs two categories of individuals managed by a Club: TennisPlayers (competing in Matches) and Personnel (e.g. Scouts, and others TBD). A TennisPlayer can retire and transition into a Personnel role within the same Club. This retirement-to-Personnel path creates a lifecycle relationship between the two roles that needs to be modelled explicitly.

Two structural alternatives were considered:

1. **Separate hierarchies** — TennisPlayer and Personnel are distinct entity types with no shared base. Transition at retirement is an explicit copy/transform operation between two independent models.
2. **Unified Personne entity** — TennisPlayer and Personnel are roles (expressed as Contrat types) held by the same underlying Personne. Both attribute sets (TennisPlayerAttributes and PersonnelAttributes) coexist on the Personne from creation.

## Decision
Use the unified Personne model (option 2).

A `Personne` carries:
- Identity (name, nationality, age, preferred surface)
- `TennisPlayerAttributes` — always present, used while holding a `TENNIS_PLAYER` Contrat
- `PersonnelAttributes` — always present, but not visible to the User while the Personne holds a `TENNIS_PLAYER` Contrat
- Two independent `Potentiel` values: one for TennisPlayer growth, one for Personnel growth

The active role is determined by the `type` field on the Personne's current Contrat (`TENNIS_PLAYER` | `SCOUT` | other Personnel types). A Personne holds at most one active Contrat at a time.

At retirement, if the Personne is still under Contrat with a Club, the User can inspect their PersonnelAttributes and sign a new Personnel Contrat — no data migration, no synthetic entity creation.

## Consequences
- PersonnelAttributes and Personnel Potentiel are generated at creation for every Personne — including TennisPlayers who may never be recruited as Personnel. This is acceptable overhead given the information is not exposed unless the retirement-to-Personnel path is taken.
- Some PersonnelAttributes are career-influenced: a TennisPlayer's active career modifies certain PersonnelAttributes over time (e.g. high Mental Attributs progressively increase coaching ability in the Mental domain). This cross-influence is weighted and tunable via ConfigurationGlobale. Scouting aptitude is explicitly not career-influenced — it is purely innate.
- The Contrat entity is generalised: it links a Club to a Personne (not specifically a TennisPlayer) and carries a `type` field. TENNIS_PLAYER Contrats support 1–3 Saison durations; Personnel Contrats are 1 Saison.
- The TennisPlayer and Personnel Potentiel values are independent — a high TennisPlayer Potentiel does not imply high Personnel Potentiel. This prevents the retirement path from being a trivial quality recycling mechanism.
- The Âge de Retraite Possible is never revealed to the User. The only retirement signal is a mid-season announcement in the final season. PersonnelAttributes are also hidden while the Personne is active as a TennisPlayer — the User discovers them only at the retirement transition.
