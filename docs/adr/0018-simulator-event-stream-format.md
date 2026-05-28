# ADR-0018 — Format du stream d'événements du simulateur

## Status
Accepted

## Context

ADR-0011 établit le modèle physique du simulateur et nomme `BALL_FLIGHT_SEGMENT` comme événement de replay — sans définir la liste complète des événements, leur granularité, ni la distinction entre les modes Live et Batch.

Cinq questions restaient ouvertes :

1. Quels événements émettre, et comment les catégoriser ?
2. À quelle granularité — par Coup, par Point, par Échange ?
3. `BALL_FLIGHT_SEGMENT` séparé ou trajectoire embarquée dans `SHOT` ?
4. Quels événements émettre en Live vs Batch ?
5. Le replay différé (revoir un Match après coup) est-il dans le périmètre MVP ?

Le contexte clé pour la question 3 : le Vent (ADR-0018, Simulator CONTEXT.md) courbe la trajectoire en vol sur les surfaces extérieures. La zone d'arrivée effective d'un lob n'est pas connue à l'impact — elle se précise segment par segment. L'adversaire recalcule son déplacement à chaque segment reçu. Cela rend impossible d'embarquer la trajectoire complète dans `SHOT`.

## Decision

### Taxonomie — événements en 4 catégories

**Structurels** — organisent le Match en niveaux logiques ; émis en Live et Batch :

| Événement | Déclencheur |
|---|---|
| `MATCH_START` | Début du Match — initialise joueurs, surface, format, VENT_MOYEN |
| `SET_START` | Début de chaque Set |
| `GAME_START` | Début de chaque Jeu — porte le serveur du Jeu |
| `POINT_START` | Début de chaque Point — porte serveur, côté (DEUCE/AD), score |
| `POINT_END` | Fin de Point — porte IssueDuPoint, gagnant, score, longueur d'Échange |
| `GAME_END` | Fin de Jeu — porte si break ou non |
| `SET_END` | Fin de Set |
| `MATCH_END` | Fin du Match — porte vainqueur, score complet, stats agrégées, `fatigue_delta` |

**Action** — ce que font les joueurs pendant le Point :

| Événement | Déclencheur |
|---|---|
| `SERVE_START` | Avant chaque tentative de service — porte `serveNumber: 1 \| 2`, `courtSide: DEUCE \| AD` |
| `PLAYER_MOVE` | Déplacement vers la balle — porte CourtPosition de départ/arrivée, `availableTime`, `requiredTime` |
| `SHOT` | Chaque Coup — porte type de coup, `effectiveIntent`, `reachResult`, `hitQuality` |
| `FAULT` | Première faute de service (n'interrompt pas le Point) |
| `SERVE_LET` | Service qui touche la bande, passe, et tombe dans le bon carré — le service est rejoué |

**Visuel** — animation du vol de balle :

| Événement | Déclencheur |
|---|---|
| `BALL_FLIGHT_SEGMENT` | Segment de Trajectoire — porte CourtPosition départ/arrivée (3D), durée, spin, vitesse |
| `BOUNCE` | Rebond — porte CourtPosition d'impact, vitesse/spin entrants et sortants |
| `NET_INTERACTION` | Interaction avec le filet — clair, autour du filet, bande passée, bande ratée, filet |

**Utilisateur** — actions du User en cours de Match :

| Événement | Déclencheur |
|---|---|
| `TACTIC_CHANGE` | Changement de Tactique par le User en cours de Match (Live uniquement) |

---

### `BALL_FLIGHT_SEGMENT`, `BOUNCE` et `NET_INTERACTION` comme événements physiques séparés

La trajectoire n'est pas embarquée dans `SHOT`. `SHOT` décrit l'action du joueur et son exécution. Le simulateur émet ensuite les événements physiques successifs : segments de vol, interaction éventuelle avec le filet, Rebond, puis suite de trajectoire si la balle reste jouable.

Sur les surfaces extérieures (`CLAY`, `GRASS`, `HARD`) : plusieurs `BALL_FLIGHT_SEGMENT` par Coup — le Vent courbe la trajectoire, la déviation latérale croissant avec la hauteur et la durée de vol. L'adversaire recalcule son `AvailableTime` à chaque segment et peut corriger son déplacement.

Sur `INDOOR_HARD` (sans Vent) : un seul `BALL_FLIGHT_SEGMENT` par Coup — la trajectoire est entièrement déterminée à l'impact.

Embarquer la trajectoire dans `SHOT` a été rejeté : sur surfaces extérieures, la trajectoire complète n'est pas calculée à l'impact (modèle progressif). L'embarquer créerait une asymétrie entre surfaces et retirerait la dynamique d'adaptation au Vent à l'adversaire.

`BOUNCE` est distinct parce que la surface et l'humidité transforment vitesse et spin. Sans événement ou payload équivalent, le replay ne peut pas reconstruire correctement la balle après impact.

`NET_INTERACTION` est distinct parce qu'une bande passée modifie la vitesse, le spin et parfois l'Issue du service (let), sans être équivalente à un simple segment de vol.

---

### `SERVE_START` comme type d'événement séparé

`SERVE_START` porte `serveNumber: 1 | 2` et `courtSide: DEUCE | AD`. Après une première faute (`FAULT`), c'est le seul mécanisme explicite qui informe les consommateurs que le serveur s'apprête à jouer sa deuxième balle.

Dériver cette information de `POINT_START` + `FAULT` a été rejeté : cela impose au consommateur de maintenir un compteur de fautes par Point pour reconstituer le contexte du service, ce qui fragmente la logique d'état côté client.

---

### `FAULT` comme type d'événement séparé

Une première faute de service ne termine pas le Point — le serveur rejoue. Sans `FAULT` dédié, la première faute serait un `SHOT` sans `POINT_END` suivant, ce qui oblige le consommateur à inférer la continuation du Point depuis le payload de `SHOT`.

Représenter la faute comme un champ `result: FAULT` sur `SHOT` a été rejeté : l'absence de `POINT_END` après un `SHOT` n'est pas une sémantique naturelle, et les stats de service (pourcentage de premières balles, taux de double faute) sont plus simples à accumuler depuis un événement dédié.

La séquence canonique double faute est :
```
SERVE_START → SHOT → FAULT → SERVE_START → SHOT → FAULT → POINT_END { issueDuPoint: DOUBLE_FAUTE }
```

La séquence canonique d'un let de service est :
```
SERVE_START → SHOT → BALL_FLIGHT_SEGMENT → NET_INTERACTION { NET_TAPE_PASSED } → BOUNCE → SERVE_LET → SERVE_START
```

Un let ne compte ni comme `FAULT` ni comme `POINT_END`.

---

### Split Live vs Batch

**Live** (WebSocket, Redis) — stream complet :
- Les événements structurels, action, physiques et utilisateur
- `SHOT` porte l'intention/exécution ; les événements physiques portent la Trajectoire, le Rebond et le filet

**Batch** (background, pas de WebSocket) — stream minimal :
- Les 8 événements structurels
- `SERVE_START`, `FAULT`, `SERVE_LET`
- `SHOT` sans champ `trajectory`
- Les événements physiques détaillés (`BALL_FLIGHT_SEGMENT`, `BOUNCE`, `NET_INTERACTION`) peuvent être absents du stream persisté pour économiser le stockage, mais le worker doit en agréger les stats physiques utiles avant de les jeter
- `PLAYER_MOVE` peut être absent du stream persisté, mais distance parcourue, pression subie, ReachResult et métriques d'effort doivent être agrégés si ces stats sont requises
- `TACTIC_CHANGE` impossible en Batch — pas de User pour changer de Tactique en temps réel

---

### Replay différé = stream Batch uniquement (MVP)

Stocker le stream Live complet par Match est prohibitif à l'échelle MVP (volume de `BALL_FLIGHT_SEGMENT` et `PLAYER_MOVE` sur des milliers de Matchs simultanés). Le stream Batch suffit pour reconstruire le score, les stats coup par coup, et la narration du Match, à condition que les métriques physiques utiles soient agrégées pendant la simulation.

Le replay 3D animé (depuis le stream Live) est différé à post-lancement, conditonné à l'économie de stockage du jeu. Aucun changement de schéma ne sera nécessaire — seule la persistance du stream Live devra être activée.

## Consequences

- `AvailableTime` est recalculé à chaque `BALL_FLIGHT_SEGMENT` sur les surfaces extérieures. C'est dynamique — un joueur peut anticiper un ReachResult `COMFORTABLE` et finir `STRETCHED` si le Vent courbe la balle plus que prévu. Ce comportement est intentionnel.
- `TACTIC_CHANGE` est Live-only par construction — Batch ne peut pas le produire.
- ADR-0017 définit les poids de fatigue intra-match pour `COMFORTABLE`, `STRETCHED`, et `MISSED`. L'expansion de ReachResult à 5 valeurs (`LATE` et `DESPERATE` ajoutés — Simulator CONTEXT.md) nécessite que `LATE` et `DESPERATE` reçoivent leurs poids dans la table d'accumulation. Ces valeurs sont externalisées dans ConfigurationGlobale — il n'y a pas de changement structurel au modèle de l'ADR-0017.
- Le format de sérialisation (Protobuf) est couvert séparément dans ADR-0019.
- La liste des événements et leurs payloads constituent une extension du contrat du simulateur défini dans ADR-0004. Tout ajout d'événement est un changement de contrat.
