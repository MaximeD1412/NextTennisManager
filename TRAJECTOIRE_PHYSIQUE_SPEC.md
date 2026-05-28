# Spec agent - Trajectoire physique, filet et service

## Objectif

Refondre la trajectoire de balle pour que le simulateur repose sur une physique 3D coherente:

- la rotation de la balle influence reellement la trajectoire;
- le filet est detecte par croisement geometrique et hauteur reelle;
- une balle qui touche la bande du filet peut passer avec une probabilite deterministe;
- le service utilise la meme physique que les autres Coups;
- la taille et l'amplitude du joueur influencent la hauteur de contact au service.

Le hasard doit representer l'execution du Coup, pas remplacer la physique.

## Diagnostic actuel

`TrajectoireService` ne calcule pas encore une trajectoire 3D continue. Il construit un segment:

```text
from = position joueur, z = hauteur de frappe fixe
to = position d'atterrissage, z = 0
duration = distance horizontale / vitesse
spin = propage au segment, mais pas utilise physiquement
```

La detection actuelle du filet revient a:

```text
si la balle atterrit du meme cote que le joueur, elle a touche le filet
sinon elle est consideree comme ayant passe le filet
```

Ce raccourci empeche de detecter:

- une balle qui traverse vers le camp adverse mais passe sous le filet;
- une balle qui passe autour des poteaux;
- une balle qui touche la bande puis retombe de l'autre cote;
- l'effet reel du topspin, du slice ou du lift;
- l'avantage physique d'un joueur grand au service.

`ServeSimulator` est encore plus separe de la physique: il tire une position d'atterrissage et decide `in/out` avec `fiabilite / 100`, sans filet, sans spin, sans vitesse, sans hauteur de contact, et sans vraie contrainte geometrique de service.

## Principe de solution

Creer un moteur physique commun pour tous les Coups, par exemple `BallPhysics` ou `TrajectoryPhysicsService`.

Ce moteur doit recevoir une specification de Coup, integrer la trajectoire dans le temps, puis retourner:

- les points ou segments de trajectoire;
- le point d'atterrissage effectif;
- l'interaction eventuelle avec le filet;
- la duree de vol;
- la vitesse au filet et a l'atterrissage;
- les informations necessaires au replay.

Le service et les coups d'echange doivent utiliser le meme moteur.

## Modele physique cible

Etat de balle:

```text
position = (x, y, z)
velocity = (vx, vy, vz)
spinRpm
spinAxis
```

Constantes de depart:

```text
BALL_MASS_KG = 0.0577
BALL_RADIUS_M = 0.0335
AIR_DENSITY = 1.225
DRAG_COEFFICIENT = 0.55
GRAVITY = 9.81
BALL_AREA = PI * BALL_RADIUS_M^2
```

Forces a chaque pas d'integration:

```text
gravity = (0, 0, -GRAVITY)

drag =
  -0.5 * AIR_DENSITY * DRAG_COEFFICIENT * BALL_AREA
  * |v|^2 * normalize(v) / BALL_MASS_KG

magnus =
  0.5 * AIR_DENSITY * liftCoefficient * BALL_AREA
  * |v|^2 * normalize(spinAxis x v) / BALL_MASS_KG
```

Coefficient Magnus:

```text
omega = spinRpm * 2PI / 60
spinRatio = BALL_RADIUS_M * omega / |v|
liftCoefficient = clamp(1.5 * spinRatio, 0.0, 0.35)
```

Integration:

```text
dt = 0.005s a 0.010s
```

Un pas semi-implicite suffit:

```text
velocity += acceleration * dt
position += velocity * dt
```

La trajectoire s'arrete quand:

- la balle touche le sol (`z <= BALL_RADIUS_M`);
- elle touche le filet et ne passe pas;
- elle sort d'une zone de simulation maximale de securite.

## Effets de balle

Le `ShotEffect` doit produire `spinRpm` et `spinAxis`.

Mapping conceptuel:

```text
FLAT
  spinRpm faible
  Magnus faible

TOPSPIN / LIFT
  spinRpm fort
  force Magnus vers le bas pendant le vol
  trajectoire lancee plus haut, puis qui redescend plus vite

SLICE
  spinRpm moyen
  backspin ou spin lateral selon le Coup
  balle plus flottante, ou courbe laterale sur service slice
```

Important: le topspin ne doit pas etre modelise comme un simple bonus de hauteur. En tennis reel, le topspin permet de frapper plus haut au-dessus du filet parce que la balle redescend plus fort ensuite. Le bon effet doit emerger de la force Magnus.

## Filet

Dimensions:

```text
NET_CENTER_HEIGHT_M = 0.914
NET_POST_HEIGHT_M = 1.07
NET_POST_X_M = 5.029
BALL_RADIUS_M = 0.0335
```

Hauteur du filet a la position laterale `x`:

```text
netHeightAtX =
  NET_CENTER_HEIGHT_M
  + (NET_POST_HEIGHT_M - NET_CENTER_HEIGHT_M)
  * min(abs(x) / NET_POST_X_M, 1.0)
```

Detection du croisement du filet:

A chaque pas d'integration, verifier si le segment traverse `y = 0`.

```text
crossesNetPlane =
  (previous.y > 0 && current.y <= 0)
  || (previous.y < 0 && current.y >= 0)
```

Si oui, interpoler le vrai point de croisement:

```text
tau = (0 - previous.y) / (current.y - previous.y)
xNet = lerp(previous.x, current.x, tau)
zNet = lerp(previous.z, current.z, tau)
vNet = lerp(previous.velocity, current.velocity, tau)
```

Regles:

```text
abs(xNet) > NET_POST_X_M + BALL_RADIUS_M
  => la balle passe autour du filet, pas de collision filet

zNet >= netHeightAtX + BALL_RADIUS_M
  => la balle passe clairement au-dessus

zNet <= netHeightAtX - BALL_RADIUS_M
  => la balle touche le filet et ne passe pas

sinon
  => contact avec la bande du filet
```

## Bande du filet

Une balle qui touche la bande ne doit pas produire automatiquement une faute. Elle doit avoir une probabilite de passer.

Ce tirage doit etre deterministe. Ne pas utiliser un `Random` global dont le resultat change si un tirage est ajoute ailleurs.

Seed conseillee:

```text
hash(matchId, pointIndex, shotIndex, "netTape")
```

Si ces identifiants ne sont pas encore disponibles localement, injecter un `Random` dedie au Coup ou prevoir l'API pour le recevoir plus tard.

Probabilite de passage:

```text
edge =
  clamp((zNet - netHeightAtX + BALL_RADIUS_M) / (2 * BALL_RADIUS_M), 0, 1)

base = lerp(0.08, 0.78, smoothstep(edge))
```

Modificateurs:

```text
speedBonus =
  clamp((speedKmhAtNet - 80) / 140, 0, 1) * 0.08

verticalVelocityBonus =
  clamp(vNet.z / 4, -1, 1) * 0.08

spinBonus:
  TOPSPIN / LIFT => +0.06 a +0.10
  FLAT           =>  0.00
  SLICE          => -0.04
  service slice lateral => 0.00 a +0.02
```

Probabilite finale:

```text
pPass = clamp(
  base + speedBonus + verticalVelocityBonus + spinBonus,
  0.03,
  0.90
)
```

Si la bande passe, la trajectoire ne doit pas rester normale. Il faut appliquer une perte d'energie au contact et continuer l'integration:

```text
velocity.x *= 0.55 a 0.85
velocity.y *= 0.55 a 0.85
velocity.z = max(0.15, abs(velocity.z) * 0.10 a 0.30)
spinRpm *= 0.50 a 0.80
```

Une bande qui passe donne souvent une balle courte. Le moteur doit donc recalculer l'atterrissage apres le contact, pas conserver la cible initiale.

## Service

Le service doit devenir un cas de `ShotSpec`, pas un simulateur probabiliste separe.

Le flux cible:

```text
ServeSimulator
  1. construit une intention de service
  2. choisit vitesse, effet, cible et bruit d'execution
  3. appelle le moteur physique commun
  4. classe le resultat selon filet + carre de service
```

Le service ne doit plus decider:

```text
random.nextDouble() < fiabilite / 100
```

La fiabilite doit plutot reduire:

- le bruit angulaire;
- le bruit de hauteur de contact;
- le bruit de direction;
- le risque de mauvais lancer ou de mauvais contact.

### Cibles de service

Le serveur est derriere une ligne de fond. Il doit viser le carre de service oppose.

Verifier le repere du terrain:

```text
y = 0      => filet
y > 0      => un cote du court
y < 0      => autre cote du court
baseline   => +/- 11.89
```

Si le serveur est a `y = +11.89`, la cible de service doit avoir `y < 0`.

Le `TARGET_Y = 3.2f` actuel est suspect parce qu'il peut viser le meme cote selon la position du serveur. Le `ServeSimulator` doit recevoir ou connaitre la `CourtPosition` du serveur.

### Classification du service

Apres integration:

```text
filet touche et ne passe pas
  => faute

bande touchee, passe, atterrit dans le bon carre
  => let

bande touchee, passe, atterrit hors du bon carre
  => faute

filet passe clairement, atterrit dans le bon carre
  => service in

filet passe clairement, atterrit hors du bon carre
  => faute
```

Le contrat proto cible contient maintenant un evenement `ServeLet`. Traiter un let comme une faute serait faux.

### Premiere et deuxieme balle

Premiere balle:

```text
speedKmh = 155 + 70 * puissanceServiceNorm
spinRpm = faible a moyen, selon varieteService et effet choisi
targetDepth = plutot profond dans le carre
executionNoise = depend surtout de fiabiliteService et precisionService
```

Deuxieme balle:

```text
speedKmh = 110 + 50 * puissanceServiceNorm
spinRpm = plus eleve, souvent LIFT/TOPSPIN/SLICE
targetDepth = plus securise
executionNoise = depend surtout de secondService
netMargin recherchee plus haute
```

La deuxieme balle doit naturellement produire moins d'aces, moins de fautes filet directes chez un bon serveur, mais plus de services attaquables.

## Taille, amplitude et hauteur de contact au service

La morphologie du joueur est indispensable au service. Un joueur plus grand, avec plus d'amplitude, frappe la balle plus haut. Cela agrandit la fenetre d'angle entre le filet et le carre de service.

Ajouter au contrat du simulateur:

```proto
float height_m = 55;
float standing_reach_m = 56;
float body_mass_kg = 57;
PlayerHand dominant_hand = 58;
```

`standing_reach_m` est plus utile qu'une notion vague d'amplitude: c'est la hauteur maximale atteinte par la main debout. Elle peut etre generee cote WebApp a la creation du joueur a partir de taille, morphologie et amplitude.
`body_mass_kg` sert aux calculs de fatigue, d'inertie et de risque de glissement/blessure. `dominant_hand` sert a la geometrie du service, aux zones de confort, aux angles et au spin lateral.

Hauteur de contact au service:

```text
serveContactHeight =
  standingReachM
  + racketSweetSpotReachM
  + jumpLiftM
  - contactLossM
  + contactNoiseM
```

Valeurs de depart:

```text
racketSweetSpotReachM = 0.42 a 0.50

athleticismNorm =
  average(agilite, equilibre, jeu_de_jambes) / 99

serviceTechniqueNorm =
  average(puissance_service, precision_service, fiabilite_service) / 99

jumpLiftM =
  0.06 + 0.22 * athleticismNorm * fatigueFactor

contactLossM =
  lerp(0.14, 0.03, serviceTechniqueNorm)

contactNoiseM =
  gaussian(0, sigmaContact)
```

Bruitage:

```text
sigmaContact premiere balle =
  lerp(0.10, 0.025, fiabilite_service / 99)

sigmaContact deuxieme balle =
  lerp(0.08, 0.020, second_service / 99)
```

Ordres de grandeur:

```text
joueur 1.75m, standing reach ~2.30m => contact ~2.75 a 2.90m
joueur 1.90m, standing reach ~2.50m => contact ~2.95 a 3.15m
joueur 2.05m, standing reach ~2.70m => contact ~3.15 a 3.35m
```

La hauteur fixe actuelle de `2.5m` sous-estime la plupart des services realistes et supprime l'avantage des grands joueurs.

L'avantage ne doit pas etre un bonus probabiliste. Il doit emerger de:

```text
zContact plus haut
=> meilleure clearance au filet
=> plus grande fenetre angulaire
=> plus de services rapides possibles dans le carre
```

## API interne conseillee

Creer des types internes simples:

```java
record ShotSpec(
    CourtPosition contactPosition,
    CourtPosition target,
    ShotType shotType,
    ShotEffect shotEffect,
    ShotIntent shotIntent,
    float hitQuality,
    Vector3 initialVelocity,
    float spinRpm,
    Vector3 spinAxis,
    long deterministicSeed
) {}
```

```java
record TrajectoryResult3d(
    CourtPosition landing,
    List<BallFlightSegment> segments,
    NetInteraction netInteraction,
    float durationMs,
    float speedKmhAtLanding
) {}
```

```java
sealed interface NetInteraction {
    record Clear(float xNet, float zNet, float netHeight) implements NetInteraction {}
    record AroundPost(float xNet) implements NetInteraction {}
    record TapePassed(float xNet, float zNet, float pPass) implements NetInteraction {}
    record NetFault(float xNet, float zNet, float netHeight) implements NetInteraction {}
    record NotCrossed() implements NetInteraction {}
}
```

Le nom exact peut changer, mais separer le resultat de filet rendra les tests et le replay beaucoup plus clairs.

## Tests attendus

Trajectoire et filet:

- une balle qui atterrit du meme cote sans traverser `y = 0` produit une faute filet;
- une balle qui traverse `y = 0` trop bas produit une faute filet;
- une balle qui traverse au-dessus de `netHeight + radius` passe;
- une balle avec `abs(xNet) > NET_POST_X + radius` passe autour du filet;
- la hauteur du filet est plus elevee pres des poteaux qu'au centre;
- une balle dans la zone de bande peut passer avec un seed deterministe;
- le spin modifie la probabilite de passage sur la bande;
- une bande qui passe modifie la vitesse et produit un nouvel atterrissage.

Spin:

- a vitesse et direction comparables, TOPSPIN/LIFT redescend plus vite qu'un FLAT;
- SLICE/BACKSPIN flotte davantage qu'un TOPSPIN;
- le spin lateral d'un service slice modifie `x` pendant le vol.

Service:

- le service vise le carre oppose au serveur;
- le service qui touche le filet et ne passe pas est faute;
- le service qui passe le filet mais atterrit hors carre est faute;
- le service qui passe le filet et atterrit dans le bon carre est in;
- le let existe: bande touchee, passe, atterrit dans le carre;
- la deuxieme balle est moins rapide et plus spinnee que la premiere;
- un joueur avec `standing_reach_m` plus eleve a une hauteur de contact plus haute;
- a execution identique, le grand joueur obtient une meilleure marge filet qu'un petit joueur.

Regression:

- les tests existants de variance `HitQuality -> landing` doivent rester valables conceptuellement, mais la variance doit s'appliquer aux parametres d'execution physiques, pas seulement a une position finale tiree directement.

## Plan d'implementation conseille

1. Ajouter les constantes physiques et un petit type vecteur interne si aucun n'existe.
2. Extraire la generation de trajectoire dans un service commun.
3. Ajouter la detection de croisement du filet et `NetInteraction`.
4. Ajouter la logique bande deterministe avec effet du spin.
5. Faire passer `TrajectoireService` par le moteur commun.
6. Ajouter `height_m`, `standing_reach_m`, `body_mass_kg`, `dominant_hand`, `ServeLet`, `Bounce` et `NetInteraction` au proto, puis regenerer les classes.
7. Refaire `ServeSimulator` pour generer un `ShotSpec` de service.
8. Ajouter le resultat de let au modele de service / event stream.
9. Calibrer les constantes avec des tests de distribution et quelques scenarios types.

## Points de calibration

Les constantes initiales doivent etre considerees comme des valeurs de depart. Il faudra calibrer contre des distributions plausibles:

- pourcentage de premieres balles;
- taux de double faute;
- nombre de lets;
- fautes filet vs fautes longues/laterales;
- vitesse moyenne premiere/deuxieme balle;
- avantage statistique des grands serveurs;
- taux de fautes selon TOPSPIN/FLAT/SLICE.

Le but n'est pas une simulation CFD parfaite. Le but est que les decisions du point emergent d'une physique 3D stable, explicable et testable.
