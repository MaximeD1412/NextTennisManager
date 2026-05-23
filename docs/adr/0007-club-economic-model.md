# ADR-0007 — Club economic model

## Status
Accepted

## Context
A Club needs a financial system that creates meaningful strategic pressure without being frustrating. Two competing concerns:

1. **Predictability** — a User should be able to plan across a Saison; surprises should be strategic, not arbitrary.
2. **Risk** — the financial model must have real consequences; a Club that mismanages its Budget must feel it.

Multiple design questions had genuine alternatives: how to structure revenue (recurring vs lump-sum), what to block in deficit, whether to allow voluntary bankruptcy, and how to price contract operations.

## Decision

### Revenue
- **Cachet** — the primary revenue source. A lump sum paid at the first day of each Saison by each of the Club's Sponsors (one per Type de Sponsor: Principal, Raquette, Équipement). Not recurring mid-season — the User negotiates next season's Cachet during the current season and receives it all on day 1 of the next Saison.
- **Prize Money** — a secondary revenue source distributed per Tour won, scaled by Catégorie de Tournoi. Credited to Budget immediately when the Tour result is confirmed.

### Expenses
- **Salaire** — deducted once per Semaine for every active Contrat (TennisPlayer and Personnel). Not per-season, not per-Créneau.
- **Frais de renouvellement** — a small mandatory fee at Contract renewal. Tunable via ConfigurationGlobale (default: small fixed amount). Prevents free mid-deficit rollovers.
- **Résiliation compensation** — `remaining Saisons × annual Salaire × compensation coefficient`. Coefficient defaults to 1 in ConfigurationGlobale; tunable per Monde. A Personnel Agent may negotiate a lower coefficient at signing.

### Deficit rules
A Club may go negative. Being in deficit **blocks**: Enchères participation, Résiliation (compensation required), hiring new Personnel, and Contract renewal (fee required). Being in deficit does **not** block: signing from the Marché Libre, accepting a Wildcard, Désinscription, or sponsor renegotiations that increase incoming Cachet.

Rationale for keeping Marché Libre open in deficit: Marché Libre players carry low Salaires and are the only path for a deficit Club to field a competitive roster and earn Prize Money to recover. Blocking it entirely would force Banqueroute in most cases.

### Banqueroute
Voluntary, User-declared at any time. Consequences: all Contrats terminated, Club starts the next Saison with a minimal fixed Budget (imposed), must rebuild through Enchères or Marché Libre. Ligue membership, ATP points, and Prestige are unaffected. Banqueroute is never forced by the system — the User chooses when to trigger it.

## Consequences
- All financial parameters (Cachet ranges, prize money amounts, Salaire scales, frais de renouvellement, Résiliation coefficient, Banqueroute budget floor) are tunable via ConfigurationGlobale/MondeSetting. Balance will be validated through batch simulation with NPC User bots before launch.
- The Prévisionnel tool (two projections: guaranteed and estimated) is required to make the Salaire-per-Semaine cadence legible to the User.
- The separation of Cachet (lump sum, negotiated) from Prize Money (per-Tour, earned) means a Club can survive a poor sporting season if it has strong Sponsors, and vice versa. Neither source alone should be sufficient.
