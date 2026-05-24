# Handoff — NextManagerTennis : Format des événements de replay validé

## Project

**NextManagerTennis** — multiplayer online tennis management game. Multi-context monorepo at `/home/dupre/PROJECTS/NextTennisManager/`.

- Domain glossary (WebApp) : [`NextManagerTennis_WebApp/CONTEXT.md`](NextManagerTennis_WebApp/CONTEXT.md)
- Domain glossary (Simulator) : [`NextManagerTennis_Simulator/CONTEXT.md`](NextManagerTennis_Simulator/CONTEXT.md)
- Context map : [`CONTEXT-MAP.md`](CONTEXT-MAP.md)
- System-wide ADRs : [`docs/adr/`](docs/adr/) (0001–0019 accepted)
- No production code exists yet — project is in design/planning phase.

---

## Ce qui a été acté dans cette session

### Simulator CONTEXT.md — mises à jour

Toutes les modifications ci-dessous sont déjà appliquées dans [`NextManagerTennis_Simulator/CONTEXT.md`](NextManagerTennis_Simulator/CONTEXT.md) — ne pas dupliquer.

| Concept | Changement |
|---|---|
| `ReachResult` | 3 → 5 valeurs : `COMFORTABLE`, `LATE`, `STRETCHED`, `DESPERATE`, `MISSED` |
| `ErrorProbability` | **Supprimée.** L'erreur émerge de la CourtPosition d'arrivée effective de la Trajectoire. Ajoutée aux termes à éviter. |
| `HitQuality` | Pilote désormais la variance de la CourtPosition d'arrivée — plus d'ErrorProbability. |
| `IssueDuPoint` | Définitions réécrites sans référence à ErrorProbability (FAUTE_NON_FORCÉE, FAUTE_FORCÉE, DOUBLE_FAUTE). |
| `Intention de Coup` | Table de dégradation par ReachResult ajoutée — LATE maintient l'intent, DESPERATE force DEFENSIVE. |
| `Trajectoire` | CourtPosition d'arrivée **effective** (variance HitQuality). Progressive sur outdoor (Vent). Un seul segment sur INDOOR_HARD. |
| `Vent` | **Nouveau concept.** VENT_MOYEN par Créneau (Tournoi + Météo). Évolue graduellement entre Points. Constant pendant un Point. Nul sur INDOOR_HARD. |
| `AvailableTime` | Recalculé à chaque `BALL_FLIGHT_SEGMENT` sur outdoor — la zone d'arrivée se précise progressivement. |
| `Contrat du Simulateur` | VENT_MOYEN ajouté aux inputs : `(direction: float, intensité: float)`, nul pour INDOOR_HARD. |

### ADR-0018 — Format du stream d'événements

Voir [`docs/adr/0018-simulator-event-stream-format.md`](docs/adr/0018-simulator-event-stream-format.md).

Résumé des décisions (ne pas dupliquer l'ADR) :

| Décision | Choix retenu |
|---|---|
| Liste d'événements | 14 événements, 4 catégories (structurel, action, visuel, utilisateur) |
| BALL_FLIGHT_SEGMENT | Événements progressifs séparés — pas embarqués dans SHOT |
| Granularité outdoor | Multiple BALL_FLIGHT_SEGMENT par Coup (Vent) |
| Granularité INDOOR_HARD | Un seul BALL_FLIGHT_SEGMENT par Coup |
| SERVE_START | Événement dédié — porte `serveNumber: 1\|2` et `courtSide: DEUCE\|AD` |
| FAULT | Événement dédié — première faute de service sans fin de Point |
| effectiveIntent | Strictement tripartite : `AGGRESSIVE \| NEUTRAL \| DEFENSIVE` (pas de LOB, APPROACH, etc.) |
| Live | Stream complet — 14 événements, SHOT avec trajectory |
| Batch | Stream minimal — structurels + SERVE_START + FAULT + SHOT sans trajectory. Pas de BALL_FLIGHT_SEGMENT, PLAYER_MOVE, TACTIC_CHANGE |
| Replay différé | Batch uniquement pour le MVP |

### ADR-0019 — Protobuf comme format de sérialisation

Voir [`docs/adr/0019-protobuf-serialization-for-simulator-stream.md`](docs/adr/0019-protobuf-serialization-for-simulator-stream.md).

JSON rejeté (verbosité haute fréquence, pas d'enforcement contrat à la compilation, migration coûteuse). Protobuf retenu. Un fichier `.proto` partagé entre Spring Boot (simulateur) et Next.js (frontend) constitue l'extension du contrat du simulateur (ADR-0004).

---

## Points ouverts à traiter en session suivante

### 1. Météo (priorité haute)

Le concept `Vent` dépend d'un concept `Météo` non encore défini (marqué "à définir" dans le Simulator CONTEXT.md). La Météo est générée en amont pour chaque Créneau de Tournoi et détermine le VENT_MOYEN. Questions ouvertes :

- Quels paramètres météo existent au-delà du vent ? (température, précipitations, luminosité ?)
- Où vit la Météo dans le domaine — WebApp ou Simulator, ou les deux ?
- La Météo affecte-t-elle autre chose que la Trajectoire ? (Rebond sur surface mouillée, Fatigue en chaleur extrême ?)
- La Météo est-elle visible du User (affichée sur la fiche du Tournoi) ou purement simulée ?

### 2. Poids de fatigue intra-match pour LATE et DESPERATE

ADR-0017 définit la table d'accumulation pour `COMFORTABLE`, `STRETCHED`, et `MISSED` uniquement. L'expansion à 5 valeurs de ReachResult requiert que `LATE` et `DESPERATE` reçoivent leurs poids (`ReachResult_weight`) dans la formule d'accumulation :

```
increment = ReachResult_weight × Intent_weight / Endurance_resistance_coefficient
```

Ces poids sont externalisés dans ConfigurationGlobale — pas de changement structurel à l'ADR-0017, seulement une extension de la table.

### 3. Authoring du fichier `.proto`

ADR-0019 acte Protobuf mais n'écrit pas le schéma. Le fichier `.proto` doit définir les 14 types d'événements et les types partagés (`CourtPosition`, `ScoreSnapshot`, `PlayerTactic`, `MatchStats`). C'est le prochain artefact concret à produire avant tout travail d'implémentation.

### 4. `BOISÉ` — coup sur le cadre

Mentionné en session comme cas extrêmement rare et comique. Non tranché : serait un `SHOT` avec un flag `frameHit: true` et HitQuality quasi nul, trajectoire aléatoire — pas un `ReachResult` distinct. À confirmer avant l'authoring du `.proto`.

---

## Hooks existants dans les CONTEXT.md

- Simulator CONTEXT.md → `Vent` : "Voir Météo (à définir)"
- Simulator CONTEXT.md → `Contrat du Simulateur` : VENT_MOYEN dans les inputs
- WebApp CONTEXT.md → `Créneau` : "14 Créneaux per Semaine" — le VENT_MOYEN s'y rattache
- WebApp CONTEXT.md → `Tournoi` → `surface` : "Affects ball bounce and movement physics" — Météo viendra s'ajouter ici

---

## Suggested skills

- `/grill-with-docs` — pour designer le concept Météo (sujet #1 ci-dessus) en challengeant chaque décision contre les CONTEXT.md et ADRs existants. Commencer par : "Quels paramètres météo existent, et où vivent-ils dans le domaine ?"
- `/grill-with-docs` — pour finaliser les poids ReachResult LATE/DESPERATE dans le modèle de fatigue (extension ADR-0017) et valider le cas `BOISÉ`.
- `/tdd` — pour démarrer l'implémentation du simulateur une fois le fichier `.proto` écrit. Les cinq piliers du simulateur sont désormais complets : positions (ADR-0014), shot intent (ADR-0015), fatigue intra-match (ADR-0017), replay events (ADR-0018), Protobuf (ADR-0019).
