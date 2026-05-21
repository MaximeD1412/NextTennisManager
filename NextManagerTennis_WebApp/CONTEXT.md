# Context — NextManagerTennis (Online Game)

## Glossary

### User
The human logged into the game. A User can own one or more Clubs (one per Monde, potentially across different sports in the future). Do not use "Player", "Manager", or "Coach" to refer to the human — those are ambiguous.

### TennisPlayer
The simulated tennis athlete. A TennisPlayer exists independently of any Club — they can be free (no Club) or under Contract. Do not use "Player" alone; it is too ambiguous with User in some contexts.

### Monde
The global online game instance shared by all Users. A Monde is the top-level container for Clubs, TennisPlayers, Tournaments, and Seasons. Multiple Mondes can coexist on the server without overlap (e.g. different game modes or regions).

### Club
The entity a User manages inside a Monde. A Club signs Contracts with TennisPlayers. A User can hold one Club per Monde. Maximum **4 contracted TennisPlayers** per Club (excluding future Centre de Formation capacity). A Club is also used in solo modes as a neutral container — a solo session creates a fictitious Club with an infinite Contract. Do not use "Agence", "Écurie", or "Team" as synonyms.

### Centre de Formation *(future)*
A Club upgrade that extends the maximum number of contracted TennisPlayers beyond the base limit of 4. Not yet implemented.

### Pool de TennisPlayers
The full population of TennisPlayers that exist within a Ligue — approximately 1 000 at launch, growing with features like the Centre de Formation. At any time, some TennisPlayers are under Contract with a Club; the rest are **libres**. Free TennisPlayers are persistent entities (with Attributs, ELO, PlayerType, nationality, preferred surface) — not disposable bots. They are auto-inscribed by the system to fill Tournoi draws when not enough contracted TennisPlayers are registered.

### Contrat (Contract)
The link between a Club and a TennisPlayer. Has a duration and a **Salaire** (weekly or per-season wage paid by the Club). A TennisPlayer without a Contract is **libre** (free agent). When a Contract expires, the Club decides whether to renew. A TennisPlayer whose Contract is not renewed enters the **Enchères** during Pré-Saison; if unsold, they fall back to the **Marché Libre**.

### Salaire (Wage)
The recurring cost a Club pays to maintain a Contract with a TennisPlayer. The only cost of signing a TennisPlayer from the Marché Libre. Higher-quality TennisPlayers command higher Salaires.

### Pré-Saison
The period before each Saison where Enchères take place for TennisPlayers whose Contracts were not renewed. After the Pré-Saison window closes, unsold TennisPlayers move to the Marché Libre.

### Enchères (Auction)
The mechanism for acquiring previously contracted TennisPlayers during Pré-Saison. Multiple Clubs can bid; the highest Salaire offer wins. Only available during Pré-Saison.

### Marché Libre (Free Market)
Instant-sign market available at any time. TennisPlayers here are generally weak (either newly generated or unsold after Enchères). No acquisition cost — only the ongoing Salaire.

### Onboarding
When a User joins a Ligue, they receive 2 TennisPlayers automatically:
- One competitive TennisPlayer (top-100 level) ready to play immediately.
- One young TennisPlayer with high potential, to develop over time.
Both TennisPlayers are created and added to the Ligue Pool at the moment of inscription, then immediately placed under Contract with the new Club.

### PlayerType
A trait assigned to a TennisPlayer that shapes their training progression and suggests recommended Tactic presets. Examples: `Defender`, `Serve & Volley`, `Baseline`, `All-Court`. A PlayerType influences which Attribute domains progress faster during training — a TennisPlayer trained against their PlayerType progresses more slowly in mismatched domains. PlayerType can evolve naturally through play or be forced by the User at their own strategic risk.

### Tactique (Tactic)
The game plan applied to a TennisPlayer during a Match. Composed of named presets (e.g. `AGGRESSIVE_BASELINE`, `DEFENSIVE_COUNTER`, `SERVE_AND_VOLLEY`) which are configurations of underlying parameters. The User can use a preset as-is or fine-tune the parameters manually. A PlayerType suggests recommended presets but does not restrict the User's choice.

Two scopes exist:
- **Tactique permanente** — the default Tactic configured on the TennisPlayer, persisted.
- **Tactique en match** — an in-match override, ephemeral by default. Saved only if the User explicitly chooses to persist it at the end of the Match.

### Attributs
The grouped numerical stats of a TennisPlayer. Organized in three categories:
- **Physique** — vitesse, endurance, explosivité, etc.
- **Technique** — service, retour, coup droit, revers, volley, etc.
- **Mental** — clutch, résistance à la pression, concentration, etc.

Each Attribute has a numerical value. Do not call these "stats" or "notes" — use "Attributs".

### Rythme (Match Rhythm)
A dynamic state of a TennisPlayer that represents competitive sharpness. Rises with Matches played (Tours). Decays naturally each Semaine without a Match — not through Repos or other Activités. Affects **Attributs Technique** (service, retour, coup droit, revers, volley): high Rythme improves technical precision and timing; low Rythme reflects the rustiness of a player returning after a long break from competition. Creates strategic tension with Fatigue: playing more keeps Rythme high but raises Fatigue; resting recovers Fatigue but lets Rythme decay. Do not confuse with Moral (→ Mental) or Fatigue (→ Physique) — each dynamic state targets a distinct Attribut category.

### Fatigue
A dynamic state of a TennisPlayer. Rises with Matches played and Entraînement Activités, falls with Repos Créneaux. High Fatigue increases the risk of Blessure and reduces effective **Attributs Physique** (vitesse, endurance, explosivité) during a Match. Distinct from the intra-match fatigue computed by the simulator point-by-point (which is ephemeral and not persisted between Matches).

### Moral
A dynamic weekly state of a TennisPlayer. Rises with wins and certain Activités, falls with losses or overtraining. Influences effective Attributs during a Match, primarily the Mental category. Managed by the User through weekly Activité choices.

### Blessure (Injury)
A temporary state triggered by elevated Fatigue. Applies in-match Attribute malus but does **not** prevent a TennisPlayer from being entered in a Tournoi. Not a long absence. Designed to be a strategic cost, not a frustrating block.

### Ligue
A subdivision of a Monde that groups approximately 100 Users into an isolated competitive pool. Each Ligue has its own ATP ranking leaderboard — ATP points are scoped per Ligue and reset to zero when a User moves to a different Ligue. ELO is global and portable across Ligues (used by the simulator, not affected by Ligue changes).

Ligues are numbered (Ligue 1, Ligue 2, etc.). Ligue 1 contains the most active Users. New Ligues are created as the Monde grows.

**Membership rules:**
- A User must renew their Ligue membership mid-season to remain active. Non-renewal = inactive status = progressive demotion to a lower Ligue.
- To move up, a User joins the **ListeAttente** (waitlist) for the higher Ligue. A User requesting promotion is placed in the highest Ligue that has no inactive (AFK) Users.
- There is no ranking-based relegation — only activity-based demotion.

**Design intent:** The Ligue system rewards *assiduity*, not level. A User can have a long-term strategy with young TennisPlayers who score few points and still stay in Ligue 1, as long as they remain active.

### ListeAttente (Promotion Waitlist)
The mechanism by which a User requests promotion to a higher Ligue. Opens at a fixed time near the end of a Saison (first-come, first-served). Users are notified 4–5 days in advance with reminders, and the exact opening time is communicated clearly. A User on the ListeAttente is placed in the highest Ligue without inactive Users when a spot becomes available.

### Renouvellement (Membership Renewal)
A mid-season activity check. A User who does not renew their Ligue membership is marked inactive and begins a progressive demotion to lower Ligues. Renewal is the mechanism that keeps Ligues populated with active Users. Do not call this "abonnement" or "subscription" — it is a presence signal, not a payment.

### MondeSetting
The configuration entity attached to a Monde. Holds parameters that define how the Monde behaves, including:
- `type`: `SOLO` | `MULTI`
- `cadence`: `REAL_TIME` | `CRÉNEAU_PAR_JOUR`
- `value_cadence`: number of Créneaux processed per real-world day when cadence is `CRÉNEAU_PAR_JOUR` (e.g. `2` = one game Semaine lasts 7 real days; `4` = one game Semaine lasts 3.5 real days). Unused for `REAL_TIME`. A Semaine always contains exactly 14 Créneaux regardless of cadence — the cadence only controls how fast they are processed. Tours are never scheduled in the dead-of-night window (0h–6h); implementation maps each Créneau index to an execution hour that avoids unsociable times for European users.

Distinct from the Monde entity itself — the Monde is the game world, MondeSetting is its ruleset.

### Saison (Season)
A fixed competitive cycle within a Monde, composed of Semaines. Defines the Tournoi calendar for the period. TennisPlayers accumulate ranking points across a Saison.

### Semaine (Week)
The atomic unit of management within a Saison. A Semaine is composed of **14 Créneaux** (7 days × morning + afternoon). The User assigns one Activité to each Créneau for each of their TennisPlayers. Activité effects (Fatigue, Moral, Rythme) accumulate per Créneau, not in batch at the end of the Semaine. A Semaine advances at midnight when all Tournois scheduled in it have completed their allocated Tours (see `toursParSemaine`). A Semaine in-game does not correspond to a real-world week except in `REAL_TIME` cadence.

### Créneau (Time Slot)
The half-day scheduling unit within a Semaine. There are 14 Créneaux per Semaine (morning and afternoon of each of the 7 days). Each Créneau receives exactly one Activité. When a TennisPlayer plays a Tour, that Tour occupies one Créneau; the immediately following Créneau is mandatory Repos. The Créneau is the granular unit of the management loop.

### Activité
The action assigned to a TennisPlayer for a given Créneau. The available Activités depend on the TennisPlayer's current Contexte.

**HORS_TOURNOI Activités (MVP):**
- `Entraînement` — improves Attributs, raises Fatigue
- `Repos` — reduces Fatigue
- `Préparation Mentale` — improves Moral, Fatigue-neutral

**EN_TOURNOI Activités (MVP):**
- Tour (not an Activité — occupies 1 Créneau + mandatory Repos)
- `SparringPartner` — raises Rythme; only available EN_TOURNOI
- `Entraînement`, `Repos`, `Préparation Mentale` — same as HORS_TOURNOI but with adjusted effects (e.g. higher Fatigue cost or reduced Attribut gain, reflecting on-site tournament conditions rather than academy training). Exact effect values are tunable and not architecturally load-bearing.

Presets are available for Users who prefer not to micro-manage each Créneau individually. Do not use "Activité" to refer to a Tour — Tours occupy Créneaux but are not Activités.

### Preset
A named template that pre-fills a TennisPlayer's Créneau plan for a Semaine (all 14 Créneaux). Applied by the User as a starting point; individual Créneaux can be adjusted manually afterwards. A Preset respects the TennisPlayer's current Contexte — Créneaux already occupied by Tours or mandatory Repos are not overwritten. Examples: `PREPARATION_PHYSIQUE`, `MATCH_RYTHME`, `RÉCUPÉRATION`. A separate category of Preset exists for the Plan de Secours, covering only the Créneaux freed after early elimination (not the full Semaine).

### Abandon
A voluntary withdrawal by the User of a TennisPlayer from an ongoing Tournoi. Consequences:
- **Moral decreases** (penalty for quitting mid-competition).
- **Rythme is unaffected** (the TennisPlayer played rounds before withdrawing).
- **ATP points are kept** for all Tours completed before the Abandon — no retroactive penalty.
- The TennisPlayer cannot participate in another Tournoi in the current Semaine.
- The 3-Semaine inscription rule naturally prevents re-inscription in the same or another Tournoi on short notice.
- Freed Créneaux in the current Semaine fall to Plan de Secours or mandatory Repos.

### Wildcard *(future)*
An invitation extended to a TennisPlayer to enter a more prestigious Tournoi without the standard 3-Semaine advance inscription, typically following a strong recent performance. Requires explicit User acceptance. Bypasses the inscription deadline as a special case. Design details TBD.

### Plan de Secours (Fallback Plan)
A pre-configured set of Activités that fills the Créneaux freed by an early elimination from a Tournoi. Defined by the User before the Tournoi begins. Can be filled manually or via a preset. If no Plan de Secours is set and the TennisPlayer is eliminated before the last Tour of the Semaine, the freed Créneaux default to mandatory Repos. The Plan de Secours draws from the Activités available in the TennisPlayer's current Contexte (`EN_TOURNOI`) for the remainder of that Semaine.

### Contexte (Player Context)
The situational state of a TennisPlayer for an entire Semaine. Determines which Activités are available for each Créneau within that Semaine. Two rules:
- `EN_TOURNOI` — automatic, set when the TennisPlayer has Tours scheduled in that Semaine (derived from Tournoi inscription). Cannot be overridden by the User.
- `HORS_TOURNOI` — default when not in a Tournoi. The User can override this to another Contexte if additional options exist (e.g. `EN_CAMP` in the future).

Do not model Contexte as a persisted field on the TennisPlayer — it is resolved per Semaine from the schedule and User choices.

### Traits *(future)*
Qualitative situational bonuses attached to a TennisPlayer. No numerical value — they activate in specific match contexts (e.g. `Grand Serveur`, `Joueur de Pressure`). Distinct from Attributs. Not yet implemented.

### Tournoi
A competitive event within a Saison that TennisPlayers enter by User inscription. Configuration properties:
- `format`: `ELIMINATION_DIRECTE` | `POULE_PUIS_ELIMINATION`
- `matchFormat`: `BEST_OF_3` | `BEST_OF_5` (e.g. Grand Chelem = best of 5, other Tournois = best of 3)
- `toursParSemaine`: an ordered list specifying how many Tours belong to each Semaine (e.g. `[4, 3]` for a Grand Chelem spanning 2 Semaines — Tours 1–4 in Semaine 1, Tours 5–7 in Semaine 2). Single-Semaine Tournois have a list of one element. This property drives the Semaine advancement condition: a Semaine advances when all Tournois scheduled in it have completed their allocated Tours.

Inscription requires at least **3 game Semaines** before the first Semaine in which the Tournoi has Tours (i.e. 3 Semaines before `toursParSemaine[0]`). Exception: at Saison start, a special inscription window opens with the publication of the calendar, with no advance deadline. Inscription is to the Tournoi as a whole — not per Semaine.

Composed of one or more Phases.

### Phase
A stage within a Tournoi that groups Tours under shared rules. Two types:
- `POULE` — round-robin group play. A Match loss does not eliminate the TennisPlayer. The Phase defines how many TennisPlayers qualify per group and the tie-breaking criteria.
- `ELIMINATION` — single elimination bracket. A Match loss eliminates the TennisPlayer.

### Tour (Round)
The atomic progression unit within a Phase. A Tour is a set of Matches played at the same step of the Tournoi. Consistent across both Phase types — "all matches happening in this step". A Tour is also the scheduling unit in accelerated Mondes (1 or 2 Tours per day).

### Match
A single encounter between two TennisPlayers within a Tour. Simulated in either Live or Batch mode. The Match is the unit of work dispatched to the simulation engine.

### Live (Simulation Live)
A Match simulation followed in real time by a User. The User can change Tactics mid-match. Events are streamed via WebSocket. The source of truth for live state is Redis.

### Batch (Simulation Batch)
A Match simulation that runs in the background without real-time display. Used for unattended matches and mass simulation (e.g. simulating an entire Tournament round). No WebSocket, minimal storage.

## Avoided terms

| Avoid | Use instead | Reason |
|---|---|---|
| Player | TennisPlayer or User | Ambiguous between athlete and human |
| Manager | User | The human is a User; "Manager" has no formal role |
| Agence / Écurie | Club | Club is the canonical entity |
| Stats / Notes | Attributs | Canonical term for TennisPlayer numerical values |
| Simulation interactive | Live | Canonical simulation mode name |
| Simulation massive | Batch | Canonical simulation mode name |
| Partie | Monde (online) or Carrière (solo, future) | "Partie" is ambiguous across game modes |
| ROUND_PER_DAY | CRÉNEAU_PAR_JOUR | Créneaux are the universal scheduling unit, not rounds |
| Activité Tournoi | Tour (occupies a Créneau) | A Tour is not an Activité — it occupies a Créneau |
