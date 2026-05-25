# Handoff — NextManagerTennis : Authoring du fichier `.proto`

## Project

**NextManagerTennis** — multiplayer online tennis management game. Multi-context monorepo at `/home/dupre/PROJECTS/NextTennisManager/`.

- Domain glossary (WebApp) : [`NextManagerTennis_WebApp/CONTEXT.md`](NextManagerTennis_WebApp/CONTEXT.md)
- Domain glossary (Simulator) : [`NextManagerTennis_Simulator/CONTEXT.md`](NextManagerTennis_Simulator/CONTEXT.md)
- Context map : [`CONTEXT-MAP.md`](CONTEXT-MAP.md)
- System-wide ADRs : [`docs/adr/`](docs/adr/) — ADRs 0001–0021 accepted
- No production code exists yet — project is in design/planning phase.

---

## Ce qui a été acté dans cette session

### Session précédente (voir [`handoff-nextmanagertennis-meteo-proto.md`](handoff-nextmanagertennis-meteo-proto.md))

Météo, ADR-0020, `surface_wetness_initial`, `precipitation_active`, `frameHit` — tout détaillé dans ce handoff. Ne pas redupliquer.

### Cette session — ADR-0021 + mises à jour CONTEXT.md

Voir [`docs/adr/0021-humidite-de-surface-modele-effets.md`](docs/adr/0021-humidite-de-surface-modele-effets.md) pour le détail complet. Résumé :

| Décision | Choix retenu |
|---|---|
| Effets par surface | `surface_wetness` → courbes Rebond distinctes par surface (CLAY/GRASS/HARD) — pas un coefficient universel |
| Lift/Slice/Amortie | Coefficients d'interaction explicites avec `surface_wetness`, configurables par surface dans ConfigurationGlobale |
| RequiredTime + Replacement | `surface_wetness` affecte aussi la mobilité des joueurs (CLAY réduit, GRASS/HARD augmente) — mêmes courbes |
| Glissement accidentel | `slipped: bool` sur `PLAYER_MOVE`, CourtPosition dégradée pour le **Coup suivant dans l'Échange** (pas le Point suivant), déclencheur de Blessure indépendant de la Fatigue |
| `Équilibre` | Double rôle : prévention du glissement (réduit probabilité) + mitigation HitQuality en position dégradée |
| `Glissade` | Double rôle sur CLAY : réduction RequiredTime + réduction probabilité de glissement accidentel sur CLAY humide |
| Protection du terrain | Float 0.0–1.0 sur Tournoi (pas Ville), statique, label UI dans vue détaillée Tournoi uniquement |
| Protection du terrain — double effet | Cap `surface_wetness_initial` + multiplicateur `surface_drying_rate` |
| `surface_drying_rate` | **Nouveau champ du Contrat du Simulateur** — calculé par WebApp (base ConfigurationGlobale × protection), transmis au Simulator |
| `precipitation_active` | Aucun malus HitQuality direct — rôle unique conservé (stabilise wetness entre Points) |

### Nouveaux champs `.proto` identifiés dans cette session

| Champ | Message | Source |
|---|---|---|
| `surface_wetness_initial: float` | `MATCH_START` | ADR-0020 (session précédente) |
| `precipitation_active: bool` | `MATCH_START` | ADR-0020 (session précédente) |
| `surface_drying_rate: float` | `MATCH_START` | **ADR-0021 (cette session)** |
| `frame_hit: bool` | `SHOT` | Session précédente (handoff) |
| `slipped: bool` | `PLAYER_MOVE` | **ADR-0021 (cette session)** |

---

## Prochain artefact : le fichier `.proto`

C'est la priorité haute. Le `.proto` doit couvrir :

### 14 événements du stream (ADR-0018)

**Structurels** (Live + Batch) :
- `MATCH_START` — inputs : TennisPlayers (Attributs, états dynamiques, Tactique), surface, format, `VENT_MOYEN (direction, intensité)`, `surface_wetness_initial`, `precipitation_active`, `surface_drying_rate`
- `SET_START`
- `GAME_START` — porte le serveur du Jeu
- `POINT_START` — porte serveur, côté (DEUCE/AD), score
- `POINT_END` — porte IssueDuPoint, gagnant, score, longueur d'Échange
- `GAME_END` — porte si break ou non
- `SET_END`
- `MATCH_END` — porte vainqueur, score complet, stats agrégées, `fatigue_delta`

**Action** (Live + Batch partiel) :
- `SERVE_START` — porte `serveNumber: 1 | 2`, `courtSide: DEUCE | AD`
- `PLAYER_MOVE` — Live uniquement — porte CourtPosition départ/arrivée, `availableTime`, `requiredTime`, **`slipped: bool`**
- `SHOT` — porte type de coup, `effectiveIntent`, `reachResult`, `hitQuality`, **`frameHit: bool`** ; champ `trajectory` uniquement en Live
- `FAULT` — première faute de service

**Visuel** (Live uniquement) :
- `BALL_FLIGHT_SEGMENT` — porte CourtPosition départ/arrivée (3D), durée, spin, vitesse

**Utilisateur** (Live uniquement) :
- `TACTIC_CHANGE`

### Types partagés

- `CourtPosition` — x, y, z floats
- `ScoreSnapshot` — sets, games, points courants
- `PlayerTactic` — preset + paramètres fins
- `MatchStats` — stats agrégées émises dans `MATCH_END`

### Gouvernance (ADR-0019)

- Le `.proto` vit dans un module partagé référencé par le simulateur Spring Boot et le frontend Next.js
- Spring Boot : `protoc` + plugin Java
- Next.js : `protoc` + plugin TypeScript (ex. `ts-proto`)
- Champs supprimés → `reserved`, jamais réutilisés avec le même numéro

---

## Hooks ouverts

- **`MatchStats`** — le contenu exact des stats agrégées dans `MATCH_END` n'a pas été précisé (durée du match, nb de Coups, % première balle, longueur moyenne d'Échange, etc.). À designer avant ou pendant l'authoring du `.proto`.
- **`PlayerTactic`** — les paramètres fins de la Tactique (au-delà du preset) ne sont pas encore définis dans le CONTEXT.md. Le `.proto` doit au minimum contenir les champs nécessaires pour `TACTIC_CHANGE` en Live.
- **Split Live/Batch dans le `.proto`** — ADR-0018 définit quels événements vont en Live vs Batch, mais le `.proto` lui-même ne gère pas ce split (c'est le producteur qui choisit quoi émettre). Un seul schéma couvre les deux modes. Vérifier que ce choix est explicite.

---

## Suggested skills

- `/tdd` — pour démarrer l'implémentation du simulateur une fois le `.proto` écrit. Les piliers sont complets : positions (ADR-0014), shot intent (ADR-0015), fatigue intra-match (ADR-0017), replay events (ADR-0018), Protobuf (ADR-0019), Météo (ADR-0020), Humidité de Surface (ADR-0021).
- `/grill-with-docs` — si des questions émergent sur `MatchStats` ou `PlayerTactic` pendant l'authoring du `.proto` (deux hooks ouverts ci-dessus).
