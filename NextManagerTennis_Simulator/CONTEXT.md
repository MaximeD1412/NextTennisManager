# Context — NextManagerTennis (Simulation Engine)

Le moteur de simulation reçoit le snapshot d'un Match (TennisPlayers, Tactiques, surface, format) et produit un résultat point par point en modélisant la physique du jeu. Ce contexte définit le vocabulaire propre au moteur — distinct du vocabulaire de gestion du jeu (WebApp).

## Glossaire

### CourtPosition
Un point dans l'espace 3D du terrain, défini par trois axes métriques avec origine au centre du filet :

- **x** : largeur du terrain, `x = 0` sur la ligne centrale. Valeurs négatives d'un côté, positives de l'autre. En simple, la balle est dans le couloir jouable si `abs(x) <= 4.115`.
- **y** : profondeur, `y = 0` sur le plan du filet. `y > 0` désigne un fond de court, `y < 0` l'autre fond de court. La ligne de fond est à `abs(y) = 11.89`.
- **z** : hauteur au-dessus de la surface, `z = 0` au sol.

Ne jamais utiliser `x = 0` comme "côté gauche" : `x = 0` est le centre du terrain. `CourtSide.DEUCE/AD` est une notion de service relative au serveur et doit être convertie en zone cible à partir du côté de service (`sign(y)`), pas supposée équivalente à un signe de `x` universel. Ne pas utiliser "coordonnées" seul — utiliser CourtPosition.

### Coup
Une frappe de balle effectuée par un joueur. Un Coup est décrit en deux temps :

1. **ShotPlan** — intention tactique : zone cible, marge de filet désirée, vitesse désirée, Effet désiré, type de trajectoire, niveau de risque.
2. **ShotExecution** — exécution physique effective : CourtPosition de contact, vitesse initiale 3D, axe de spin, spin RPM, erreur de timing/contact, angle de raquette.

Le hasard intervient dans ShotExecution (bruit d'exécution), pas comme un verdict direct `in/out`. Le Coup génère une Trajectoire et déclenche un calcul de HitQuality. Ne pas utiliser "tir" — utiliser "Coup".

Cas spécial — **BOISÉ** : un Coup sur le cadre de la raquette, extrêmement rare. Le joueur a atteint la balle (ReachResult valide, généralement COMFORTABLE ou LATE) mais le contact est raté. Représenté par `frameHit: true` sur l'événement `SHOT` du stream. HitQuality forcé à quasi zéro ; variance de Trajectoire maximale et aléatoire — la balle peut atterrir n'importe où. Le Point continue normalement après l'impact. Pas un ReachResult distinct ni une IssueDuPoint distincte.

### Trajectoire
Le chemin 3D d'un Coup depuis la frappe jusqu'au Rebond, au filet, ou à la sortie de la zone de simulation. La Trajectoire est produite par intégration physique à partir de ShotExecution : position, vitesse 3D, gravité, traînée, Magnus, Vent, surface et humidité.

La CourtPosition d'arrivée effective n'est pas tirée directement comme verdict du Coup. Elle émerge de la trajectoire intégrée. HitQuality module l'amplitude des erreurs d'exécution (direction, vitesse, spin, hauteur de contact, timing), qui peuvent produire une balle bonne, longue, large, dans le filet, ou boisée. Si la CourtPosition d'arrivée effective est hors limites ou si la balle ne franchit pas le filet, l'IssueDuPoint est `FAUTE_NON_FORCÉE` ou `FAUTE_FORCÉE` selon la pression subie et la difficulté du Coup — pas selon une probabilité indépendante.

Sur les surfaces extérieures (`CLAY`, `GRASS`, `HARD`), le Vent courbe la balle en vol — la déviation latérale croît avec la hauteur (`z`) et la durée de vol. Le simulateur émet la Trajectoire en segments successifs via `BALL_FLIGHT_SEGMENT`, calculés progressivement (pas de pré-calcul de la trajectoire complète à l'impact). Sur `INDOOR_HARD` (sans Vent), un seul `BALL_FLIGHT_SEGMENT` suffit par Coup. Sert de base au calcul de AvailableTime pour l'adversaire.

### Moteur Physique de Balle
Le simulateur doit utiliser un moteur commun pour tous les Coups, services inclus. L'implémentation cible de ce moteur est un `physics-core` Rust appelé par le worker Java (ADR-0024). Le moteur reçoit ShotExecution et retourne une Trajectoire intégrée.

Le worker Java reste responsable de l'orchestration : Match, Point, Score, Tactique, Attributs, Fatigue, Moral, IA tactique, choix de Coup, publication RabbitMQ/Redis/Replay et classification métier des événements. Le `physics-core` Rust reste responsable de la vérité physique : trajectoire 3D, drag, Magnus, Vent, filet, bande, let de service, Rebond et événements physiques compacts.

La frontière ne doit pas faire fuiter le domaine jeu dans Rust. Rust reçoit des entrées physiques déjà dérivées (`PhysicsEnvironment`, `ShotSpec`, seed déterministe) et retourne des résultats physiques. Il ne connaît pas les noms d'Attributs, le Score, la Tactique, le Tournoi, la Ligue, le Club ou l'Utilisateur.

Les appels Java -> Rust doivent être **batch-first** pour le Monte Carlo et l'IA :

```text
simulateBatch(environment, shotSpecs[], deterministicSeed)
  -> physicsResults[]
```

Ne pas appeler Rust Coup par Coup quand plusieurs candidats peuvent être simulés en lot. Le coût de frontière inter-langage doit être amorti par des batches de candidats ou de variantes Monte Carlo.

État minimal de balle :

```text
position = (x, y, z)
velocity = (vx, vy, vz)
spinRpm
spinAxis = vecteur 3D normalisé
```

Forces minimales :

```text
gravity
drag aérodynamique
Magnus issu de spinRpm et spinAxis
Vent sur surfaces extérieures
```

Le `ShotEffect` (`FLAT`, `TOPSPIN`, `SLICE`, `LIFT`) est une intention d'effet, pas une force physique suffisante. Il doit être converti en `spinRpm` et `spinAxis`. Le topspin/lift doit permettre une balle lancée plus haut qui redescend plus vite par Magnus ; il ne doit pas être modélisé comme un simple bonus de hauteur.

Déterminisme : Java fournit les seeds sémantiques (`matchId`, Point, Coup, variante Monte Carlo). Rust ne doit pas utiliser d'horloge, d'état aléatoire global, d'itération non déterministe ou de réduction parallèle non stable pour produire un résultat physique. La reproductibilité est garantie pour une même version de simulateur, même version de `physics-core`, même profil de build et même cible d'exécution ; changer l'algorithme physique ou les options bas niveau impose un bump de version simulateur.

### Filet et Bande
Le filet est un obstacle géométrique, pas une déduction depuis la CourtPosition d'arrivée.

Dimensions de référence :

```text
NET_CENTER_HEIGHT_M = 0.914
NET_POST_HEIGHT_M = 1.07
NET_POST_X_M = 5.029
BALL_RADIUS_M = 0.0335
```

À chaque passage de la Trajectoire à travers le plan `y = 0`, le simulateur interpole `xNet`, `zNet` et `vNet`.

Règles :

- `abs(xNet) > NET_POST_X_M + BALL_RADIUS_M` : la balle passe autour du filet, pas de collision filet.
- `zNet >= netHeightAtX + BALL_RADIUS_M` : la balle franchit clairement le filet.
- `zNet <= netHeightAtX - BALL_RADIUS_M` : la balle touche le filet et ne passe pas.
- sinon : contact avec la bande du filet.

Une balle qui touche la bande peut passer. Le tirage est déterministe par Coup (`matchId`, `pointIndex`, `shotIndex`, suffixe `"netTape"`), et la probabilité dépend au minimum de la hauteur relative sur la bande, de la vitesse au filet, de la vitesse verticale et du spin. Si la bande laisse passer la balle, la trajectoire continue avec perte d'énergie et de spin ; le point ou le service n'utilise pas l'atterrissage prévu avant contact.

Sur service, une bande qui passe et tombe dans le bon carré produit un **Let de service** : le service est rejoué, ce n'est ni une faute ni un point terminé.

### Rebond
L'impact de la balle sur la surface du terrain. Le Rebond transforme la vitesse et le spin entrants en vitesse et spin sortants selon :

- surface (`CLAY` | `GRASS` | `HARD` | `INDOOR_HARD`) ;
- humidité de surface ;
- vitesse verticale et horizontale entrante ;
- spinRpm et spinAxis entrants ;
- coefficients de restitution et de friction configurables.

La surface influence la hauteur du rebond (clay = rebond plus haut et plus lent, grass = rebond plus bas et plus fusant) et donc AvailableTime. L'Humidité de Surface modifie ces paramètres sur les surfaces extérieures — voir Humidité de Surface. Le Rebond doit être représentable dans le stream Live par un événement dédié ou par des segments portant explicitement la transition pré/post-rebond.

### AvailableTime
Le temps dont dispose un joueur entre sa position courante et sa fenêtre de frappe jouable. Ce n'est pas seulement le temps jusqu'au premier Rebond : selon le type de Coup, le joueur peut frapper avant Rebond (volée), juste après Rebond, ou plus tard dans la trajectoire post-rebond. Recalculé à chaque `BALL_FLIGHT_SEGMENT` reçu : chaque segment précise la zone d'arrivée probable, le joueur ajuste son déplacement en conséquence.

AvailableTime est réduit par le ReactionDelay, influencé par Lecture du jeu, Lecture de service, Vision du court, l'effet de surprise tactique, et la lisibilité du Coup adverse.

### RequiredTime
Le temps minimal nécessaire au joueur pour atteindre une fenêtre de frappe jouable et se stabiliser avant le contact. Dépend de : distance à parcourir, accélération, vitesse maximale, changement de direction, split-step, portée de raquette, Attributs physiques, Fatigue effective, Humidité de Surface et qualité des appuis.

RequiredTime ne doit pas viser exactement la CourtPosition de Rebond si une frappe réaliste se joue à une autre hauteur ou après déplacement post-rebond. Ne pas confondre avec AvailableTime — ces deux valeurs sont calculées indépendamment puis comparées.

### ReachResult
Le résultat de la comparaison `AvailableTime − RequiredTime`. Cinq valeurs :
- `COMFORTABLE` — marge largement positive. Geste complet préparé. HitQuality élevé ; intent Tactique maintenu.
- `LATE` — marge légèrement insuffisante. Geste raccourci mais stable. HitQuality dégradé ; intent Tactique maintenu.
- `STRETCHED` — marge négative faible. Joueur en bout de course, étiré, geste réduit au coup de poignet. HitQuality fortement dégradé ; intent dégradé d'un cran (`AGGRESSIVE → NEUTRAL`).
- `DESPERATE` — marge très négative. Balle à peine touchée du bout de la raquette. HitQuality quasi nul, variance de Trajectoire maximale ; intent forcé à `DEFENSIVE` quel que soit l'intent Tactique.
- `MISSED` — marge négative franche. Le joueur n'atteint pas la balle : le Point se termine immédiatement (IssueDuPoint = `COUP_GAGNANT` pour l'adversaire).

### HitQuality
Coefficient normalisé (0.0–1.0) représentant la qualité d'exécution d'un Coup. Déterminé par : la marge du ReachResult, le coefficient de Fatigue, le Rythme, les Attributs Technique du joueur, l'effet entrant, la hauteur de balle et la stabilité au contact.

HitQuality ne tire jamais directement une faute. Il pilote l'erreur d'exécution physique : bruit sur direction, vitesse initiale, angle vertical, spin, point de contact, timing et choix de zone. Une CourtPosition d'arrivée effective hors limites ou une collision filet émerge ensuite de la Trajectoire.


### Échange
La séquence alternée de Coups à l'intérieur d'un Point, depuis le service jusqu'à l'IssueDuPoint. Un Échange de longueur 1 se termine sur un ace ou une double faute. La durée de l'Échange est une métrique produite par le simulateur et transmise dans les stats du Match.

### IssueDuPoint
L'événement terminal d'un Point. Cinq valeurs :
- `ACE` — service non atteint par le relanceur (ReachResult = `MISSED` sur le service).
- `DOUBLE_FAUTE` — deux services consécutifs dont la CourtPosition d'arrivée effective est hors limites du terrain ou dans le filet.
- `COUP_GAGNANT` — Coup non atteint par l'adversaire (ReachResult = `MISSED` hors service).
- `FAUTE_NON_FORCÉE` — CourtPosition d'arrivée effective hors limites, avec un HitQuality élevé (le joueur était bien placé mais a mal exécuté).
- `FAUTE_FORCÉE` — CourtPosition d'arrivée effective hors limites, avec un HitQuality faible suite à un ReachResult `STRETCHED` ou `DESPERATE` (l'adversaire a créé la difficulté).

Ne pas utiliser "erreur" seul — préciser FAUTE_NON_FORCÉE ou FAUTE_FORCÉE.

### Contrat du Simulateur
L'interface fixe entre le contexte WebApp et le Simulator, définie dans ADR-0004. Le simulateur reçoit : les Attributs de chaque TennisPlayer (1–99), leurs états dynamiques (Fatigue, Rythme, Moral en 0.0–1.0), leur morphologie physique minimale (`height_m`, `standing_reach_m`, `body_mass_kg`, main dominante), leur Tactique active, la surface du Match, le format (BEST_OF_3 | BEST_OF_5), le VENT_MOYEN du Créneau `(direction: float, intensité: float)`, `surface_wetness_initial: float` (0.0–1.0), `precipitation_active: bool`, et `surface_drying_rate: float` (taux de séchage par Point, calculé par le WebApp depuis la base ConfigurationGlobale modulée par la Protection du terrain du Tournoi) — les quatre derniers nuls/false/0.0 pour `INDOOR_HARD`. Il retourne : le vainqueur, le score complet, les stats du Match, et — si activé — les événements de replay.

Les paramètres physiques internes (vitesse maximale par unité d'Attribut, coefficients de traînée, coefficients Magnus, restitution de Rebond, courbes de bruit d'exécution, coefficients de bande du filet, etc.) sont tunables via ConfigurationGlobale/MondeSetting et ne font **pas** partie du contrat fixe. Ne pas introduire de probabilité de faute indépendante.

Le contrat externe du simulateur reste porté par le worker Java. Le contrat interne Java -> Rust est plus étroit : `PhysicsEnvironment`, batch de `ShotSpec`, seed déterministe, résultats physiques. Un changement de cette frontière interne doit être documenté avec le même niveau de rigueur qu'un changement du `.proto`, car l'IA Monte Carlo et le replay en dépendent.

### Morphologie du TennisPlayer
La morphologie n'est pas un Attribut entraînable : elle est générée à la création de la Personne et transmise au Simulator.

Champs minimaux :

- `height_m` : taille debout.
- `standing_reach_m` : allonge bras levé, plus directement utile que la taille pour le service et les smashes.
- `body_mass_kg` : utile pour fatigue, explosivité, inertie et risque de glissement/blessure.
- `dominant_hand` : main dominante, utile pour géométrie de service, slice, angles, zones de confort.

Au service, la hauteur de contact doit émerger de :

```text
serveContactHeight =
  standingReachM
  + racketSweetSpotReachM
  + jumpLiftM
  - contactLossM
  + contactNoiseM
```

Un grand serveur ne reçoit pas un bonus arbitraire de réussite : son avantage vient d'une hauteur de contact supérieure, donc d'une fenêtre angulaire plus large au-dessus du filet et vers le carré de service.

### Service Physique
Le service est un Coup physique comme les autres. `Fiabilité service` et `Second service` ne sont pas des probabilités directes de réussite. Ils modulent le bruit d'exécution, le choix de marge, la prise de risque, la stabilité de la hauteur de contact et la cohérence du spin.

Le service doit :

- construire un ShotPlan selon première/deuxième balle, Tactique, CourtSide, main dominante et côté de service ;
- calculer une CourtPosition de contact avec la morphologie du serveur ;
- convertir le plan en ShotExecution physique ;
- intégrer la trajectoire avec filet, bande, spin, Vent et Rebond ;
- classifier le résultat : service bon, faute, let, ace potentiel après ReachResult du relanceur.

Une première balle et une deuxième balle utilisent le même moteur. La différence vient de la vitesse désirée, du spin désiré, de la marge de filet, de la cible et de la tolérance d'exécution.

### Intention de Coup (Shot Intent)
L'intention offensivo-défensive d'un Coup, déterminée avant chaque frappe. Trois valeurs :
- `AGGRESSIVE` — le joueur cherche à faire mal : profondeur, vitesse, angle, ou conversion défense→offensive.
- `NEUTRAL` — le joueur joue sans prise de risque offensive : remise propre, maintien de l'échange.
- `DEFENSIVE` — le joueur cherche uniquement à remettre la balle en jeu.

L'intent préféré est exprimé par la Tactique. Il est contraint par le ReachResult selon la table suivante :

| ReachResult | Intent effectif |
|---|---|
| `COMFORTABLE` | intent Tactique maintenu |
| `LATE` | intent Tactique maintenu ; HitQuality dégradé seulement |
| `STRETCHED` | `AGGRESSIVE → NEUTRAL` |
| `DESPERATE` | forcé `DEFENSIVE`, quel que soit l'intent Tactique |
| `MISSED` | point terminé, pas de frappe |

Les Attributs Technique activés pour le calcul du HitQuality dépendent de l'intent effectif : Contre (AGGRESSIVE depuis STRETCHED), Remise difficile (DEFENSIVE depuis DESPERATE), En course (taux de dégradation entre états).

### Fatigue Intra-Match
Un accumulateur interne au simulateur (0.0–1.0), initialisé à 0.0 au début de chaque Match. Croît à chaque Coup selon le ReachResult et l'Intention de Coup, réduit par l'Attribut **Endurance** (coefficient de résistance). Se dissipe entre les Points selon le type de pause (inter-point vs changement de côté), modulé par l'Attribut **Récupération inter-points**. N'est jamais persisté — il est discardé à la fin du Match.

La Fatigue Intra-Match se combine avec le coefficient de Fatigue inter-match (snapshot reçu du WebApp au début du Match) pour produire le **coefficient effective_fatigue** consommé par RequiredTime et HitQuality :

```
effective_fatigue = inter_match + intra_match × (1 − inter_match)
```

Ce coefficient est recalculé à chaque Coup — il évolue tout au long du Match, il n'est pas fixe. À la fin du Match, le simulateur rapporte un `fatigue_delta = mean(intra_accumulator_per_point) × format_scaling_coefficient` au WebApp, qui l'applique à la Fatigue inter-match persistante du TennisPlayer. Ne pas confondre avec la Fatigue inter-match (état persistant entre les Matchs, propriété du WebApp). Voir ADR-0017.

### Vent
Une force horizontale (vecteur direction + intensité) affectant la Trajectoire des balles en vol sur les surfaces extérieures. Applicable uniquement aux surfaces `CLAY`, `GRASS`, et `HARD` — nul sur `INDOOR_HARD`.

Chaque Créneau de Tournoi dispose d'un **VENT_MOYEN** : une intensité et direction de référence dérivées de la Météo effective générée pour ce Créneau. Le VENT_MOYEN est transmis au simulateur dans le snapshot du Match. Voir Météo (WebApp CONTEXT.md).

État persistant à travers le Match : le vecteur de Vent évolue **graduellement** autour du VENT_MOYEN entre les Points (petits incréments aléatoires en direction et/ou intensité, bornés par des seuils configurables) — il ne change pas aléatoirement d'un Point à l'autre. Pendant un Point donné, le vecteur de Vent est constant.

Effet sur la Trajectoire : courbe la balle latéralement en vol, l'amplitude de déviation croissant avec la hauteur (`z`) et la durée de vol — les lobs et trajectoires hautes sont proportionnellement plus affectés que les trajectoires basses et rapides. La déviation est calculée progressivement lors de l'émission des `BALL_FLIGHT_SEGMENT` — le simulateur ne pré-calcule pas la trajectoire complète à l'impact.

Transmis au simulateur dans le snapshot du Match (Contrat du Simulateur) comme un vecteur `(direction: float, intensité: float)`. Intensité maximale, plage d'évolution par Point, et probabilité de changement de direction sont tunables via ConfigurationGlobale/MondeSetting.

### Humidité de Surface
Un état dynamique du terrain, parallèle au Vent, initialisé au démarrage du Match par `surface_wetness_initial` (reçu du Contrat du Simulateur, 0.0–1.0). Évolue entre les Points selon `precipitation_active` : décroît progressivement si le terrain sèche (`precipitation_active = false`), stable si la pluie continue. Le taux de séchage entre les Points est transmis dans le Contrat du Simulateur comme `surface_drying_rate: float` — calculé par le WebApp depuis la valeur de base ConfigurationGlobale modulée par la `Protection du terrain` du Tournoi.

Produit deux effets distincts sur les surfaces extérieures (`CLAY`, `GRASS`, `HARD`), chacun avec des courbes configurables par surface dans ConfigurationGlobale :

1. **Rebond** — modifie la hauteur du rebond et la vitesse horizontale post-rebond. Module également l'efficacité des Effets appliqués au Rebond (`Lift`, `Slice`, `Amortie coup droit`, `Amortie revers`) via des coefficients d'interaction configurables par surface.

2. **RequiredTime** — modifie la mobilité des joueurs selon la surface : sur `CLAY` humide, légère réduction du RequiredTime (glissade amplifiée, s'empile additivement avec l'Attribut `Glissade`) ; sur `GRASS` et `HARD` humides, augmentation du RequiredTime (appuis instables, difficulté à se placer).

Nul pour `INDOOR_HARD` : `surface_wetness_initial = 0.0`, `precipitation_active = false`, `surface_drying_rate = 0.0`. Jamais persisté — discardé à la fin du Match, comme la Fatigue Intra-Match.

### Replacement
Le mouvement d'un joueur vers sa CourtPosition de base après avoir frappé un Coup. La vitesse de replacement est calculée depuis les Attributs Physique (Vitesse latérale, Vitesse avant-arrière, Agilité), modulée par l'Humidité de Surface (mêmes courbes par surface que RequiredTime). La CourtPosition de base visée est déterminée par l'Attribut Intelligence de jeu Placement — elle n'est pas un point fixe (centre du fond de court), mais une CourtPosition optimale calculée selon la situation en cours. Le replacement commence dès que le joueur frappe et se poursuit jusqu'au moment où il doit se déplacer vers la balle suivante. La CourtPosition effective au moment du prochain calcul RequiredTime est la position atteinte pendant le temps de replacement disponible.

**Glissement accidentel** — sur les trois surfaces extérieures humides (`CLAY`, `GRASS`, `HARD`), un changement de direction pendant le Replacement peut provoquer un glissement involontaire. Probabilité distincte par surface (CLAY très faible, GRASS et HARD plus élevées), configurable dans ConfigurationGlobale. Réduite par l'Attribut `Équilibre` sur toutes les surfaces ; réduite additionnellement par l'Attribut `Glissade` sur `CLAY` spécifiquement (maîtrise du sliding = meilleurs appuis sur CLAY humide).

Lorsqu'un glissement se produit, l'événement `PLAYER_MOVE` porte `slipped: true`. Le joueur se retrouve dans une CourtPosition dégradée : le calcul du RequiredTime pour le **Coup suivant dans l'Échange en cours** part d'une position très défavorable, résultant typiquement en un ReachResult `STRETCHED` ou `DESPERATE`. Le glissement est un déclencheur de Blessure additionnel, indépendant de la Fatigue — probabilité configurable par surface dans ConfigurationGlobale, distincte de la probabilité Fatigue.

### PlayerBrain
L'interface Java représentant l'intelligence tactique d'un joueur pendant un Point. Appelée à chaque SimulationTick par le simulateur. Maintient un état interne propre au joueur. Reçoit un `GameTickState` (position de balle interpolée, positions joueurs, score, météo) et un `ObservableOpponentState`. Produit une `TacticalState` mise à jour à chaque tick.

`PlayerBrain` est pluggable — le simulateur n'interagit qu'avec l'interface. La première implémentation est `RuleBasedBrain` (logique déterministe basée sur les Attributs du joueur). Une `NeuralBrain` peut la remplacer sans toucher au simulateur.

Ne pas confondre avec la Tactique (style de jeu déclaré du TennisPlayer) : la Tactique est une entrée du PlayerBrain, pas le PlayerBrain lui-même.

### SimulationTick
L'unité de temps discrète de la simulation d'un Point. À chaque tick, le simulateur :
1. Interpole la position de la balle depuis la Trajectoire Rust pré-calculée pour le Coup en cours.
2. Met à jour les positions joueurs selon leur TacticalState courante.
3. Appelle chaque PlayerBrain avec l'état courant du terrain.
4. Détecte le tick de contact balle-raquette et lit la TacticalState pour construire le ShotSpec.

La physique (Rust sidecar) reste batch : la Trajectoire complète est calculée en un seul appel à l'issue de chaque Coup, puis interpolée tick par tick. Le tick n'entraîne pas de nouvel appel Rust.

### TacticalState
L'état tactique courant d'un joueur, maintenu en continu par son PlayerBrain et mis à jour à chaque SimulationTick. Contient la CourtPosition cible de déplacement courant, le type de Coup préparé, le ShotIntent courant, la zone cible approximative du prochain Coup, et le niveau de risque choisi.

Au tick de contact balle-raquette, le simulateur lit la `TacticalState` courante du joueur frappeur pour construire le `ShotSpec` soumis au moteur physique. La décision de Coup est donc prise progressivement pendant le vol de la balle, pas au moment du contact.

### ObservableOpponentState
Ce qu'un PlayerBrain peut percevoir de son adversaire à un tick donné : CourtPosition courante, vecteur vélocité, et préparation de Coup visible (forehand / backhand / smash / aucune). La TacticalState adverse n'est pas accessible.

L'attribut `Lecture du jeu` module la capacité du brain à inférer les Coups probables adverses depuis cet état observable : une valeur élevée permet de générer plus de coups adverses possibles avec des probabilités associées, et d'optimiser la CourtPosition de couverture en conséquence.

## Mapping Attributs → Rôle physique

Tableau de référence pour l'implémentation. Chaque Attribut est mappé à son effet dans le modèle physique.

| Attribut | Catégorie | Rôle dans le simulateur |
|---|---|---|
| Vitesse latérale | Physique | Composante x du vecteur de déplacement — RequiredTime |
| Vitesse avant-arrière | Physique | Composante y du vecteur de déplacement — RequiredTime |
| Agilité | Physique | Accélération et changement de direction — RequiredTime |
| Jeu de jambes | Physique | Micro-ajustements pré-frappe — gate de stabilité avant la frappe, modifie HitQuality |
| Endurance | Physique | Résistance à l'accumulation de fatigue intra-match — coefficient de résistance |
| Équilibre | Physique | (1) Réduit la probabilité de glissement accidentel lors du Replacement sur surface humide ; (2) HitQuality quand le joueur frappe en déséquilibre (position dégradée) |
| Récupération inter-points | Physique | Taux de dissipation de la fatigue intra-match entre les points |
| Puissance service | Technique | Vitesse initiale de balle dans la Trajectoire du service |
| Précision service | Technique | Dispersion autour du CourtPosition cible dans la zone de service |
| Fiabilité service | Technique | Réduit le bruit d'exécution sur première balle : timing, angle vertical, direction, spin, hauteur de contact |
| Second service | Technique | Réduit le bruit d'exécution sur deuxième balle et favorise une marge de filet/spin plus sûre |
| Variété service | Technique | Coefficient réducteur sur l'effet de Lecture de service adverse (réduit la réduction du ReactionDelay) |
| Retour coup droit | Technique | HitQuality pour le retour de service joué côté coup droit |
| Retour revers | Technique | HitQuality pour le retour de service joué côté revers |
| Lecture de service | Technique | Réduit le ReactionDelay sur le retour (anticipation zone/effet/vitesse du service) |
| Puissance coup droit | Technique | Vitesse initiale de balle dans la Trajectoire du coup droit |
| Précision coup droit | Technique | Dispersion autour du CourtPosition cible pour le coup droit |
| Régularité coup droit | Technique | Répétabilité technique : réduit le bruit d'exécution côté coup droit |
| Coup droit en course | Technique | Taux de dégradation du HitQuality coup droit quand ReachResult = STRETCHED |
| Puissance revers | Technique | Vitesse initiale de balle dans la Trajectoire du revers |
| Précision revers | Technique | Dispersion autour du CourtPosition cible pour le revers |
| Régularité revers | Technique | Répétabilité technique : réduit le bruit d'exécution côté revers |
| Revers en course | Technique | Taux de dégradation du HitQuality revers quand ReachResult = STRETCHED |
| Volée coup droit | Technique | HitQuality pour la volée côté coup droit |
| Volée revers | Technique | HitQuality pour la volée côté revers |
| Réflexes au filet | Technique | Réduit le ReactionDelay au filet (balles rapides, passing shots) |
| Toucher au filet | Technique | HitQuality pour les volées fines, amorties et placements au filet |
| Smash | Technique | HitQuality pour le smash sur lob adverse |
| Couverture du filet | Technique | Qualité du CourtPosition de départ au filet pour couvrir les angles adverses |
| Contre | Technique | HitQuality pour un Coup d'Intention AGGRESSIVE depuis ReachResult = STRETCHED |
| Glissade | Technique | Réduit le RequiredTime sur surface CLAY (glissade = extension de reach sans perte d'Équilibre) ; réduit additionnellement la probabilité de glissement accidentel sur CLAY humide |
| Passing | Technique | HitQuality pour le passing shot quand l'adversaire est en CourtPosition filet |
| Lob | Technique | HitQuality pour le lob (Trajectoire à arc z élevé) quand l'adversaire est en CourtPosition filet |
| Remise difficile | Technique | Qualité d'une ShotExecution défensive sur balle near-MISSED : contact, contrôle minimal, hauteur suffisante |
| Lift | Technique | Modifie le Rebond : rebond haut et lourd — augmente la distance de frappe requise pour l'adversaire |
| Slice | Technique | Modifie le Rebond : rebond bas et rapide — réduit le AvailableTime de l'adversaire |
| Amortie coup droit | Technique | HitQuality pour l'amortie (Trajectoire courte + basse) côté coup droit |
| Amortie revers | Technique | HitQuality pour l'amortie côté revers |
| Clutch | Mental | Modificateur de HitQuality sur les points importants (balles de break, jeux décisifs, balles de match) — score-based |
| Concentration | Mental | Résistance à la dérive de HitQuality sur la durée du match |
| Confiance | Mental | Coefficient de résistance : module l'impact de Moral bas sur les Attributs Mental en match |
| Combativité | Mental | Modificateur de HitQuality quand le score est défavorable |
| Lecture du jeu | Intelligence de jeu | Réduit le ReactionDelay général (anticipation des intentions et trajectoires adverses) |
| Placement | Intelligence de jeu | Qualité du CourtPosition de base visé lors du Replacement (baseline et filet) |
| Choix des coups | Intelligence de jeu | Qualité de sélection du shot intent et du type de Coup ; intègre la conscience des propres Attributs du joueur |
| Construction du point | Intelligence de jeu | Qualité du CourtPosition cible pour maximiser le déplacement adverse et construire un avantage positionnel sur plusieurs Coups |
| Vision du court | Intelligence de jeu | Qualité géométrique du CourtPosition cible — espaces libres, angles créés |
| Exploitation des faiblesses | Intelligence de jeu | Module le gain extrait en ciblant les Attributs faibles adverses — persistance et précision du ciblage sous pression |

## Testing

### Stack

JUnit 5 (`@Test`) + AssertJ (`assertThat`). Pas de Spring context — tous les tests sont des tests unitaires purs qui instancient directement la classe testée.

### Règles

**Pas de mocks.** Instancier les dépendances directement (`new ServeSimulator(new Random(42))`). Si une dépendance devient trop lourde à instancier, c'est un signal de design, pas une raison d'introduire Mockito.

**Random seedé.** Toujours passer `new Random(seed)` avec un seed fixe pour les tests déterministes. Utiliser `new Random(0)` ou `new Random(42)` par convention.

**Tests statistiques pour les comportements probabilistes.** Lancer N tirages (typiquement 1000), asserter sur un compteur avec une marge >3σ. Documenter le raisonnement statistique en commentaire inline :
```java
// P(in)=0.99 over 1000 trials: threshold 970 is >3σ below the mean 990
assertThat(inCount).isGreaterThan(969);
```

**Service comme champ de classe** quand il n'a pas de dépendance aléatoire :
```java
private final PlayerMovementService service = new PlayerMovementService();
```

**Méthodes helper privées** regroupées en bas du fichier sous un séparateur `// ─── helpers ─────`. Elles construisent les proto builders (`TennisPlayerSnapshot`, `CourtPosition`, `BallFlightSegment`) pour éviter la répétition dans chaque test.

**Nommage** : camelCase, exprime le comportement attendu en anglais (`highReliabilityFirstServeGoesInNearlyAlways`, `producesLateWhenMarginIsSmallPositive`).

**Pas de `@BeforeEach`** sauf si le setup est identique dans tous les tests de la classe. Préférer des helpers appelés explicitement dans chaque test.

## Termes à éviter dans ce contexte

| Éviter | Utiliser | Raison |
|---|---|---|
| Tir | Coup | Terme canonique |
| Raté | ReachResult = MISSED | Plus précis |
| Erreur | FAUTE_NON_FORCÉE ou FAUTE_FORCÉE | Trop ambigu |
| Créneau, Activité, Contrat | — | Concepts du contexte WebApp, absents du Simulator |
| Match physique | Match | "physique" est redondant, toute simulation est physique ici |
| Probabilité de faute indépendante | Bruit d'exécution physique | L'erreur émerge de ShotExecution puis de la Trajectoire, pas d'un tirage `in/out` séparé |

## Exemple de dialogue

> **Dev** : Quand on dit qu'un joueur "rate" la balle, c'est quoi exactement ?
>
> **Domaine** : C'est un ReachResult = MISSED — AvailableTime est inférieur à RequiredTime. Le Point se termine là, IssueDuPoint = COUP_GAGNANT pour l'adversaire.
>
> **Dev** : Et une faute directe en coup droit, c'est différent ?
>
> **Domaine** : Oui, complètement. Le joueur a atteint la balle (ReachResult = COMFORTABLE ou STRETCHED), mais son exécution physique a généré une trajectoire dehors ou dans le filet. Si le joueur était confortable et que la difficulté adverse était faible, c'est une FAUTE_NON_FORCÉE. Si la faute vient d'une position difficile créée par l'adversaire, c'est une FAUTE_FORCÉE.
>
> **Dev** : Et le HitQuality, c'est calculé avant ou après la trajectoire ?
>
> **Domaine** : Avant. Le HitQuality est calculé dès que le joueur frappe — il définit l'amplitude du bruit d'exécution. Ensuite la Trajectoire est intégrée physiquement ; la faute, le filet ou la balle bonne émergent de cette trajectoire.
