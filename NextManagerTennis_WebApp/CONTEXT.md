# Context — NextManagerTennis (Online Game)

## Glossary

### User
The human logged into the game. A User can own one or more Clubs (one per Monde, potentially across different sports in the future). Carries a `preferredLanguage` field (e.g. `fr`, `en`) that persists in the database and is read by the backend to localise asynchronous notifications (emails, offline push events) in the User's chosen language. Do not use "Player", "Manager", or "Coach" to refer to the human — those are ambiguous.

### Personne
The base entity for any individual in the game. Every TennisPlayer and every Personnel member is a Personne. Carries: identity (name, nationality, age, preferred surface, **sexe**), physical morphology (`height_m`, `standing_reach_m`, `body_mass_kg`, dominant hand), TennisPlayerAttributes, and PersonnelAttributes. Sexe is fixed at creation and determines which circuit a TennisPlayer competes in (ATP for male, WTA for female). Do not use "genre" — use "sexe". Both attribute sets are generated at creation and coexist on the same entity throughout their lifecycle — the Personne exercises only one role at a time, determined by their active Contrat type. The TennisPlayer Potentiel and the Personnel Potentiel are independent values, also generated at creation. Do not use "Personne" as a synonym for TennisPlayer or Personnel — it is the underlying entity they share.

Morphology is not trainable and is not an Attribut. It is generated at creation and transmitted to the Simulator because it affects real tennis physics: service contact height, smash reach, volley reach, movement inertia, fatigue and injury/glissement risk. `standing_reach_m` is the primary field for service contact height; `height_m` alone is not enough.

### TennisPlayer
A Personne currently holding a `TENNIS_PLAYER` Contrat with a Club, or libre (no Contrat). The simulated tennis athlete. Do not use "Player" alone; it is too ambiguous with User in some contexts.

When a TennisPlayer retires, their active Contrat ends. If they are still under Contrat with a User's Club at that moment, the User can consult their PersonnelAttributes and choose to recruit them as Personnel (signing a new Contrat of type `SCOUT` or another Personnel type). Otherwise, the Personne exits the game.

### Monde
The global online game instance shared by all Users. A Monde is the top-level container for Clubs, TennisPlayers, Tournaments, and Seasons. Multiple Mondes can coexist on the server without overlap (e.g. different game modes or regions).

### Club
The entity a User manages inside a Monde. A Club signs Contrats with both ATP (male) and WTA (female) TennisPlayers simultaneously — the same Personnel works across both circuits. A User can hold one Club per Monde. Maximum **4 contracted TennisPlayers per sexe** (8 total), plus up to 4 juniors per sexe in the Centre de Formation (8 total). In the MyPlayer sub-mode of Mode Solo, a **Club fictif** is automatically created as the contractual container for the TennisPlayer and their Personnel entourage. The TennisPlayer's Contrat with this Club fictif has an **indefinite duration** — it remains active until Retraite and cannot be résilié by the User. Do not use "Agence", "Écurie", or "Team" as synonyms.

### Centre de Formation
A structure attached to every Club that serves two roles: (1) it holds up to 4 junior TennisPlayers per sexe (aged 16–17) — 8 juniors total — under `JUNIOR` Contrats; (2) it generates an internal **Vivier** of new junior candidates each Saison, drawn from the Club's own academy pipeline.

Capacity is separate from the main Club limit per sexe — a Club can hold 4 male juniors + 4 female juniors in the Centre de Formation and 4 male + 4 female TennisPlayers in the main roster simultaneously. Juniors in the Centre de Formation participate in the Circuit Junior (its own Tournois, separate from the adult circuit) and cannot enter adult Tournois.

At 18, a junior exits the Centre de Formation automatically. The User may sign them to a standard adult Contrat (`TENNIS_PLAYER`) if a slot is available in the main Club — this is the graduation decision. If no slot is available or the User declines, the junior becomes libre on the adult circuit and leaves the Club.

A junior may be signed to the Centre de Formation from age 16 via a Contrat of type `JUNIOR` (duration: 1 Saison, renewable). See Contrat.

**Qualité du Centre de Formation:** Each Centre de Formation has a quality rating on a 0–5 star scale. All Clubs start at **3 stars**. Quality decays by **0.5 stars per Saison** without maintenance. A Club can perform a **Maintenance** action: fixed cost (tunable via ConfigurationGlobale), adds 1 star, takes half a Saison to complete — allowing up to 2 Maintenances per Saison if the Centre has degraded significantly. Higher quality widens the upper end of the quality range of Vivier-generated juniors — it does not raise the floor. A low-quality Centre can still produce weak juniors regardless of maintenance history; exact probability curves are tunable and validated through Batch simulation. See Vivier and Maintenance du Centre de Formation.

**Mode de gestion des juniors:** A per-Club User preference (not a MondeSetting — it affects only the User's own Club and does not influence other Users or the Monde's rules). Two modes: `FULL` — juniors are managed identically to adult TennisPlayers (14 Créneaux, full Activité set, Rythme/Fatigue/Moral active); `SIMPLIFIED` — no Créneau assignment; progression is handled automatically by heuristic, and the User only makes three decisions: Tournoi Circuit Junior inscription, Contrat JUNIOR renewal/résiliation, and the graduation decision at 18. The `FULL` mode is the default; Presets are the primary tool for reducing micro-management overhead in that mode. Other parts of the game may gain similar SIMPLIFIED toggles in the future via the same per-Club preference system.

### Vivier
The cohort of junior TennisPlayer candidates generated by a Club's Centre de Formation at each Pré-Saison. Exclusive to the Club — no other Club can see or sign them during the Pré-Saison signing window. If the Club does not sign a Vivier candidate before Saison start, that candidate becomes libre and enters the Marché Libre. The quality range of Vivier candidates is determined by the Centre de Formation's quality rating at the time of generation: higher quality widens the upper bound of the range without raising the floor. See Centre de Formation.

### Maintenance du Centre de Formation
An action available to the User to restore or improve the Centre de Formation's quality rating. Fixed cost (tunable via ConfigurationGlobale/MondeSetting); adds 1 star to the quality rating; takes one half-Saison to complete. A Club can perform at most 2 Maintenances per Saison if quality has degraded sufficiently. Maintenance does not pause Créneau assignment or affect any TennisPlayer already in the Centre de Formation. See Centre de Formation.

### Pool de TennisPlayers
The full population of TennisPlayers that exist within a Ligue. Two sex-separated pools coexist per Ligue — one for the ATP circuit (male) and one for the WTA circuit (female), each approximately 1 000 TennisPlayers at launch, growing with features like the Centre de Formation. At any time, some TennisPlayers are under Contract with a Club; the rest are **libres**. Free TennisPlayers are persistent entities (with Attributs, Prestige, PlayerType, nationality, preferred surface) — not disposable bots. They are auto-inscribed by the system to fill Tournoi draws when not enough contracted TennisPlayers are registered.

**Ligue scoping rules:**
- A libre TennisPlayer belongs to the Ligue they were in when their last Contrat expired (or the Ligue they were generated in, if they have never been under Contrat). They do not change Ligue on their own.
- A TennisPlayer under active Contrat follows their Club/User if the User changes Ligue. Their associated Ligue updates to the User's new Ligue.
- If a Contrat is not renewed at inter-season, the TennisPlayer becomes libre and stays in the Ligue they are currently in (i.e. the User's Ligue at the time of expiry) — regardless of whether the User is being promoted, demoted, or staying. A User who changes Ligue without renewing a Contrat loses that TennisPlayer to the Ligue they are leaving.

### Contrat (Contract)
The link between a Club and a Personne. Has a `type` (`TENNIS_PLAYER` | `JUNIOR` | `SCOUT` | `PREPARATEUR_PHYSIQUE` | `PREPARATEUR_TECHNIQUE` | `PREPARATEUR_MENTAL` | `PREPARATEUR_TACTIQUE` | `MEDECIN` | `KINE` | `COMMERCIAL` | `AGENT`) that determines the Personne's active role. A Personne holds at most one active Contrat at a time.

**JUNIOR Contrats** are for TennisPlayers aged 16–17 signed to a Club's Centre de Formation. Duration: **1 Saison**, renewable each Saison. Expires automatically when the TennisPlayer turns 18 — at that point the User may sign a standard `TENNIS_PLAYER` Contrat (if a main roster slot is available) or the TennisPlayer becomes libre on the adult circuit. A junior cannot participate in adult Tournois while holding a `JUNIOR` Contrat. A `JUNIOR` Contrat **cannot be résilié mid-Saison** — the only exit is non-renewal at Pré-Saison. The User decides at the start of each Saison whether to renew; if not renewed, the junior enters the Marché Libre.

**TENNIS_PLAYER Contrats** have a duration of **1 to 3 Saisons**, chosen by the User at signing (from Marché Libre, Enchères, or renewal). Minimum age to sign a `TENNIS_PLAYER` Contrat: **16**. A TennisPlayer aged 16–17 signed on a `TENNIS_PLAYER` Contrat joins the main Club roster and competes in the adult circuit — they bypass the Centre de Formation entirely. The Salaire is fixed for the full duration. Renewal at the natural end of a Contract requires a small **frais de renouvellement** (fee); amount is tunable via ConfigurationGlobale. A TennisPlayer without a Contrat is **libre** (free agent).

**Personnel Contrats** (Scout and others) are **1 Saison**. Use the same Enchères/Marché Libre mechanics as TennisPlayers, including the **same Pré-Saison window** — but in **separate pools per Contrat type** — Personnel are never mixed with TennisPlayers in the same market view. Each Contrat type has its own Enchères pool (Pré-Saison) and its own Marché Libre (permanent).

Strategic tension: signing a young TennisPlayer on a 3-season contract locks in a low Salaire while they develop, but committing to 3 seasons on a player who later regresses or underperforms is costly to exit.

The User can decide to **résilier** (terminate) a Contract at any time, but the TennisPlayer always finishes the current Saison — there is no mid-season immediate departure. At Pré-Saison, the TennisPlayer enters the Enchères like any other non-renewed TennisPlayer. Résiliation of a Contract that still has remaining Saisons requires a **financial compensation** equal to `remaining Saisons × annual Salaire × compensation coefficient`. The compensation coefficient defaults to 1 in ConfigurationGlobale (overridable per Monde); a Personnel Agent may negotiate a lower coefficient at signing. Non-renewal at the natural end of the Contract incurs no compensation.

**Exception — MyPlayer Club fictif:** the `TENNIS_PLAYER` Contrat between a TennisPlayer and their Club fictif in the MyPlayer sub-mode has an **indefinite duration**. It carries no Salaire, no renewal fee, no Résiliation mechanism, and no expiry. It ends only at Retraite.

A persistent UI indicator reminds the User of any TennisPlayer in their **final Saison of Contract** whose renewal decision is still pending, or any TennisPlayer for whom the User has initiated a résiliation. No confirmation is required in intermediate Saisons — a 3-season Contract runs 3 seasons without friction. There is no tacit reconduction.

A TennisPlayer whose Contract is not renewed or has been résilié enters the **Enchères** during Pré-Saison; if unsold, they fall back to the **Marché Libre**.

### Salaire (Wage)
The recurring cost a Club pays to a Personne under Contrat (TennisPlayer or Personnel). Deducted from the Club's Budget once per Semaine. The only acquisition cost of signing from the Marché Libre. Higher-quality TennisPlayers command higher Salaires. Personnel Salaires follow the same deduction cadence.

### Pré-Saison
The period between the end of Saison N and the start of Saison N+1. Sequence:
1. Renouvellement and ListeAttente windows close — inactive Users are identified, Ligue promotions are resolved.
2. TennisPlayers whose Contrats were not renewed enter the Enchères.
3. Enchères close — unsold TennisPlayers move to the Marché Libre.
4. Saison N+1 starts — calendar is published, special inscription window opens.

The Marché Libre remains open throughout Pré-Saison and all of Saison N+1.

**Mode Solo:** Pré-Saison does not exist in `SOLO` Mondes. Signing is permanent — a TennisPlayer becomes available as soon as their Contrat ends, and can be signed immediately.

### Enchères (Auction)
The mechanism for acquiring previously contracted TennisPlayers during Pré-Saison. Sealed-bid format: each Club submits a Salaire offer that is not visible to other bidders. The highest Salaire offer at close wins. A Club can see how many other Clubs have placed a bid on a given TennisPlayer (participation count, not amounts), and can revise their own offer up or down at any time before the Enchères close. Tie-break: if two Clubs offer the same Salaire, the first to have submitted that amount wins. After close, the winning Salaire is publicly visible — allowing Users to calibrate future bids. Only available during Pré-Saison.

### Marché Libre (Free Market)
Instant-sign market available at any time throughout the Saison and Pré-Saison. TennisPlayers here are generally weak (either newly generated or unsold after Enchères). No acquisition cost — only the ongoing Salaire. Free junior TennisPlayers (aged 16–17) are also present on the Marché Libre. A Club can sign them in one of two ways: as a `JUNIOR` Contrat (Centre de Formation, if the TennisPlayer is under 18) or as a standard `TENNIS_PLAYER` Contrat (main roster, available for TennisPlayers aged 16 and above). The User chooses based on perceived talent and roster availability; prior Scouting is not required but informs the decision.

### Onboarding
When a User joins a Ligue, they receive 4 TennisPlayers automatically — 2 per circuit:
- One competitive ATP TennisPlayer (top-100 level) ready to play immediately.
- One young ATP TennisPlayer with high potential, to develop over time.
- One competitive WTA TennisPlayer (top-100 level) ready to play immediately.
- One young WTA TennisPlayer with high potential, to develop over time.
All 4 TennisPlayers are created and added to their respective Ligue Pool at the moment of inscription, then immediately placed under Contrat with the new Club.

### PlayerType
A trait assigned to a TennisPlayer that shapes their training progression and suggests recommended Tactic presets. Examples: `Defender`, `Serve & Volley`, `Baseline`, `All-Court`. A PlayerType influences which Attribute domains progress faster during training — a TennisPlayer trained against their PlayerType progresses more slowly in mismatched domains. PlayerType can evolve naturally through play or be forced by the User at their own strategic risk.

### Tactique (Tactic)
The game plan applied to a TennisPlayer during a Match. Composed of named presets (e.g. `AGGRESSIVE_BASELINE`, `DEFENSIVE_COUNTER`, `SERVE_AND_VOLLEY`) which are configurations of underlying parameters. The User can use a preset as-is or fine-tune the parameters manually. A PlayerType suggests recommended presets but does not restrict the User's choice.

Two scopes exist:
- **Tactique permanente** — the default Tactic configured on the TennisPlayer, persisted.
- **Tactique en match** — an in-match override, ephemeral by default. Saved only if the User explicitly chooses to persist it at the end of the Match.

### Attributs
The grouped numerical stats of a TennisPlayer. Organized in four categories:

- **Physique** (7) — Vitesse latérale, Vitesse avant-arrière, Agilité, Jeu de jambes, Endurance, Équilibre, Récupération inter-points. Degraded by the Fatigue dynamic state. **Endurance** acts as a resilience coefficient: it modulates how strongly high Fatigue reduces effective Attributs Physique during a Match.
- **Technique** (31) — organized into seven sub-families: *Service* (Puissance, Précision, Fiabilité, Second service, Variété); *Retour* (Retour coup droit, Retour revers, Lecture de service); *Coup droit* (Puissance, Précision, Régularité, En course); *Revers* (Puissance, Précision, Régularité, En course); *Volée* (Coup droit, Revers, Réflexes au filet, Toucher au filet, Smash, Couverture du filet); *Défense* (Contre, Glissade, Passing, Lob, Remise difficile); *Effets* (Lift, Slice, Amortie coup droit, Amortie revers). Degraded by the Rythme dynamic state.
- **Mental** (4) — Clutch, Concentration, Confiance, Combativité. Degraded by the Moral dynamic state. **Confiance** acts as a resilience coefficient: it modulates how strongly low Moral reduces effective Attributs Mental during a Match.
- **Intelligence de jeu** (6) — Lecture du jeu, Placement, Choix des coups, Construction du point, Vision du court, Exploitation des faiblesses. Not affected by any dynamic state (Fatigue, Rythme, Moral) — these Attributs remain stable regardless of competitive rhythm, physical condition, or emotional state. Developed through dedicated Exercices.

Each Attribut has a value on a fixed **1–99 scale**. The set of Attributs, their sub-families, and their scale are fixed across all Mondes — they form the simulator contract and cannot be overridden by MondeSetting. Do not call these "stats" or "notes" — use "Attributs".

### Rythme (Match Rhythm)
A dynamic state of a TennisPlayer that represents competitive sharpness. Rises with Matches played (Tours). Decays naturally each Semaine without a Match — not through Repos or other Activités. Affects all **Attributs Technique** (the 31 Attributs across the Service, Retour, Coup droit, Revers, Volée, Défense, and Effets sub-families): high Rythme improves technical precision and timing; low Rythme reflects the rustiness of a player returning after a long break from competition. Creates strategic tension with Fatigue: playing more keeps Rythme high but raises Fatigue; resting recovers Fatigue but lets Rythme decay. Do not confuse with Moral (→ Mental) or Fatigue (→ Physique) — each dynamic state targets a distinct Attribut category.

Stored and displayed in the WebApp as a percentage bar (0–100%). Transmitted to the simulator as a normalised coefficient (0.0–1.0). Decay rate and effect thresholds are tunable via ConfigurationGlobale/MondeSetting.

### Fatigue
A dynamic state of a TennisPlayer. Rises with Matches played and Entraînement Activités, falls with Repos Créneaux. High Fatigue increases the risk of Blessure and reduces effective **Attributs Physique** (Vitesse latérale, Vitesse avant-arrière, Agilité, Jeu de jambes, Endurance, Équilibre, Récupération inter-points) during a Match. Distinct from the intra-match fatigue computed by the simulator point-by-point (which is ephemeral and not persisted between Matches).

Stored and displayed in the WebApp as a percentage bar (0–100%). Transmitted to the simulator as a normalised coefficient (0.0–1.0). Effect thresholds (e.g. Blessure trigger probability) are tunable via ConfigurationGlobale/MondeSetting.

### Moral
A dynamic weekly state of a TennisPlayer. Rises with wins and certain Activités, falls with losses or overtraining. Influences effective Attributs during a Match, primarily the **Mental** category. Managed by the User through weekly Activité choices.

Stored and displayed in the WebApp as a percentage bar (0–100%). Transmitted to the simulator as a normalised coefficient (0.0–1.0). Effect weights are tunable via ConfigurationGlobale/MondeSetting.

### Blessure (Injury)
A temporary health state of a TennisPlayer triggered by two independent mechanisms: (1) **probabilistically when Fatigue is elevated** — the higher the Fatigue at the time of a Match, the greater the chance of injury occurring during that Tour; (2) **probabilistically when a glissement accidentel occurs** during a Match on a wet outdoor surface — independent of Fatigue, configurable per surface in ConfigurationGlobale. Applies an in-match Attribut malus but does **not** prevent participation in a Tournoi. Distinct from Maladie: a Blessure is physical, has no contagion risk, and is the direct consequence of poor Fatigue management. A Kiné accelerates recovery (reduces the number of Créneaux before the malus clears). The design intent is deliberate: Maladie is bad luck (unavoidable), Blessure is bad management (avoidable). Fatigue threshold and probability curve are tunable via ConfigurationGlobale/MondeSetting.

### Maladie (Illness)
A temporary health state of a TennisPlayer triggered by **pure random chance** — independent of Fatigue, calendar density, or any other game state. Applies an in-match Attribut malus but does **not** prevent participation in a Tournoi. Carries a **contagion risk**: if left untreated, other TennisPlayers in the same Club may become sick. A Médecin accelerates recovery (a top-quality Médecin can resolve a minor illness in as few as 2 Créneaux) and reduces contagion probability. The randomness is intentional — a Médecin is insurance against an unavoidable event, not a preventive tool. Trigger probability and contagion rates are tunable via ConfigurationGlobale/MondeSetting.

### Ligue
A subdivision of a Monde that groups approximately 100 Users into an isolated competitive pool. Each Ligue has two independent ranking leaderboards — one for the ATP circuit (male TennisPlayers) and one for the WTA circuit (female TennisPlayers). Points in each circuit are scoped per Ligue and reset to zero when a User changes Ligue. Activity-based demotion (Renouvellement) is a single Club-level decision — a User is either active or AFK for the whole Club, regardless of how many circuits they manage. **Note:** "ATP" and "WTA" are working names pending IP review; tournament and circuit names will use fictional equivalents before launch.

Ligues are numbered (Ligue 1, Ligue 2, etc.). Ligue 1 contains the most active Users. New Ligues are created as the Monde grows.

Prestige is global and portable across Ligues.

**Membership rules:**
- A User must renew their Ligue membership during the Renouvellement window (mid-Saison to end of Saison). Non-renewal = inactive status = immediate demotion of one Ligue at Renouvellement close, as long as there are Users on the ListeAttente waiting for the spot. This repeats each Saison until the User renews or reaches the bottom Ligue.
- To move up, a User joins the **ListeAttente** (waitlist) for the higher Ligue. A User requesting promotion is placed in the highest Ligue that has no inactive (AFK) Users.
- There is no ranking-based relegation — only activity-based demotion.
- When a User changes Ligue (demotion or promotion), their Club, Contrats, and TennisPlayers follow. ATP points reset to zero. Prestige is global and unaffected by Ligue changes. At the inter-season (Pré-Saison) when the Ligue change takes effect, all of the User's TennisPlayers receive a Moral penalty (near-zero) reflecting the disruption of relegation.

**Design intent:** The Ligue system rewards *assiduity*, not level. A User can have a long-term strategy with young TennisPlayers who score few points and still stay in Ligue 1, as long as they remain active.

**IP note:** "ATP" and "WTA" are working names. Circuit names, Catégorie labels (Grand Chelem, Masters, ATP 500, etc.), and tournament names must be replaced with fictional equivalents before any public release. The architecture and code are circuit-agnostic — the IP risk materialises at the point of populating the DEFAULT data in ConfigurationGlobale (tournament calendar, Catégorie assignments, circuit names). No architectural change required; this is a data authoring task.

### ListeAttente (Promotion Waitlist)
The mechanism by which a User requests promotion to a higher Ligue. Opens simultaneously with the Renouvellement window at **mid-Saison N**, and closes at the end of Saison N. First-come, first-served. A User on the ListeAttente is placed in the highest Ligue without inactive Users when spots become available (resolved at Pré-Saison). Users are notified at Saison start and again the real-world day before the window closes.

### Renouvellement (Membership Renewal)
A Ligue membership confirmation window open from **mid-Saison N to the end of Saison N**. A User who does not renew before the window closes is marked inactive and begins a progressive demotion to lower Ligues. Renewal is a presence signal — do not call it "abonnement" or "subscription".

Notifications: Users are reminded at the start of Saison N that the window will open at mid-season, and again the real-world day before the window closes.

### MondeSetting
The configuration entity attached to a Monde. Holds both structural parameters and tunable game-balance values that can be overridden per Monde. Structural parameters include:
- `type`: `SOLO` | `MULTI`
- `cadence`: `REAL_TIME` | `CRÉNEAU_PAR_JOUR` | `USER_CONTROLLED`
- `value_cadence`: number of Créneaux processed per real-world day when cadence is `CRÉNEAU_PAR_JOUR` (e.g. `2` = one game Semaine lasts 7 real days; `4` = one game Semaine lasts 3.5 real days). Unused for `REAL_TIME` and `USER_CONTROLLED`. A Semaine always contains exactly 14 Créneaux regardless of cadence — the cadence only controls how fast they are processed. Tours are never scheduled in the dead-of-night window (0h–6h) in `REAL_TIME` and `CRÉNEAU_PAR_JOUR`; irrelevant in `USER_CONTROLLED`.

`USER_CONTROLLED` is exclusively for `SOLO` Mondes. The User manually drives time forward: Créneau by Créneau, day by day, to a specific date, or to the next Match. There is no real-time clock — the Monde advances only when the User triggers it.

Tunable parameters (Exercice effects, Tournoi config, progression rates, etc.) have global defaults defined in the ConfigurationGlobale. MondeSetting can override any of these for a specific Monde — enabling test Mondes used for rebalancing. **Exception:** parameters that form the simulator contract (i.e. the Attribut model consumed by the simulation engine) are fixed across all Mondes and cannot be overridden in MondeSetting.

Distinct from the Monde entity itself — the Monde is the game world, MondeSetting is its ruleset.

In a `SOLO` Monde, the Ligue system is architecturally present and operates as in a `MULTI` Monde (pool scoping, Tournoi ranking thresholds), but is **never surfaced in the Solo UI**. The social and competitive Ligue mechanics (Renouvellement, ListeAttente, promotion/demotion) do not apply — there are no other Users to compete against for Ligue spots.

### ConfigurationGlobale
The server-wide default set of tunable game parameters from which new Mondes are initialised. A Monde generated in `DEFAULT` mode uses ConfigurationGlobale values unchanged. MondeSetting overrides specific parameters for a given Monde without modifying ConfigurationGlobale. Think of ConfigurationGlobale as the template and MondeSetting as the per-instance diff.

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

### Désinscription
A voluntary withdrawal from a Tournoi **before it has started** (no Tours played). No Moral penalty. Distinct from Abandon. Can occur when a User accepts a Wildcard to a more prestigious Tournoi, triggering automatic Désinscription from the lower-prestige one.

### Abandon
A voluntary withdrawal by the User of a TennisPlayer from an ongoing Tournoi (Tours already in progress). Consequences:
- **Moral decreases** (penalty for quitting mid-competition).
- **Rythme is unaffected** (the TennisPlayer played rounds before withdrawing).
- **ATP points are kept** for all Tours completed before the Abandon — no retroactive penalty.
- The TennisPlayer cannot participate in another Tournoi in the current Semaine.
- The 3-Semaine inscription rule naturally prevents re-inscription in the same or another Tournoi on short notice.
- Freed Créneaux in the current Semaine fall to Plan de Secours or mandatory Repos.

### Wildcard
An invitation extended to a TennisPlayer to enter a Tournoi without the standard 3-Semaine advance inscription deadline, typically following a strong recent performance. Each Tournoi has a fixed quota of Wildcard slots. Candidates are selected algorithmically (ELO, recent results, etc.).

- If the TennisPlayer belongs to a User: the User explicitly accepts or declines the invitation.
- If the TennisPlayer is an NPC: the system decides algorithmically (e.g. based on Fatigue).
Wildcards are issued in two waves:
1. **At inscription close (~75% of quota)** — candidates ranked by a composite score combining Ligue ranking and recent performance (Tours reached in recent Semaines, weighted by Catégorie). Weighting between the two components is data-driven and tunable via ConfigurationGlobale/MondeSetting. User TennisPlayers receive an invitation; NPCs are decided algorithmically using the same score.
2. **Rolling over the 2 Semaines following inscription close (~25% of quota)** — additional Wildcards generated as TennisPlayers post strong results in other ongoing Tournois during that window; same composite score applied to the updated snapshot.

If quota is not filled after both waves, the NPC waitlist fills remaining slots automatically.

**Conflict with an existing inscription:** A User may receive a Wildcard for Tournoi A while their TennisPlayer is already inscribed in Tournoi B. The User may accept only if Tournoi A is **more prestigious** than Tournoi B (see Catégorie de Tournoi). If accepted, the TennisPlayer is automatically désinscrit from Tournoi B — this is **not** an Abandon (no Moral penalty) because the Tournoi had not yet started. A Wildcard invitation is never sent to a TennisPlayer who is already mid-Tournoi (Tours in progress).

Bypasses the inscription deadline as a special case. Eligibility criteria (what "strong performance" means) are tunable and TBD.

### Plan de Secours (Fallback Plan)
A pre-configured set of Activités that fills the Créneaux freed by an early elimination from a Tournoi. Defined by the User before the Tournoi begins. Can be filled manually or via a preset. If no Plan de Secours is set and the TennisPlayer is eliminated before the last Tour of the Semaine, the freed Créneaux default to mandatory Repos. The Plan de Secours draws from the Activités available in the TennisPlayer's current Contexte (`EN_TOURNOI`) for the remainder of that Semaine.

### Contexte (Player Context)
The situational state of a TennisPlayer for an entire Semaine. Determines which Activités are available for each Créneau within that Semaine. Two rules:
- `EN_TOURNOI` — automatic, set when the TennisPlayer has Tours scheduled in that Semaine (derived from Tournoi inscription). Cannot be overridden by the User.
- `HORS_TOURNOI` — default when not in a Tournoi. The User can override this to another Contexte if additional options exist (e.g. `EN_CAMP` in the future).

Do not model Contexte as a persisted field on the TennisPlayer — it is resolved per Semaine from the schedule and User choices.

### Prestige
A global, career-long accumulation of points for a TennisPlayer that persists across Ligue changes and is never reset. Distinct from ATP points (which are Ligue-scoped and reset on Ligue change) — Prestige reflects total career achievement across a Monde. Enables the notion of "legendary" TennisPlayers who have dominated a Monde over many Saisons. Not a simulator input — it is a meta-game recognition layer.

Three sources of Prestige accumulation:
1. **Tournoi results** — wins and Tours atteints, weighted by Catégorie (Grand Chelem win > Masters win > ATP 500, etc.). ATP points and Prestige are calculated from the same event with separate formulas.
2. **Milestones** — career thresholds trigger Prestige awards independently of Tournoi results (e.g. 50 wins in a Catégorie, 10 Grand Chelem titles). Milestone criteria are data-driven and extensible.
3. **Surface dominance** — Prestige is tracked per surface as well as globally, enabling "legendary on clay" profiles (e.g. a Rafa Nadal analogue). Surface-specific Prestige accumulates from Tournoi results and milestones on that surface.

Exact point values, milestone thresholds, and surface breakdown are tunable via ConfigurationGlobale/MondeSetting.

Prestige is surfaced to Users in three ways:
- **Leaderboard global** — top-N TennisPlayers by total Prestige, visible from the Monde. Two views: active TennisPlayers only, and all-time (including retired). Updated each Saison.
- **Leaderboard par surface** — same ranking scoped to a specific surface (clay, grass, hard, indoor hard). Same two views.
- **Badges** — displayed on the TennisPlayer profile and in match contexts. Two independent families of criteria, both data-driven and extensible: (1) **Prestige seuil** — awarded when cumulative Prestige (global or per-surface) crosses a threshold, recognising sustained careers; (2) **Milestone d'événement** — awarded when a specific event count is reached (e.g. 10 Grand Chelem titles, 50 wins in a Catégorie), recognising concrete performance peaks. A TennisPlayer may hold Badges from both families simultaneously.

When a TennisPlayer retires, their profile (Prestige, Badges, career record) is archived and remains permanently visible in the Hall of Fame of their Monde. See Hall of Fame.

### Compétition Interclub *(future)*
A Club-vs-Club team event distinct from individual Tournois. Will exist in both Multi and Mode Solo (optional in Solo). Does not consume Fatigue or interact with the individual Tournoi calendar — it is designed to be loosely coupled from the core management loop. Exact format (team size, frequency, scoring) is TBD and can be defined post-launch. The primary purpose is to add competitive structure between Clubs and give NPC Clubs meaningful presence beyond filling Tournoi draws.

### Traits *(future)*
Qualitative situational effects attached to a TennisPlayer — can be positive (e.g. `Grand Serveur`, `Joueur de Pressure`) or negative (e.g. a disposition to sustain Blessure more easily at elevated Fatigue). No numerical value — they activate in specific match contexts. Hidden by default; revealed progressively through Scouting: the count of Traits is revealed first, then full details are discovered with further Scouting investment. A negative Trait that modifies injury risk acts as a coefficient on the Blessure probability curve — it does not change the Fatigue trigger mechanic, only the probability. Distinct from Attributs. Not yet implemented.

### Priorité d'Inscription
A mechanism allowing a User to register their TennisPlayer for multiple Tournois scheduled in the same Semaine, ordered by preference. All Tournois in the same Semaine share a common inscription close date. At close, the system resolves the list in priority order in a single batch: if the TennisPlayer is accepted into the highest-priority Tournoi, all lower-priority inscriptions are discarded. If rejected (e.g. ranking-based slot limit), the system moves to the next Tournoi in the list. The User is guaranteed at most one active Tournoi inscription per TennisPlayer per Semaine after resolution. Special case: when a TennisPlayer is attempting Qualifications for a Tournoi in Semaine S, the User may simultaneously submit a Priorité d'Inscription for Semaine S+1 (the main draw Semaine). If the TennisPlayer qualifies, the S+1 fallback is cancelled automatically. If not, the S+1 fallback activates.

### Catégorie de Tournoi
The prestige tier of a Tournoi. Uses real ATP nomenclature as the default: **Grand Chelem → Masters → ATP 500 → ATP 250 → Challenger Premium → Challenger → Future** (ordered highest to lowest), plus **Circuit Junior** — a parallel circuit for TennisPlayers aged 16–17, with its own Tournoi calendar and no direct ranking interaction with the adult circuit.

Catégorie determines:
- **Wildcard prestige condition** — a Wildcard invitation is only valid if the target Tournoi has a higher Catégorie than the one the TennisPlayer is already inscribed in.
- **Ranking ceiling** — `CHALLENGER_PREMIUM` and `CHALLENGER` each carry two configurable thresholds (tunable via ConfigurationGlobale/MondeSetting): a **hard block** (top X% of Ligue ranking — blocked from entering even via Wildcard) and a **Wildcard-only zone** (top Y% to X% — can receive a Wildcard but cannot inscribe directly). `CHALLENGER_PREMIUM` has a wider Wildcard-only zone (equivalent to IRL Top 11–50 access via invitation); `CHALLENGER` blocks the same high-ranked TennisPlayers entirely from both inscription and Wildcard. `FUTURE` carries only a hard block threshold with no Wildcard-only zone. Exact percentile thresholds are data-driven and tunable.
- **ATP point scale** — higher Catégorie awards more points per Tour reached.

The full Tournoi calendar and Catégorie assignments are configurable per Monde (via ConfigurationGlobale and MondeSetting). The default config uses the real ATP calendar.

### Qualification
A pre-Tournoi mini-tournament that fills the qualification slots of a Tournoi draw, played the **Semaine before** the main draw (Semaine S). TennisPlayers who do not rank high enough for a direct draw slot compete in Qualifications for the remaining places. Qualification slots are awarded to the best-ranked candidates who missed the direct cut (User and NPC TennisPlayers compete against each other in this tier).

The system warns the User at inscription time when their TennisPlayer is likely to go through Qualifications, because:
1. The TennisPlayer is committed to Semaine S (qualif Semaine) regardless of outcome.
2. For Semaine S+1 (main draw Semaine): the User can submit a Priorité d'Inscription in parallel. If the TennisPlayer qualifies, the S+1 fallback inscription is cancelled (Désinscription, no penalty). If the TennisPlayer fails to qualify, the S+1 fallback inscription activates normally.

The number of Tours in the qualif Semaine is set by `toursQualification`, a configurable property on the Tournoi (same data-driven model as `toursParSemaine`). Default values per Catégorie are defined in ConfigurationGlobale and overridable per Monde in MondeSetting.

### Ville
The geographic entity representing the physical location of a Tournoi. A Ville carries a climate profile: one set of typical weather tendencies per Période Climatique (vent intensity range, precipitation frequency and intensity), plus an optional Saison des Pluies overlay. Multiple Tournois can be held in the same Ville — the climate profile applies to all editions. Historical Météo records (Météo effective from previous Saison editions) are stored per Tournoi and linked to the Ville. Default climate profile values are seeded from real-world historical weather data and are tunable per Monde via MondeSetting. Do not use "city" or "location" — use "Ville".

### Période Climatique
The meteorological season label used in a Ville's climate profile. Four values: `ÉTÉ`, `AUTOMNE`, `HIVER`, `PRINTEMPS`. Each defines typical vent intensity ranges and precipitation frequency/intensity for a given Ville. Do not confuse with Saison (the competitive cycle) — a Période Climatique is a weather descriptor, not a game period.

### Saison des Pluies
An optional overlay on a Ville's climate profile that defines a window of significantly elevated precipitation probability and intensity, bounded by game-Semaine start and end within the Saison calendar. Stacks on top of the standard Période Climatique profile — precipitation during this window is the base profile value multiplied by a configurable intensity coefficient. Not all Villes have a Saison des Pluies. Cannot be modelled as a fifth Période Climatique — it can span multiple Périodes Climatiques (e.g. a tropical monsoon covering late PRINTEMPS through early ÉTÉ). Tunable per Monde via MondeSetting.

### Tournoi
A competitive event within a Saison that TennisPlayers enter by User inscription. Configuration properties:
- `format`: `ELIMINATION_DIRECTE` | `POULE_PUIS_ELIMINATION`
- `matchFormat`: `BEST_OF_3` | `BEST_OF_5` (e.g. Grand Chelem = best of 5, other Tournois = best of 3)
- `toursParSemaine`: an ordered list specifying how many Tours belong to each Semaine (e.g. `[4, 3]` for a Grand Chelem spanning 2 Semaines — Tours 1–4 in Semaine 1, Tours 5–7 in Semaine 2). Single-Semaine Tournois have a list of one element. This property drives the Semaine advancement condition: a Semaine advances when all Tournois scheduled in it have completed their allocated Tours.
- `slotsRankés`: draw slots reserved for TennisPlayers who meet the direct ranking cut, accepted automatically at inscription close.
- `slotsQualification`: slots filled through a Qualification mini-tournament for TennisPlayers who missed the direct cut, awarded to the best-ranked candidates in that tier.
- `slotsWildcard`: slots filled through the Wildcard system.
- `toursQualification`: number of Tours in the Qualification mini-tournament for this Tournoi (e.g. 3 for Grand Chelem, 2 for ATP 250). Configurable per Tournoi; defaults per Catégorie come from ConfigurationGlobale and are overridable in MondeSetting.
- `ville`: the Ville where the Tournoi takes place. Links the Tournoi to its climate profile and historical Météo records. See Ville and Météo.
- `surface`: the playing surface of the Tournoi (`CLAY` | `GRASS` | `HARD` | `INDOOR_HARD`). Affects ball bounce and movement physics in the simulator. Drives surface-specific Prestige accumulation and informs PlayerType/Tactique recommendations for the User.
- `protection_du_terrain`: the Protection du terrain rating of the Tournoi. A static value defined in ConfigurationGlobale per Tournoi edition. See Protection du terrain.

Inscription requires at least **3 game Semaines** before the first Semaine in which the TennisPlayer could play their first Match — i.e. 3 Semaines before the Qualification Semaine if Qualifications exist, or 3 Semaines before `toursParSemaine[0]` otherwise. Exception: at Saison start, a special inscription window opens with the publication of the calendar, with no advance deadline. Inscription is to the Tournoi as a whole — not per Semaine.

Composed of one or more Phases.

### Météo
The weather conditions associated with a Créneau de Tournoi on an outdoor surface (`CLAY`, `GRASS`, `HARD`). All Matches within the same Tour share the same Météo. Not applicable to `INDOOR_HARD` — all Météo inputs to the Simulator are zero/false for indoor Tournois.

Four progressive states, surfaced to the User in sequence:

1. **Météo précédente** — the Météo effective of the previous Saison's edition of this Tournoi. Available immediately from calendar publication. Falls back to the Ville's Période Climatique default in Saison 1 of a Monde (no prior edition exists).
2. **Prévision initiale** — generated 8 Semaines before the first Match. Represented as qualitative labels: `ENSOLEILLÉ | NUAGEUX | PLUIE_LÉGÈRE | PLUIE_FORTE` for precipitation and `CALME | VENTEUX | TRÈS_VENTEUX` for vent (with a numerical range available on hover in the UI). Low accuracy — may change significantly before the match.
3. **Prévision affinée** — generated 1 Semaine before the first Match. Same label format, higher accuracy. May differ substantially from the Prévision initiale.
4. **Météo effective** — generated at the start of the Créneau on match day. Precise values transmitted to the Simulator: `VENT_MOYEN` (direction + intensité), `surface_wetness_initial` (float, 0.0–1.0), and `precipitation_active` (bool). See Contrat du Simulateur in the Simulator context.

In Solo Mondes, Météo generation parameters are tunable via MondeSetting. The Sandbox sub-mode allows direct override of Météo effective for any Créneau.

### Protection du terrain
A static property of a Tournoi representing the quality of court protection against precipitation — bâche coverage, drainage infrastructure, and surface treatment. Stored as a float (0.0–1.0) in ConfigurationGlobale per Tournoi edition; cannot be upgraded during play (evolution deferred to future versions). Displayed to the User as a qualitative label derived from the float value (thresholds configurable via ConfigurationGlobale) — shown only in the detailed Tournoi information view, not in list or summary contexts.

Acts on two physical parameters transmitted to the Simulator in the Contrat du Simulateur:
1. **Plafonnement de `surface_wetness_initial`** — caps the starting court wetness at match time, regardless of how much it rained before. A high value means the court starts drier even after heavy overnight rain.
2. **`surface_drying_rate`** — a higher value multiplies the base drying rate (how fast the court surface dries between Points when `precipitation_active = false`). The effective rate is computed by the WebApp (ConfigurationGlobale base rate × protection coefficient) and transmitted to the Simulator.

Does not apply to `INDOOR_HARD` surfaces — weather inputs are zero/false for indoor Tournois regardless of protection value. Do not confuse with Réputation du Club (a MyClub-exclusive value) or Qualité du Centre de Formation. Do not use "bâchage" or "drainage" alone — use "Protection du terrain".

### Phase
A stage within a Tournoi that groups Tours under shared rules. Two types:
- `POULE` — round-robin group play. A Match loss does not eliminate the TennisPlayer. The Phase defines how many TennisPlayers qualify per group and the tie-breaking criteria.
- `ELIMINATION` — single elimination bracket. A Match loss eliminates the TennisPlayer.

### Tour (Round)
The atomic progression unit within a Phase. A Tour is a set of Matches played at the same step of the Tournoi. Consistent across both Phase types — "all matches happening in this step". A Tour is also the scheduling unit in accelerated Mondes (1 or 2 Tours per day).

### Match
A single encounter between two TennisPlayers within a Tour. Simulated in either Live or Batch mode. The Match is the unit of work dispatched to the simulation engine.

The WebApp dispatches to the Java simulator worker only. The worker may use an internal Rust `physics-core` for ball physics and Monte Carlo, but this is not a WebApp integration point. The WebApp contract remains the simulator snapshot, `simulatorVersion`, result payload and replay/events schema.

### Live (Simulation Live)
A Match simulation followed in real time by a User. The User can change Tactics mid-match. Events are streamed via WebSocket. The source of truth for live state is Redis. On reconnection, the full event history is replayed from Redis — the client reconstructs the current match state before resuming the real-time stream. The Match continues without interruption regardless of client connection state.

### Batch (Simulation Batch)
A Match simulation that runs in the background without real-time display. Used for unattended matches and mass simulation (e.g. simulating an entire Tournament round). No WebSocket, minimal storage.

### NPC (Non-Player Club)
A Club not managed by any User, used to fill Ligues, Tournoi draws, and Enchères with simulated opponents. NPC TennisPlayers have Attributs and PlayerType, and follow the same simulation rules as User-controlled TennisPlayers. NPC behavior (e.g. whether to accept a Wildcard, whether to bid at Enchères) is driven by configurable heuristics (e.g. Fatigue thresholds, ranking gap). NPCs do not hire Personnel — all decisions that Personnel would inform for a User are handled by heuristics. Do not use "bot" or "IA" — use NPC.

### Catégorie d'Exercice
A grouping of Exercices within the Entraînement Activité. Examples: `Service`, `Physique`, `Mental`. Acts as the mid-level of the Entraînement hierarchy: `Entraînement → Catégorie d'Exercice → Exercice spécifique`.

### Exercice
The leaf-level unit of the Entraînement hierarchy. A User assigns exactly one Exercice to each Créneau d'Entraînement. Each Exercice has a defined Attribut impact profile — one or more Attributs targeted with individual weights. Displayed to the User via two superimposed star layers (★ to ★★★★★) per targeted Attribut:
- **Note Brute** (yellow stars) — the fixed base rating of the Exercice, independent of the TennisPlayer.
- **Note Réelle** (orange stars) — the effective rating for the specific TennisPlayer, after PlayerType modulation. Shown overlaid on the yellow stars. The PlayerType multiplier is not stated explicitly — the User infers it from the gap between the two layers.

The underlying model uses numerical delta values; stars are the UX representation only. The Note Brute of each targeted Attribut maps to a non-linear base delta per Créneau (data-driven via ConfigurationGlobale). Each Exercice declares a **PlayerType affinity** per PlayerType — three discrete levels: `HIGH`, `NEUTRAL`, `LOW` — stored in the catalogue alongside the targeted Attributs. The **Note Réelle** is the product of Note Brute delta × PlayerType multiplier × Préparateur multiplier (if a Préparateur is assigned); it is both the value displayed in the UI and the input to the per-Créneau gain chain. Gains are further modulated by Potentiel, the TennisPlayer's current progression phase, and — secondarily and minimally — Fatigue. See ADR-0016 for the full gain formula.

Exercices are data-driven entities managed through ConfigurationGlobale and overridable per Monde in MondeSetting. The full catalogue (which Exercices exist, their Catégorie, their targeted Attributs, their Note Brute per Attribut, and their PlayerType affinities) can be created, modified, or removed without code changes.

### Potentiel
A label assigned at Personne creation that defines the rate of growth for a given attribute set. Two independent Potentiel values exist per Personne: one for TennisPlayerAttributes growth during Entraînement, one for PersonnelAttributes growth through experience. They are generated independently and may differ (a great TennisPlayer may be a mediocre Scout and vice versa).

**TennisPlayer Potentiel** — defines how much a TennisPlayer gains per Exercice, in two complementary ways: (1) an always-active **base multiplier** applied to every Exercice gain throughout the career — a 5★ TennisPlayer consistently gains more per Créneau than a 3★ one regardless of phase; (2) the **peak multiplier** applied additionally during the Période de Grosse Progression — the amplitude of the peak is itself Potentiel-dependent, making a 5★ TennisPlayer especially dominant during their window. Potentiel also defines the **soft cap threshold**: the global Attribut average at which diminishing returns begin to apply. A higher Potentiel raises this threshold, allowing the TennisPlayer to reach a higher overall plateau before gains slow down. Beyond the threshold, gains continue but with progressive diminishing returns — there is no hard ceiling on individual Attributs. Five values on a star scale (1 to 5); exact label names and all numeric coefficients are data-driven via ConfigurationGlobale. **Not visible by default** — revealed progressively through Scouting as a narrowing range (never resolved to a single value; maximum refinement is a 1-star range). Distinct from Précocité — Potentiel is *how much* a TennisPlayer can grow per session; Précocité is *when* that growth occurs. See ADR-0016.

**Personnel Potentiel** — defines how much a Personne's PersonnelAttributes develop through experience (missions completed as Personnel). Same three values. Not visible to the User until the Personne is active as Personnel.

### Précocité
A label assigned at TennisPlayer creation that defines the timing of a TennisPlayer's development curve. Three values: `Précoce`, `Normal`, `Tardif`. **Not visible by default** — revealed via Tier 2 Scouting (per-player investigation) only. Binary reveal: either the label is known or it is not. Once the Scout accumulates enough investigation Semaines on this TennisPlayer (threshold tunable via ConfigurationGlobale/MondeSetting), the label is revealed in full. No partial or progressive reveal. Determines the age range of the Période de Grosse Progression. A Précoce TennisPlayer peaks early (e.g. 20–24) and has their Progression Normale mainly after the peak; a Tardif TennisPlayer peaks late (e.g. 24–30) and tends to have their Progression Normale before the peak. The exact boundaries within the label's range are randomised at creation and not revealed to the User even after Scouting — only the label is shown.

### Période de Grosse Progression
The ~5-season window (±1–2 seasons, randomised at creation) during which a TennisPlayer progresses fastest. Within this window, an additional **peak multiplier** is applied on top of the Potentiel base multiplier — the amplitude of this peak is itself Potentiel-dependent (a 5★ TennisPlayer gains proportionally more during their peak than a 3★ one). All multiplier values are data-driven and tunable; a flat peak for all Potentiel levels can be configured by setting them to the same value. The age range of this window is determined by Précocité but its exact start and end are not revealed to the User. Transitions into and out of the window are immediate (no ramp).

### Progression Normale
The 2–4 season period of slower but positive Attribut growth that surrounds the Période de Grosse Progression. During Progression Normale, the Potentiel base multiplier applies without the additional peak multiplier — the TennisPlayer progresses at their baseline rate. Duration is randomised at creation. Can be distributed before or after the peak window, or split across both sides. A Tardif TennisPlayer tends to have more Progression Normale before the peak; a Précoce TennisPlayer tends to have it after.

### Personnel
A Personne currently holding a non-`TENNIS_PLAYER` Contrat with a Club (e.g. `SCOUT`, and other types TBD). Does not participate in Matches or consume Créneaux. The User deploys Personnel for a chosen number of Semaines to perform their role. PersonnelAttributes develop through accumulated experience (missions completed). Do not use "Staff" or "Employé" — use Personnel.

A Club may hire multiple Personnel, subject to a configurable maximum per type (e.g. max Scouts per Club) to prevent degenerate accumulation. The ceiling is defined in ConfigurationGlobale and overridable in MondeSetting.

### PersonnelAttributes
The grouped stats of a Personne in their Personnel role. Generated at creation alongside TennisPlayerAttributes, with an independent Personnel Potentiel (growth rate). Not visible to the User while the Personne holds a `TENNIS_PLAYER` Contrat.

Two categories:
- **Innate** — generated at creation, develop slowly with experience but have a low ceiling (e.g. scouting aptitude, Capacité à entraîner). Not correlated with TennisPlayer career performance.
- **Career-influenced** — some PersonnelAttributes evolve through the Personne's active TennisPlayer career. Example: a TennisPlayer with high Mental Attributs progressively develops stronger coaching ability in the Mental domain. The cross-influence weights are tunable via ConfigurationGlobale.

Do not call these "stats" — use PersonnelAttributes.

### Préparateur *(future)*
A type of Personnel specialised in a specific Attribut domain. Four subtypes:

- `PREPARATEUR_PHYSIQUE` — boosts the Note Réelle of Exercices targeting Attributs Physique.
- `PREPARATEUR_TECHNIQUE` — boosts the Note Réelle of Exercices targeting Attributs Technique. Each Préparateur Technique has one or more **sub-specialisations** within the seven Technique sub-families (e.g. a specialist in Service/Retour, or a specialist in Fond de court covering Coup droit and Revers). The multiplier applies only to Exercices within the Préparateur's sub-specialisation(s).
- `PREPARATEUR_MENTAL` — boosts the Note Réelle of Exercices targeting Attributs Mental.
- `PREPARATEUR_TACTIQUE` — boosts the Note Réelle of Exercices targeting Attributs Intelligence de jeu.

Each Préparateur applies a **multiplicateur** to the Note Réelle of Exercices in their domain — the boosted Note Réelle is what the User sees in the UI, so the Préparateur's impact is immediately visible without additional explanation. The multiplier stacks with the existing PlayerType modulation on the Note Réelle. Multiplier values are tunable via ConfigurationGlobale/MondeSetting.

### Médecin *(future)*
A type of Personnel specialised in treating Maladie. Accelerates a TennisPlayer's recovery from illness and reduces the probability of contagion spreading to other Club TennisPlayers. Does not act on Fatigue or Blessure. Contrat type: `MEDECIN`.

### Kiné *(future)*
A type of Personnel specialised in treating Blessure. Accelerates a TennisPlayer's recovery from physical injury. Does not act on Fatigue or Maladie. Contrat type: `KINE`.

### Budget
The Club's financial resources at any point in the Saison. Increased by Cachet (received at the first day of each Saison) and prize money earned in Tournois. Decreased by Salaires (deducted per Semaine), Résiliation compensations, and renewal fees.

A Club may go negative. **Being in deficit blocks** all actions that require spending: Enchères participation, Résiliation (requires compensation), hiring new Personnel, and Contract renewal (which carries a small mandatory fee). **Not blocked by deficit:** signing from the Marché Libre, accepting a Wildcard, Désinscription, and sponsor renegotiations that increase incoming Cachet.

Do not call it "trésorerie", "argent", or "solde" — use "Budget".

### Cachet
The lump-sum payment received by a Club from a Sponsor at the first day of the Saison. Negotiated in advance during the current Saison. Amount is determined by the Sponsor's quality and negotiated terms; a Personnel Agent can improve the amount. Do not confuse with Salaire (which is the Club's outgoing cost for TennisPlayers and Personnel).

### Sponsor
A contractual partner attached to a Club. A Club holds at most one Sponsor per Type de Sponsor per Saison. Each Sponsor provides a Cachet at season start and carries Objectifs de Sponsor. A Sponsor's willingness to renew is assessed at the Verdict de Sponsor (75% of the Saison). Sponsor quality ranges from 1 to 5 étoiles — higher quality yields higher Cachet and harder Objectifs. At Onboarding (first Saison), every Club receives an imposed Sponsor per type with a fixed Cachet and budget.

### Type de Sponsor
The category of a Sponsor partnership. Three types: **Principal**, **Raquette**, **Équipement**. A Club negotiates one Sponsor per type independently. Each type has its own negotiation window and pool of candidates.

### Objectifs de Sponsor
Performance targets attached to a Sponsor contract for the duration of a Saison. A Sponsor carries one or more independent Objectifs. Each Objectif targets a single circuit (ATP or WTA) — there is no compound "AND" Objectif spanning both circuits. A Sponsor whose Objectifs span both circuits yields more Cachet and harder targets, creating an incentive for Users to perform on both sides of their roster.

Drawn from two families:
- **Résultats sportifs** — reach a specific Tour in a Tournoi of a given Catégorie in the specified circuit (e.g. "quarter-final of a Masters WTA"), or win N Matches in the Saison in the specified circuit.
- **Classement** — finish in the top X of the Ligue ranking for the specified circuit, or reach a global ranking threshold within the Monde for the specified circuit.

Higher Sponsor quality implies harder objectives. A Personnel Agent can negotiate easier objectives at signing or renewal. Failing to meet objectives negatively influences the Verdict de Sponsor probability; the severity scales with the gap between result and target. Exact thresholds are data-driven and tunable via ConfigurationGlobale/MondeSetting.

### Verdict de Sponsor
The Sponsor's assessment of Club performance, issued at **75% of the Saison**. Two outcomes: positive or negative. A positive verdict locks in renewal intent — the Sponsor will auto-renew at season end if no renegotiation is initiated, and the User may open renegotiation of Cachet and Objectifs for the next Saison. A negative verdict at 75% can still flip to positive based on the final 25% of results, but it is a risk the User bears. If the verdict remains negative at season end, the Sponsor does not renew and the Club must source a replacement during the next negotiation window.

### Négociation de Sponsor
The process by which a Club selects and agrees terms with a Sponsor for the next Saison. Opens at a recurring window during the current Saison (every X Semaines): the system presents **3 candidates per Type de Sponsor**, each with a quality rating (1–5 étoiles), Cachet, and Objectifs. The User selects one per type or waits for the next window (at the risk of arriving at season start without a Sponsor). A Personnel Commercial increases the probability of high-quality (5-étoile) candidates appearing in the pool. A Personnel Agent can improve the Cachet and soften the Objectifs on the chosen Sponsor before the deal is signed, or renegotiate existing terms at renewal.

### Banqueroute
A voluntary declaration by the User that the Club cannot service its obligations. Can be declared at any point. Consequences: the Club starts the next Saison with a minimal fixed Budget; all current Contrats (TennisPlayers and Personnel) are terminated immediately; the User must rebuild through Enchères or the Marché Libre. The Banqueroute resets only the financial position — Ligue membership, Prestige, and ATP points are unaffected. Do not call it "faillite" — use "Banqueroute".

### Prize Money
Revenue earned by a Club when one of its TennisPlayers wins a Tour in a Tournoi. Distributed per Tour won, scaled by Catégorie de Tournoi — a deep run in a lower Catégorie may yield more than an early exit from a higher Catégorie. Credited to the Club's Budget immediately when the Tour result is confirmed. Exact amounts per Catégorie per Tour are data-driven and tunable via ConfigurationGlobale/MondeSetting. Do not call it "gains" or "revenus" alone — use "Prize Money".

### Prévisionnel
A read-only budget forecast tool available to the User throughout the Saison. Two projections:
- **Prévisionnel garanti** — projects Budget to season end using only confirmed Salaire outflows (no prize money assumed). Shows the worst-case trajectory.
- **Prévisionnel estimé** — projects Budget including statistically expected prize money based on current TennisPlayer Attributs and Tournoi calendar. Computed offline (not real-time). Not a guarantee — informs strategic planning.

### Personnel Commercial
A type of Personnel focused on off-court revenue. Increases the probability of high-quality Sponsors (4–5 étoiles) appearing in the Négociation de Sponsor pool. Does not negotiate individual terms — that is the role of Personnel Agent. Does not interact with TennisPlayer training or Match preparation. Contrat type: `COMMERCIAL`. Note: Personnel Commercial does not act as a transfer intermediary — Enchères remain a direct Club-to-Club competition without negotiation agents.

### Personnel Agent
A type of Personnel specialised in commercial negotiation. Improves the Cachet obtained from a chosen Sponsor and can soften Objectifs de Sponsor at signing or renewal. A Club may hire at most one Personnel Agent. Distinct from Personnel Commercial (which influences the quality pool, not the individual deal). Contrat type: `AGENT`. Note: Personnel Agent operates exclusively in sponsor and commercial contexts — not in TennisPlayer transfers or Enchères.

### Scout
A type of Personnel (Contrat type `SCOUT`) specialised in player discovery. A Scout handles one mission at a time. To cover multiple regions or players simultaneously, the Club must hire multiple Scouts. A Scout can be deployed in two modes, each for a chosen number of Semaines:
- **Sweep régional (Tier 1)** — sent to a region; returns a pool of TennisPlayer candidates with limited information (identity, preferred surface, approximate Attribut ranges per category).
- **Investigation per-player (Tier 2)** — focused on a specific TennisPlayer; each Semaine-deployment refines Attribut range estimates and progresses toward revealing Potentiel label, Précocité label, and Traits. No fixed revelation sequence — reveal depth per deployment scales with Scout quality. See Scouting.

Scout PersonnelAttributes (scouting aptitude) are innate — not correlated with the Personne's TennisPlayer career.

### Scouting
The process of discovering hidden TennisPlayer properties using a Scout. Scouting is the **only** channel that reveals Potentiel label, Précocité label, and Traits — tournament performance never surfaces these dimensions regardless of career length. By contrast, tournament participation progressively reveals Attribut estimates to all Users, but the amount revealed scales steeply with Catégorie de Tournoi: Grand Chelem, Masters, and ATP 500 generate significant public Attribut information; ATP 250 generates very little; Challenger and Future generate essentially none. Qualifications generate less public information than the corresponding main draw at the same Catégorie. TennisPlayers competing exclusively in Challenger, Future, or junior circuits are effectively invisible to all Users without a Scout — Tier 1 is the primary discovery mechanism for these players. Directly facing a TennisPlayer in a Match additionally sharpens Attribut estimates for the User who fielded the opponent. At signing, **all** information about a TennisPlayer is immediately revealed to the acquiring Club regardless of prior Scouting investment.

Two Scouting tiers:
- **Sweep régional (Tier 1)** — broad regional discovery of TennisPlayer candidates with limited initial information.
- **Investigation per-player (Tier 2)** — targeted investigation; each Semaine-deployment refines Attribut range estimates and progresses toward revealing hidden dimensions. Revelation mechanics per dimension: **Potentiel** — narrowing range (never below a 1-star gap); **Précocité** — binary reveal at a deployment threshold; **Traits** — count revealed first, full details revealed with further investment. No fixed cross-dimension revelation sequence — reveal depth per deployment scales with Scout quality.

Accumulated investigation progress belongs to the Club, not the Scout. If a Scout's Contrat ends or the Scout is replaced, the next Scout continues from the Club's existing knowledge state on each target. Attribut estimates accumulated through investigation can become stale as the TennisPlayer progresses or regresses — the Club's range for a target may no longer reflect reality after several Saisons without active investigation. Potentiel labels and Traits, being fixed at creation, do not become stale.

The cost of Scouting is the Salaire of the Scout (Personnel hired per Saison) — there is no additional per-deployment cost.

### Période de Régression
A phase generated at TennisPlayer creation that defines when Attribut decline begins. Regression is a **separate Saison-end stochastic process**, independent of per-Créneau training gains: at the end of each Saison in this phase, a probability check determines whether specific Attributs lose points — and if so, how much. Both probability and intensity increase the longer the TennisPlayer has been in the phase. Per-Créneau training gains and Saison-end regression losses are additive — the net Attribut change over a Saison is their sum, which may be positive (if training outweighs regression) or negative (if regression dominates). Regression probability and intensity are **category-weighted** (Physique regresses fastest; Technique at an intermediate rate; Mental slowly; Intelligence de jeu quasi-never) — all weights are data-driven and tunable via ConfigurationGlobale. The age at which regression starts and its initial rate are randomised at creation and **never revealed to the User** — not through Scouting or any other mechanism. The only signal available is observing Attribut values decline over Saisons. See ADR-0016.

### Retraite (Retirement)
The end of a TennisPlayer's active career. Triggered by two independent mechanisms:

1. **Âge de Retraite Possible** — a retirement age generated at creation, **never revealed to the User** (not through Scouting or any other mechanism). After this age, the probability of the TennisPlayer retiring at the end of the Saison increases each year beyond it. Retirement at this stage is announced mid-Saison ("next season will be my last"), giving the User time to prepare. The mid-season announcement is the only signal the User ever receives.
2. **Attribut plancher** — if the **global average** of all TennisPlayerAttributes falls below a configured minimum threshold, retirement is forced at the end of the Saison regardless of age. No advance announcement in this case. The threshold is a single value tunable via ConfigurationGlobale/MondeSetting.

In both cases, retirement takes effect at the **end of the Saison** — never mid-season. The User cannot force a TennisPlayer into retirement; if they no longer want the TennisPlayer, they use Résiliation.

When a TennisPlayer retires while still under Contrat with a Club, the User may consult their PersonnelAttributes and sign a new Personnel Contrat with them. Otherwise, the Personne exits the active pool. In all cases, the TennisPlayer's profile is archived in the Hall of Fame of their Monde.

### Hall of Fame
The permanent archive of retired TennisPlayers within a Monde. Displays each retired TennisPlayer's career record: Prestige (global and per surface), Badges earned, and key milestones. Accessible to all Users of the Monde. Feeds the all-time views of the Leaderboard global and Leaderboard par surface. Do not confuse with the active Leaderboard — the Hall of Fame contains only retired TennisPlayers.

### Mode Solo
The offline, single-player product — distinct from the online Multi game. Paid (to avoid storage overhead from free usage). Creates a Monde of `type: SOLO`. The Ligue system is architecturally present but invisible in the UI; social Ligue mechanics (Renouvellement, ListeAttente, promotion/demotion) are disabled. Three sub-modes:

- **MyClub** — the User manages a Club as in Multi, but without other Users. Same mechanics as Multi (Contrats, Personnel, Tournois, Saisons, Enchères).
- **MyPlayer** — the User creates and follows the career of a single TennisPlayer. Personnel with Contrats are available, but some types are excluded (e.g. no `SCOUT`). See MyPlayer.
- **Sandbox** — no Club required. The User configures the Monde and observes it evolve. No mandatory management loop.

**Persistance:** Solo Mondes are stored server-side (linked to the User's account), enabling cross-device access and eliminating local save risk. A User has a fixed number of Solo Monde slots (quota, tunable via server configuration); creating a new Monde beyond the quota requires deleting an existing one. The quota is the mechanism that keeps storage costs bounded — it replaces the free-tier restriction with a per-account ceiling.

Do not use "Offline", "Carrière", or "Mode Hors-ligne" — use "Mode Solo". Do not use "mode de jeu" to refer to MyClub/MyPlayer/Sandbox — use "sous-mode".

### MyClub
The Mode Solo sub-mode in which the User manages a Club alongside NPC Clubs, without other Users. Full Club mechanics apply (Contrats, Personnel — all types, Tournois, Saisons, Budget, Sponsors) with one major difference: **no Enchères**. TennisPlayer acquisition is governed by the Club's **Réputation** instead.

**No Pré-Saison, no Enchères:** signing is permanent — the User can sign any available (libre) TennisPlayer at any time during the Saison. There is no dedicated inter-season acquisition window. Time is `USER_CONTROLLED`: the User advances the Monde manually (Créneau by Créneau, day by day, to a specific date, or to the next Match). A Mod can be imported at creation to initialise the Monde with custom TennisPlayers, Tournois, and NPC Clubs. See Mod.

**Signing mechanics (configurable):** By default, NPC Clubs compete for the same TennisPlayers (the User makes a Salaire offer; the TennisPlayer accepts or declines based on Club Réputation and offer quality — NPC Clubs may counter). The User can disable NPC competition for a frictionless experience where signing is direct (access gated by Réputation only). Default and constraints are tunable via MondeSetting.

**Contrats:** Identical to Multi — duration 1 to 3 Saisons chosen at signing, same Salaire, same résiliation compensation formula (`remaining Saisons × annual Salaire × compensation coefficient`), same renewal fee at natural end. The strategic tension of locking in a low Salaire on a developing TennisPlayer is preserved.

**NPC Clubs:** A MyClub Monde contains **20 NPC Clubs by default** (fully configurable via MondeSetting or Mod). NPC Club membership has no effect on a TennisPlayer's strength or Attributs — a libre TennisPlayer is mechanically equivalent to one under NPC Contrat. NPC Clubs exist to create competitive structure (inter-Club competition, draw context) and atmosphere, not to gate TennisPlayer quality. Tournoi draws are filled by NPC TennisPlayers whether or not they belong to a Club.

**Réputation du Club:** A MyClub-specific value (does not exist in Multi or other Mode Solo sub-modes) representing the Club's attractiveness to TennisPlayers. Updated each Saison based on results, with more recent Saisons weighted higher. Acts as an access gate: a Club below a certain Réputation threshold cannot sign TennisPlayers above a corresponding quality ceiling (top-ranked TennisPlayers, high-Potentiel juniors). The constraint is configurable — the User can disable it entirely. See Réputation du Club.

### Réputation du Club
A MyClub-exclusive value representing the attractiveness of a Club to TennisPlayers. Updated each Saison using a recency-weighted formula — more recent Saisons have greater influence than older ones. Determines the quality ceiling of TennisPlayers the Club can sign: a low-Réputation Club cannot attract top-ranked TennisPlayers or high-Potentiel juniors regardless of Salaire offered. The Réputation constraint is optional and can be disabled via MondeSetting for a frictionless experience. Do not confuse with TennisPlayer Prestige (career-long accumulation of competitive results).

**Input:** Tours atteints by the Club's TennisPlayers, weighted by Catégorie de Tournoi — a deep run at a Grand Chelem contributes more than a Challenger win. Contributions from both ATP and WTA circuits are aggregated into a single Réputation score. Exact weights per Catégorie per Tour are data-driven and tunable via ConfigurationGlobale/MondeSetting.

### MyPlayer
The Mode Solo sub-mode in which the User creates a single TennisPlayer and follows their career. Time is `USER_CONTROLLED` — same controls as MyClub (Créneau by Créneau, day by day, to a specific date, or to the next Match). A **Club fictif** is automatically created as the contractual container — it holds the TennisPlayer's Contrat and the Contrats of their Personnel entourage. The User does not manage this Club as an organisation; it is an invisible infrastructure entity. A Mod can be imported at creation to initialise the Monde with custom Tournois, NPC Clubs, and MondeSetting parameters. See Mod.

**Création du TennisPlayer:** three modes available at session start, User's choice:
- **Aléatoire** — fully system-generated (identity, Attributs, Potentiel, Précocité, PlayerType).
- **Libre** — full User control over all parameters with no constraints.
- **Contraint** — User controls all parameters but within a budget of points capping the total initial Attribut level. Prevents a fully maxed-out starting TennisPlayer.

**Tournoi inscription:** identical to Multi and MyClub — 3-Semaine advance deadline, Priorité d'Inscription, Wildcards, and Qualifications all apply. The `USER_CONTROLLED` cadence removes time pressure, but the planning mechanics are preserved.

**Available Personnel types:** `PREPARATEUR_PHYSIQUE`, `PREPARATEUR_TECHNIQUE`, `PREPARATEUR_MENTAL`, `PREPARATEUR_TACTIQUE`, `MEDECIN`, `KINE`.

**Excluded Personnel types:** `SCOUT` (no recruiting), `COMMERCIAL` (no Sponsor pool management), `AGENT` (no Sponsor negotiation intermediary).

**Sponsors in MyPlayer:** Sponsors are assigned automatically based on the TennisPlayer's ranking — no pool selection mechanic. The User can negotiate Objectifs de Sponsor directly, which adjusts the Cachet up or down. No Personnel intermediary is needed for this negotiation. See Mode Solo.

**Budget in MyPlayer:** The Club fictif has a real Budget. Inflows: Cachet (Sponsor, ranking-based) and Prize Money. Outflows: Salaires of the Personnel entourage. Being in deficit blocks new Personnel signings. Banqueroute does not exist in MyPlayer.

**End of career:** When the TennisPlayer retires, the MyPlayer session ends. The career is archived in the Hall of Fame of the Solo Monde. The User may then choose to convert the Monde into a Sandbox session to continue observing its evolution.

### Sandbox
The Mode Solo sub-mode in which the User configures a Monde and observes it evolve without taking a Club or management role. No mandatory management loop. Time is `USER_CONTROLLED` — the User advances the simulation manually (including the "advance to a specific point" option for large time jumps). The User can intervene freely at any point: modify TennisPlayer Attributs, alter Contrats, inject new TennisPlayers, or adjust MondeSetting parameters. Can also be entered by converting a finished MyPlayer session (after the TennisPlayer's Retraite). A Mod can be imported at creation to initialise the Monde with custom data. See Mod.

**Durée maximale:** a Sandbox Monde has a configurable maximum number of simulated Saisons (default: 200). The ceiling is tunable via MondeSetting and may be raised as server capacity allows. Configuration scope beyond Saison limit: TBD.

### Mod
A user-importable data file that overrides part or all of the initial content of a Mode Solo Monde at creation. Available for all Mode Solo sub-modes (MyClub, MyPlayer, Sandbox). A Mod is **partial** — each section is optional; absent sections fall back to ConfigurationGlobale defaults. Sections a Mod can define:
- **TennisPlayers** — initial pool with custom identities, Attributs, and optional Portrait component IDs (e.g. real-world player analogues with fictional names). Portrait components must reference valid IDs from the Catalogue de Sprites — no binary image assets are embedded in a Mod.
- **Tournois** — custom tournament calendar, formats, Catégories.
- **Clubs NPC** — custom NPC Club names and identities.
- **MondeSetting parameters** — game-balance overrides (same scope as MondeSetting; the Mod file is the equivalent of the advanced creation UI for these parameters).

Ligues are not part of a Mod — a Solo Monde has exactly one Ligue, which is invisible to the User.

A **template** can be exported by the system, providing a blank-filled schema of all available Mod sections to guide authoring. Community-created Mods (e.g. a real ATP tour pack) are authored and distributed by Users; the developer is not the publisher of that content. Do not use "pack", "preset", or "configuration" to refer to a Mod in this sense — Mod is the canonical term for importable custom data.

### Portrait
The visual representation of a TennisPlayer. A bust illustration (head, shoulders, and torso) in a consistent "semi-realistic illustrated sports portrait" style. Composed once at Personne creation by stacking pre-made sprite layers (body silhouette, face, hair, optional facial hair) and stored permanently in object storage. Served via CDN as a standard image asset — never recomposed or regenerated after initial creation. Two generation paths: (1) automatic random assignment for NPCs, using the Personne's sexe and nationality as seeds for component selection; (2) user-driven composition for MyPlayer via the Créateur de Personnage. Do not use "avatar", "photo", or "image de profil" — use "Portrait".

### Catalogue de Sprites
The fixed set of pre-generated layered assets used to compose Portraits. Produced once during production using a dedicated art pipeline (style reference in Midjourney, then batch generation via Stable Diffusion with a custom LoRA). Organised by component category: body silhouette (per sexe), face (per skin tone × face shape), hair (per style × colour), facial hair (male only). Stored as static assets in object storage alongside Portraits. Never generated at runtime — all portrait composition at runtime draws exclusively from this pre-built set. Do not call it "sprite sheet" (these are individual layered assets, not a packed sheet) or "asset library".

### Créateur de Personnage
The UI presented during the MyPlayer creation flow that allows the User to compose their TennisPlayer's Portrait by choosing from Catalogue de Sprites components (face shape, skin tone, hair style, hair colour, and — for male TennisPlayers — facial hair). Selections are stored as component IDs on the Personne; the final Portrait image is composed server-side and stored in object storage at the moment the User confirms. The same component-ID schema is available to Mod authors for custom TennisPlayer portraits. Do not call it "character customizer", "avatar editor", or "éditeur de personnage" — use "Créateur de Personnage".

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
| ELO | Prestige (career) or Attributs (simulator strength) | ELO is redundant — Attributs encode strength for the simulator; Prestige tracks career legacy |
| Coach Principal | User | The User is the coach — there is no separate coaching Personnel role |
| Prestige du Club | Réputation du Club | "Prestige" est réservé au TennisPlayer |
| Offline | Mode Solo | Canonical product name |
| Carrière | Mode Solo (MyPlayer pour le sous-mode) | "Carrière" is ambiguous |
| Mode Hors-ligne | Mode Solo | Canonical product name |
| mode de jeu | sous-mode | Reserved for MyClub / MyPlayer / Sandbox distinctions |
| Avatar / Photo / Image de profil | Portrait | Canonical term for TennisPlayer visual representation |
| Character customizer / Avatar editor | Créateur de Personnage | Canonical term for the MyPlayer portrait UI |
| Sprite sheet / Asset library | Catalogue de Sprites | These are layered individual assets, not a packed sheet |
| Saison (meteorological) | Période Climatique | "Saison" is the competitive cycle — use "Période Climatique" for été/automne/hiver/printemps |
| City / Location | Ville | Canonical term for the geographic location of a Tournoi |
