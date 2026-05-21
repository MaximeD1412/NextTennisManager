# ADR-0002 — Ligue membership based on activity, not ranking

## Status
Accepted

## Context
As the Monde scales to many Users, not everyone can compete in Grand Slam-level Tournois if the pool is too large. A segmentation mechanism is needed to keep competitive groups meaningful.

Two obvious approaches exist:
1. **Ranking-based** — top N players stay in the highest Ligue, bottom N are relegated. Classic sports model.
2. **Activity-based** — Ligues are populated by active Users regardless of ranking. Membership is maintained by periodic renewal; non-renewal leads to demotion.

## Decision
Ligue membership is based on **activity (assiduity)**, not ranking. A User who does not renew their mid-season membership is progressively demoted. A User requesting promotion is placed in the highest Ligue that has no inactive Users.

ATP points are scoped per Ligue and reset to zero on Ligue change. ELO is global and portable — the simulator uses it to calibrate match probabilities regardless of ATP standing, so a newly promoted User with high ELO recovers their competitive position quickly without artificial point carries.

## Consequences
- A User can pursue a long-term low-points strategy (e.g., developing young TennisPlayers) and stay in Ligue 1 as long as they remain active. Ranking does not gate access.
- Inactive Ligues accumulate at the bottom naturally. Active Users bubble up, inactive Users sink — no manual intervention needed.
- ATP points reset on Ligue change is a deliberate cost: the reward for promotion is access to a more active community, not a ranking head-start.
- Promotion is first-come, first-served via a timed waitlist — this creates engagement events (opening of the ListeAttente) without requiring complex qualification logic.
- Ranking-based relegation is explicitly excluded. A User with 0 points but 100% activity stays in their Ligue.
