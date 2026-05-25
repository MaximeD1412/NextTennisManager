# Context — NextManagerTennis (Simulation Engine)

Le moteur de simulation reçoit le snapshot d'un Match (TennisPlayers, Tactiques, surface, format) et produit un résultat point par point en modélisant la physique du jeu. Ce contexte définit le vocabulaire propre au moteur — distinct du vocabulaire de gestion du jeu (WebApp).

## Glossaire

### CourtPosition
Un point dans l'espace 3D du terrain, défini par trois coordonnées : **x** (largeur, 0 = côté gauche), **y** (profondeur, 0 = filet, positif vers le fond de court), **z** (hauteur au-dessus de la surface, 0 = sol). L'origine est au centre du filet. Ne pas utiliser "coordonnées" seul — utiliser CourtPosition.

### Coup
Une frappe de balle effectuée par un joueur. Caractérisé par : une zone cible (CourtPosition), une vitesse initiale, un type de spin, et un niveau de risque issu de la Tactique. Le Coup génère une Trajectoire et déclenche un calcul de HitQuality. Ne pas utiliser "tir" — utiliser "Coup".

Cas spécial — **BOISÉ** : un Coup sur le cadre de la raquette, extrêmement rare. Le joueur a atteint la balle (ReachResult valide, généralement COMFORTABLE ou LATE) mais le contact est raté. Représenté par `frameHit: true` sur l'événement `SHOT` du stream. HitQuality forcé à quasi zéro ; variance de Trajectoire maximale et aléatoire — la balle peut atterrir n'importe où. Le Point continue normalement après l'impact. Pas un ReachResult distinct ni une IssueDuPoint distincte.

### Trajectoire
Le chemin 3D paramétrique d'un Coup depuis la frappe jusqu'au Rebond ou la sortie du terrain. Décrite par une CourtPosition de départ, une CourtPosition d'arrivée **effective**, une durée, une hauteur de pic, un vecteur de spin, et une vitesse. La CourtPosition d'arrivée effective est calculée à partir de la zone cible (issue de la Tactique) avec une variance modulée par le HitQuality : un HitQuality élevé produit une arrivée précise ; un HitQuality faible (ReachResult `STRETCHED` ou `DESPERATE`) produit une dispersion plus large, pouvant envoyer la balle hors des limites du terrain ou dans le filet. Si la CourtPosition d'arrivée effective est hors limites, l'IssueDuPoint est `FAUTE_NON_FORCÉE` ou `FAUTE_FORCÉE` selon le HitQuality.

Sur les surfaces extérieures (`CLAY`, `GRASS`, `HARD`), le Vent courbe la balle en vol — la déviation latérale croît avec la hauteur (`z`) et la durée de vol. Le simulateur émet la Trajectoire en segments successifs via `BALL_FLIGHT_SEGMENT`, calculés progressivement (pas de pré-calcul de la trajectoire complète à l'impact). Sur `INDOOR_HARD` (sans Vent), un seul `BALL_FLIGHT_SEGMENT` suffit par Coup. Sert de base au calcul de AvailableTime pour l'adversaire.

### Rebond
L'impact de la balle sur la surface du terrain. Modifie la vitesse et la direction de la balle selon la surface (`CLAY` | `GRASS` | `HARD` | `INDOOR_HARD`). La surface influence la hauteur du rebond (clay = rebond haut et lent, grass = rebond bas et rapide) et donc le AvailableTime du joueur suivant. L'Humidité de Surface modifie ces paramètres sur les surfaces extérieures — voir Humidité de Surface.

### AvailableTime
Le temps dont dispose un joueur entre sa position courante et le moment où il doit frapper. Recalculé à chaque `BALL_FLIGHT_SEGMENT` reçu : chaque segment précise la zone d'arrivée probable, le joueur ajuste son déplacement en conséquence. Sur `INDOOR_HARD` (sans Vent), un seul segment suffit — la zone d'arrivée est connue dès l'impact adverse. Sur les surfaces extérieures, la zone d'arrivée se précise progressivement ; un Vent plus fort que prévu sur un lob peut dégrader le ReachResult attendu jusqu'au dernier segment. Réduit par le ReactionDelay du joueur (influencé par l'Attribut anticipation).

### RequiredTime
Le temps minimal nécessaire au joueur pour couvrir la distance entre sa CourtPosition courante et la zone d'arrivée de la balle, et se stabiliser avant la frappe. Dépend de : la distance à parcourir, les Attributs physiques du joueur (vitesse, explosivité), le coefficient de Fatigue (0.0–1.0), et l'Humidité de Surface (courbes par surface — réduction sur CLAY humide, augmentation sur GRASS et HARD humides). Ne pas confondre avec AvailableTime — ces deux valeurs sont calculées indépendamment puis comparées.

### ReachResult
Le résultat de la comparaison `AvailableTime − RequiredTime`. Cinq valeurs :
- `COMFORTABLE` — marge largement positive. Geste complet préparé. HitQuality élevé ; intent Tactique maintenu.
- `LATE` — marge légèrement insuffisante. Geste raccourci mais stable. HitQuality dégradé ; intent Tactique maintenu.
- `STRETCHED` — marge négative faible. Joueur en bout de course, étiré, geste réduit au coup de poignet. HitQuality fortement dégradé ; intent dégradé d'un cran (`AGGRESSIVE → NEUTRAL`).
- `DESPERATE` — marge très négative. Balle à peine touchée du bout de la raquette. HitQuality quasi nul, variance de Trajectoire maximale ; intent forcé à `DEFENSIVE` quel que soit l'intent Tactique.
- `MISSED` — marge négative franche. Le joueur n'atteint pas la balle : le Point se termine immédiatement (IssueDuPoint = `COUP_GAGNANT` pour l'adversaire).

### HitQuality
Coefficient normalisé (0.0–1.0) représentant la qualité d'exécution d'un Coup. Déterminé par : la marge du ReachResult, le coefficient de Fatigue, et les Attributs Technique du joueur (coup droit, revers, service, volley selon le type de Coup). Pilote la variance de la CourtPosition d'arrivée effective de la Trajectoire autour de la zone cible : un HitQuality de 1.0 produit une Trajectoire précise ; un HitQuality proche de 0.0 (ReachResult `DESPERATE`, haute Fatigue) produit une dispersion maximale pouvant envoyer la balle hors des limites du terrain ou dans le filet. L'IssueDuPoint (`FAUTE_NON_FORCÉE`, `FAUTE_FORCÉE`) est déterminé par la CourtPosition d'arrivée effective — pas par une probabilité échantillonnée séparément.


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
L'interface fixe entre le contexte WebApp et le Simulator, définie dans ADR-0004. Le simulateur reçoit : les Attributs de chaque TennisPlayer (1–99), leurs états dynamiques (Fatigue, Rythme, Moral en 0.0–1.0), leur Tactique active, la surface du Match, le format (BEST_OF_3 | BEST_OF_5), le VENT_MOYEN du Créneau `(direction: float, intensité: float)`, `surface_wetness_initial: float` (0.0–1.0), `precipitation_active: bool`, et `surface_drying_rate: float` (taux de séchage par Point, calculé par le WebApp depuis la base ConfigurationGlobale modulée par la Protection du terrain du Tournoi) — les quatre derniers nuls/false/0.0 pour `INDOOR_HARD`. Il retourne : le vainqueur, le score complet, les stats du Match, et — si activé — les événements de replay. Les paramètres physiques internes (vitesse maximale par unité d'Attribut, courbes d'ErrorProbability, etc.) sont tunables via ConfigurationGlobale/MondeSetting et ne font **pas** partie du contrat fixe.

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
| Fiabilité service | Technique | ErrorProbability sur la première balle de service |
| Second service | Technique | ErrorProbability et HitQuality sur la deuxième balle de service |
| Variété service | Technique | Coefficient réducteur sur l'effet de Lecture de service adverse (réduit la réduction du ReactionDelay) |
| Retour coup droit | Technique | HitQuality pour le retour de service joué côté coup droit |
| Retour revers | Technique | HitQuality pour le retour de service joué côté revers |
| Lecture de service | Technique | Réduit le ReactionDelay sur le retour (anticipation zone/effet/vitesse du service) |
| Puissance coup droit | Technique | Vitesse initiale de balle dans la Trajectoire du coup droit |
| Précision coup droit | Technique | Dispersion autour du CourtPosition cible pour le coup droit |
| Régularité coup droit | Technique | ErrorProbability pour le coup droit |
| Coup droit en course | Technique | Taux de dégradation du HitQuality coup droit quand ReachResult = STRETCHED |
| Puissance revers | Technique | Vitesse initiale de balle dans la Trajectoire du revers |
| Précision revers | Technique | Dispersion autour du CourtPosition cible pour le revers |
| Régularité revers | Technique | ErrorProbability pour le revers |
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
| Remise difficile | Technique | Probabilité de remettre en jeu une balle avec ReachResult near-MISSED (Intention DEFENSIVE) |
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

## Termes à éviter dans ce contexte

| Éviter | Utiliser | Raison |
|---|---|---|
| Tir | Coup | Terme canonique |
| Raté | ReachResult = MISSED | Plus précis |
| Erreur | FAUTE_NON_FORCÉE ou FAUTE_FORCÉE | Trop ambigu |
| Créneau, Activité, Contrat | — | Concepts du contexte WebApp, absents du Simulator |
| Match physique | Match | "physique" est redondant, toute simulation est physique ici |
| ErrorProbability | — | Concept supprimé — l'erreur émerge de la CourtPosition d'arrivée effective de la Trajectoire, modulée par HitQuality |

## Exemple de dialogue

> **Dev** : Quand on dit qu'un joueur "rate" la balle, c'est quoi exactement ?
>
> **Domaine** : C'est un ReachResult = MISSED — AvailableTime est inférieur à RequiredTime. Le Point se termine là, IssueDuPoint = COUP_GAGNANT pour l'adversaire.
>
> **Dev** : Et une faute directe en coup droit, c'est différent ?
>
> **Domaine** : Oui, complètement. Le joueur a atteint la balle (ReachResult = COMFORTABLE ou STRETCHED), mais l'ErrorProbability a déclenché — le Coup atterrit dehors. Si le HitQuality était élevé malgré tout, c'est une FAUTE_NON_FORCÉE. Si le HitQuality était faible parce que l'adversaire l'a mis sous pression, c'est une FAUTE_FORCÉE.
>
> **Dev** : Et le HitQuality, c'est calculé avant ou après l'ErrorProbability ?
>
> **Domaine** : Avant. Le HitQuality est calculé dès que le joueur frappe — il définit la dispersion de la Trajectoire et la valeur d'ErrorProbability. Ensuite on tire l'ErrorProbability. Dans cet ordre.
