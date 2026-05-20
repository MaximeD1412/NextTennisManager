# Architecture du projet — Jeu de management de tennis

## 1. Vision générale du projet

L’objectif est de créer un jeu de management de tennis avec une interface web permettant au joueur de gérer sa partie, suivre des matchs en direct, intervenir tactiquement pendant une rencontre, consulter les résultats, les statistiques, les classements et, plus tard, interagir avec le jeu depuis Discord.

Le jeu doit pouvoir gérer deux types de simulation :

1. **Simulation interactive**
   - Match suivi en direct par le joueur.
   - Possibilité de changer la tactique en cours de match.
   - Interface mise à jour en temps réel.
   - Sauvegarde optionnelle d’un replay détaillé.

2. **Simulation massive**
   - Matchs joués sans affichage direct.
   - Objectif long terme : pouvoir simuler des millions, voire des milliards de matchs.
   - Sauvegarde minimale des données importantes.
   - Pas de replay détaillé par défaut pour éviter l’explosion du stockage.

L’architecture doit donc être pensée autour de deux contraintes principales :

- **Temps réel pour les matchs suivis par un utilisateur.**
- **Stockage très optimisé pour les volumes massifs de matchs.**

---

## 2. Stack technique retenue

### Frontend

**NextJS + TypeScript**

Rôle :

- Interface web du jeu.
- Pages de gestion : joueurs, calendrier, entraînements, tournois, résultats.
- Interface live match.
- Connexion WebSocket pour recevoir les événements en direct.
- Appels REST classiques pour récupérer les états initiaux, les historiques, les classements, etc.

Pourquoi ce choix est cohérent :

- Très bon pour construire une webapp moderne.
- Compatible avec rendu serveur, client, dashboards, routing avancé.
- TypeScript apporte plus de fiabilité sur un projet de simulation complexe.
- Facile à connecter à une API Java.

---

### Backend principal

**Java + Spring Boot**

Rôle :

- API principale du jeu.
- Authentification.
- Gestion des utilisateurs.
- Gestion des parties.
- Gestion des joueurs, tournois, calendriers, entraînements, contrats, classements.
- Exposition des données au frontend.
- Relais WebSocket pour les matchs en direct.
- Lecture/écriture PostgreSQL.
- Communication avec Redis et RabbitMQ.
- Orchestration des simulations.

Pourquoi ce choix est cohérent :

- Solide pour une API structurée.
- Excellent pour les transactions, les règles métier et la persistance.
- Compatible avec PostgreSQL, Redis, RabbitMQ, WebSocket.
- Adapté à un projet long terme avec beaucoup de logique métier.

---

### Base de données

**PostgreSQL**

Rôle :

- Stockage durable des données importantes.
- Données relationnelles :
  - utilisateurs
  - parties
  - joueurs
  - clubs / académies / équipes si besoin
  - tournois
  - matchs
  - résultats
  - statistiques
  - historiques de classement
  - blessures
  - entraînements
  - contrats
  - tactiques utilisées
  - métadonnées de replay

Pourquoi PostgreSQL plutôt que NoSQL :

Le jeu aura énormément de relations fortes :

- Un joueur participe à des tournois.
- Un tournoi contient des matchs.
- Un match appartient à une saison.
- Une partie contient plusieurs joueurs, calendriers, historiques.
- Les statistiques sont liées à des matchs, des sets, des points, des surfaces.

C’est naturellement relationnel.

PostgreSQL permet aussi d’utiliser `jsonb` pour certaines données semi-flexibles :

- configuration spéciale de tournoi
- snapshot léger de joueur
- paramètres de simulation
- métadonnées de replay
- stats expérimentales

Conclusion :

> PostgreSQL est le meilleur choix comme base principale.

---

### Redis

**Redis pour le live, le cache et les états temporaires**

Rôle :

- État live des matchs.
- Events live courts via Redis Streams.
- Commandes tactiques envoyées au simulateur.
- Cache rapide.
- État temporaire des matchs en cours.
- Possiblement leaderboard temporaire, sessions, rate limiting plus tard.

Redis ne doit pas remplacer PostgreSQL.

Il sert surtout pour :

- données temporaires
- faible latence
- temps réel
- coordination entre Spring Boot et les workers de simulation

---

### RabbitMQ

**RabbitMQ pour les jobs de simulation asynchrones**

Rôle :

- Lancer une simulation.
- Envoyer un job du backend vers un worker.
- Gérer des tâches en arrière-plan :
  - démarrer un match
  - simuler un match non suivi en direct
  - simuler un tour de tournoi
  - simuler une journée complète
  - traiter une génération de statistiques
  - produire un replay compressé

RabbitMQ est adapté pour :

- files de messages fiables
- consommation par workers
- retry en cas d’échec
- découplage entre backend et simulateur

---

### Kafka

Kafka n’est pas retenu au départ.

Pourquoi :

- Trop lourd pour le besoin initial.
- Plus adapté à de l’event streaming massif, de l’analytics en temps réel, du replay d’événements à grande échelle.
- Complexité opérationnelle supérieure.

Kafka pourrait devenir intéressant beaucoup plus tard si le jeu génère énormément d’événements analytiques, mais ce n’est pas nécessaire pour un MVP.

---

### Simulateur

Deux options principales ont été discutées :

#### Option 1 : simulateur en Java

Avantages :

- Simplicité maximale.
- Pas de communication inter-langage.
- Même modèle métier que le backend.
- Déploiement plus simple.
- Moins de risques de divergence.

Inconvénients :

- Moins agréable pour expérimenter des modèles statistiques complexes.
- Moins naturel pour certains calculs scientifiques ou exploratoires.

#### Option 2 : simulateur en Python

Avantages :

- Très bon pour itérer rapidement sur les formules.
- Adapté aux simulations probabilistes, statistiques, Monte Carlo.
- Accès facile à NumPy, Pandas, SciPy, etc.
- Bon choix si la simulation devient très expérimentale.

Inconvénients :

- Nécessite communication Java ↔ Python.
- Nécessite synchronisation des modèles.
- Plus de complexité de déploiement.
- Besoin de gérer Redis/RabbitMQ proprement.

Décision recommandée :

- Pour un MVP simple : simulateur intégré en Java possible.
- Pour un moteur plus avancé : worker Python pertinent.
- Vu l’ambition de simulation avancée, Python reste un choix cohérent, à condition de bien séparer les responsabilités.

---

### Discord bot

Prévu plus tard.

Rôle futur :

- Voir les résultats.
- Recevoir des notifications.
- Gérer certaines actions de jeu.
- Consulter les matchs en cours.
- Peut-être changer une tactique depuis Discord.

Architecture recommandée :

Le bot Discord ne doit pas contenir la logique métier.

Il doit appeler l’API Spring Boot :

```txt
Discord Bot -> Spring Boot API -> PostgreSQL / Redis / Simulateur
```

Cela évite de dupliquer la logique du jeu.

---

## 3. Architecture globale cible

Architecture logique :

```txt
NextJS Webapp
   |
   | REST + WebSocket
   v
Spring Boot API
   |
   | PostgreSQL : données durables
   | Redis : live state, cache, events courts, commandes
   | RabbitMQ : jobs asynchrones
   v
Python Simulation Workers
   |
   | écrit les états live dans Redis
   | publie les events live
   | calcule les stats
   | produit éventuellement les fichiers replay
   v
PostgreSQL + stockage fichiers
```

Version simplifiée :

```txt
NextJS
  <-> Spring Boot
        <-> PostgreSQL
        <-> Redis
        <-> RabbitMQ
              <-> Python Worker
```

---

## 4. Rôle précis de chaque composant

### NextJS

Responsabilités :

- Afficher l’interface.
- Charger les données initiales via REST.
- Se connecter au WebSocket pour les matchs live.
- Envoyer les changements tactiques.
- Afficher le score, les stats, les événements, les animations.
- Rejouer éventuellement un match depuis un fichier replay déjà interprété par Java.

Ne doit pas :

- contenir la logique de simulation
- décider du résultat d’un point
- écrire directement en DB
- parler directement à Redis ou RabbitMQ

---

### Spring Boot

Responsabilités :

- API REST.
- Auth.
- Validation des actions utilisateur.
- WebSocket.
- Accès PostgreSQL.
- Lecture/écriture Redis.
- Publication de jobs dans RabbitMQ.
- Relais des événements live vers l’interface.
- Sauvegarde des snapshots importants en DB.
- Coordination générale.

Ne doit pas forcément :

- garder tous les matchs live en mémoire comme source de vérité
- simuler tout le match si le simulateur est externalisé
- sauvegarder chaque micro-événement en PostgreSQL

---

### Redis

Responsabilités :

- État live courant d’un match.
- Events live récents.
- Commandes tactiques.
- Buffer temporaire.
- Rattrapage d’événements manqués lors de connexion WebSocket.

Exemples de clés Redis :

```txt
match:{matchId}:state
match:{matchId}:events
match:{matchId}:commands
```

Exemple d’état live :

```json
{
  "matchId": 123,
  "status": "LIVE",
  "score": "4-3 30-15",
  "serverId": 12,
  "receiverId": 18,
  "setNumber": 1,
  "gameNumber": 8,
  "pointNumber": 43,
  "fatigueA": 0.72,
  "fatigueB": 0.65,
  "tacticA": "AGGRESSIVE_BASELINE",
  "tacticB": "DEFENSIVE_COUNTER",
  "sequence": 187
}
```

---

### RabbitMQ

Responsabilités :

- Démarrer des simulations.
- Envoyer des jobs aux workers.
- Gérer les simulations non interactives.
- Gérer les traitements lourds.

Exemple de message :

```json
{
  "type": "START_MATCH_SIMULATION",
  "matchId": 123,
  "gameId": 42,
  "simulationMode": "LIVE",
  "saveReplay": true,
  "replayDetail": "FULL"
}
```

---

### Python Worker

Responsabilités :

- Exécuter le moteur de simulation.
- Simuler les points progressivement.
- Lire les commandes tactiques depuis Redis.
- Appliquer les changements tactiques au bon moment.
- Publier les événements live dans Redis.
- Mettre à jour l’état courant dans Redis.
- Calculer les statistiques du match.
- Produire un fichier replay si demandé.
- Signaler la fin du match.

---

## 5. Fonctionnement d’un match live

### Objectif

Le joueur doit pouvoir :

- arriver sur la page à n’importe quel moment
- voir l’état exact du match
- suivre la suite en direct
- changer la tactique en cours de match
- recevoir les événements sans recharger la page

---

### Flux général

```txt
1. Spring crée un match en DB.
2. Spring publie un job RabbitMQ : démarrer la simulation.
3. Python worker récupère le job.
4. Python initialise l’état du match.
5. Python écrit l’état courant dans Redis.
6. Python simule progressivement.
7. Python publie les événements dans Redis Streams.
8. Spring écoute ou lit ces événements.
9. Spring pousse les updates aux clients via WebSocket.
10. Spring sauvegarde périodiquement les snapshots importants en PostgreSQL.
11. À la fin, Python calcule les stats finales.
12. Python/Spring sauvegarde le résumé en PostgreSQL.
13. Si activé, un fichier replay compact est produit.
```

---

## 6. Arrivée d’un utilisateur sur un match en cours

Problème :

L’utilisateur peut arriver alors que le match est déjà lancé.

Il faut donc lui donner :

1. l’état actuel exact
2. les événements live à venir
3. les éventuels événements manqués entre le chargement initial et l’ouverture WebSocket

Solution :

> modèle snapshot + live events.

### Étape 1 : chargement initial

NextJS appelle :

```http
GET /matches/{matchId}/live-state
```

Spring lit Redis :

```txt
match:{matchId}:state
```

Spring renvoie :

```json
{
  "matchId": 123,
  "score": "4-3 30-15",
  "serverId": 12,
  "receiverId": 18,
  "fatigueA": 0.72,
  "fatigueB": 0.65,
  "sequence": 187,
  "status": "LIVE"
}
```

### Étape 2 : ouverture WebSocket

NextJS ouvre :

```txt
/ws/matches/{matchId}?lastSequence=187
```

### Étape 3 : rattrapage

Spring lit dans Redis Streams les événements depuis la séquence 188.

Puis Spring envoie :

- les événements manqués
- les nouveaux événements live

Cela évite la perte d’événement entre :

```txt
GET live-state
```

et :

```txt
WebSocket connected
```

---

## 7. Pourquoi Redis Streams plutôt que Redis Pub/Sub

### Redis Pub/Sub

Avantage :

- très simple
- faible latence

Inconvénient :

- si le client n’est pas connecté, il rate les messages
- pas de rattrapage

### Redis Streams

Avantage :

- les événements restent disponibles temporairement
- possibilité de reprendre à partir d’un ID ou d’une séquence
- meilleur pour le modèle snapshot + rattrapage
- plus robuste pour WebSocket

Conclusion :

> Redis Streams est préférable pour les événements live de match.

Pub/Sub peut être utilisé pour des notifications très simples, mais Redis Streams est plus sûr pour le live match.

---

## 8. Changement tactique en plein match

Objectif :

L’utilisateur doit pouvoir modifier la tactique de son joueur pendant le match.

Exemple :

- jouer plus agressif
- monter davantage au filet
- viser le revers adverse
- réduire la prise de risque
- économiser l’énergie
- changer le positionnement au retour

Flux recommandé :

```txt
NextJS
  -> WebSocket / REST command
Spring Boot
  -> valide l’action
  -> écrit une commande dans Redis
Python Worker
  -> lit la commande
  -> applique la tactique au prochain point ou au prochain coup pertinent
  -> publie un événement TACTIC_CHANGED
Spring Boot
  -> relaie à NextJS
```

Exemple de commande Redis :

```json
{
  "type": "TACTIC_CHANGE",
  "matchId": 123,
  "playerId": 12,
  "newTactic": "AGGRESSIVE_BASELINE",
  "requestedAtSequence": 192
}
```

Exemple d’événement de confirmation :

```json
{
  "type": "TACTIC_CHANGED",
  "matchId": 123,
  "playerId": 12,
  "newTactic": "AGGRESSIVE_BASELINE",
  "appliedAtSequence": 195
}
```

Important :

La tactique ne doit pas forcément être appliquée instantanément au milieu d’un calcul physique.

Elle peut être appliquée :

- au prochain point
- au prochain coup
- au prochain temps mort logique
- au prochain changement de côté

Cela dépend du niveau de précision souhaité.

---

## 9. Spring doit-il garder les matchs en mémoire ?

Réponse :

> Spring peut garder un cache local, mais ne doit pas être la source de vérité principale du live.

Source de vérité live :

```txt
Redis
```

Spring peut garder :

```java
Map<Long, LiveMatchState> liveMatches;
```

Mais uniquement comme cache temporaire.

Spring doit surtout garder :

- les connexions WebSocket ouvertes
- la liste des clients abonnés à un match
- le dernier état lu
- le dernier snapshot sauvegardé
- des buffers courts pour optimiser les écritures DB

Mais si Spring redémarre, il doit pouvoir reconstruire l’état depuis Redis.

---

## 10. Sauvegarde PostgreSQL pendant un match live

Il ne faut pas écrire chaque micro-événement en PostgreSQL.

À éviter :

```txt
chaque déplacement de balle -> PostgreSQL
chaque position joueur -> PostgreSQL
chaque point intermédiaire -> PostgreSQL
```

Cela deviendrait trop lourd.

À faire :

- sauvegarder l’état initial du match
- sauvegarder les changements tactiques importants
- sauvegarder les fins de jeu
- sauvegarder les fins de set
- sauvegarder la fin du match
- éventuellement sauvegarder un snapshot toutes les X secondes
- sauvegarder les statistiques finales
- sauvegarder un lien vers un fichier replay si activé

PostgreSQL est la base durable et analytique, pas le bus temps réel.

---

## 11. Modèle de simulation

### Principes

Le moteur de simulation peut être très détaillé en interne, mais il ne faut pas forcément tout sauvegarder.

Différence importante :

```txt
simulation interne détaillée
≠
données persistées
```

Pendant le match, le simulateur peut calculer :

- position des joueurs
- position de la balle
- vitesse
- accélération
- anticipation
- vision du jeu
- fatigue
- mental
- momentum
- choix tactique
- qualité d’appui
- qualité de frappe
- prise de risque
- vent
- surface
- rebond
- spin
- trajectoire
- probabilité d’erreur

Mais à la fin, il peut ne sauvegarder que :

- résultat
- stats globales
- stats par set
- stats par point si nécessaire
- événements de replay si activé
- résumé tactique
- fichier replay optionnel

---

## 12. Calcul de capacité à atteindre la balle

Oui, le simulateur peut déterminer si un joueur peut atteindre ou non la balle.

Principe :

```txt
temps disponible avant arrivée de la balle
vs
temps nécessaire au joueur pour atteindre la zone
```

Variables possibles :

- position initiale du joueur
- position cible de la balle
- vitesse maximale du joueur
- accélération
- explosivité
- endurance
- fatigue actuelle
- anticipation
- vision du jeu
- qualité du placement
- surface
- type de déplacement
- direction du déplacement
- temps de réaction
- équilibre après le coup précédent

Exemple de logique :

```txt
available_time = ball_arrival_time - player_reaction_delay

required_time =
  reaction_time
  + acceleration_phase
  + movement_time_to_target
  + stabilization_time_before_hit

if required_time <= available_time:
    player_can_reach_ball = true
else:
    player_can_reach_ball = false
```

La qualité de la frappe peut ensuite dépendre de la marge :

```txt
marge = available_time - required_time
```

Si la marge est grande :

- joueur bien placé
- frappe confortable
- meilleure précision
- meilleure puissance

Si la marge est faible :

- frappe en bout de course
- précision réduite
- risque d’erreur augmenté
- choix tactique limité

Si la marge est négative :

- balle non atteinte
- point perdu ou coup désespéré

---

## 13. Vision du jeu et micro-déplacements

La vision du jeu peut servir à simuler des déplacements intelligents.

Un joueur avec une bonne vision :

- anticipe mieux la direction probable du coup adverse
- se replace plus tôt
- couvre mieux les angles
- réduit la distance à parcourir au coup suivant
- choisit une meilleure position d’attente
- lit mieux le style adverse
- gère mieux les zones dangereuses

Important :

Un bon joueur n’est pas seulement plus rapide.

Il est souvent :

```txt
mieux placé plus tôt
```

Exemples d’effets de la vision :

```txt
vision élevée :
- réduction du temps de réaction
- meilleur choix de position de replacement
- meilleure prédiction de trajectoire
- meilleure couverture du terrain

vision faible :
- retard au démarrage
- replacement approximatif
- mauvaise couverture des angles
- plus de courses inutiles
```

Cela peut produire des micro-déplacements entre les coups.

Ces micro-déplacements peuvent être simulés en interne, mais pas forcément tous sauvegardés.

---

## 14. Statistiques et simulations avancées

Le tennis est très adapté aux statistiques.

Statistiques possibles :

### Stats classiques

- aces
- doubles fautes
- % de premières balles
- points gagnés sur première balle
- points gagnés sur seconde balle
- balles de break obtenues
- balles de break converties
- fautes directes
- coups gagnants
- points gagnés au filet
- points gagnés en retour
- tie-breaks gagnés
- durée moyenne des échanges

### Stats avancées

- qualité moyenne du placement
- distance parcourue
- fatigue générée par set
- efficacité tactique
- zones les plus attaquées
- vulnérabilité côté revers
- agressivité moyenne
- risque moyen pris par coup
- points gagnés sous pression
- clutch factor
- momentum
- efficacité en défense
- capacité de couverture du terrain
- qualité moyenne des choix tactiques
- variation de performance selon fatigue

### Stats de simulation

- probabilité de victoire avant match
- probabilité de victoire après chaque jeu
- impact d’un changement tactique
- évolution de la fatigue
- évolution du mental
- comparaison entre tactiques
- simulations Monte Carlo

---

## 15. Monte Carlo

À terme, le moteur pourrait simuler un match ou un tournoi plusieurs fois pour produire des probabilités.

Exemple :

```txt
Match A vs B sur terre battue
Simulation 10 000 fois
Résultat :
- A gagne 56 %
- B gagne 44 %
```

Utilisation possible :

- prédiction avant match
- analyse tactique
- scouting
- IA coach
- estimation des chances de tournoi
- cotes internes au jeu
- aperçu Discord

Pas nécessaire pour le MVP, mais très intéressant plus tard.

---

## 16. ELO / rating

Il est recommandé d’utiliser un système de rating.

Possibilités :

- ELO global
- ELO par surface
- rating forme récente
- rating mental
- rating service
- rating retour

Exemple :

```txt
Player A:
- ELO global: 2180
- ELO terre: 2250
- ELO gazon: 2030
- forme récente: +4.2 %
```

Cela peut influencer :

- les probabilités de point
- la confiance
- les classements internes
- les prédictions
- les simulations massives

---

## 17. Replay : problématique

Objectif initial :

- Sauvegarder un replay complet ou semi-complet.
- Permettre un affichage du match.
- Ne pas exploser le stockage.
- Viser idéalement moins de 100 KB par match pour les replays enregistrés.
- Ne pas forcément stocker du frame-by-frame.
- Ne pas forcément imposer un replay déterministe.

Conclusion importante :

> Pour rester sous 100 KB, il ne faut pas stocker toutes les positions à chaque frame.

Il faut stocker des événements compacts et reconstruire l’animation.

---

## 18. Replay déterministe vs replay événementiel

### Replay déterministe

Principe :

```txt
seed RNG + paramètres + inputs = match reconstruit à l’identique
```

Avantage :

- fichiers extrêmement petits

Inconvénients :

- moteur doit être parfaitement déterministe
- les anciennes versions du moteur doivent rester disponibles
- toute modification du simulateur peut casser les anciens replays
- complexité de compatibilité

Cette option a été écartée car l’objectif n’est pas d’imposer le déterminisme.

---

### Replay événementiel

Principe :

On stocke les événements nécessaires pour reconstruire visuellement et statistiquement le match.

Exemples :

- début de point
- déplacement joueur
- trajectoire balle
- frappe
- rebond
- faute
- fin de point
- changement tactique

Avantages :

- pas besoin de déterminisme
- plus flexible
- compatible avec un moteur qui évolue
- permet l’affichage visuel
- stockage beaucoup plus léger que frame-by-frame

C’est l’approche recommandée.

---

## 19. Événements de replay

Plutôt que de stocker des coups figés, il est préférable de stocker des événements.

Exemples d’événements :

```txt
POINT_START
PLAYER_MOVE_SEGMENT
BALL_FLIGHT_SEGMENT
HIT
BOUNCE
BALL_OUT
BALL_NET
TACTIC_CHANGE
FATIGUE_UPDATE
POINT_END
GAME_END
SET_END
MATCH_END
```

La clé est de stocker des segments, pas des positions toutes les 50 ms.

### Exemple : déplacement joueur

```txt
PLAYER_MOVE_SEGMENT
- playerId
- startTimeDelta
- duration
- startX
- startY
- endX
- endY
- movementType
```

Le frontend ou Java peut interpoler le déplacement.

### Exemple : déplacement balle

```txt
BALL_FLIGHT_SEGMENT
- startTimeDelta
- duration
- startX
- startY
- startZ
- endX
- endY
- endZ
- curveType
- peakHeight
- spin
- speed
- windInfluence
```

L’animation peut ensuite reconstruire une trajectoire courbe.

---

## 20. Vent et trajectoire de balle

Le fait de stocker des événements de mouvement permet d’ajouter le vent plus tard.

Exemple :

```txt
BALL_FLIGHT_SEGMENT
- start position
- end position
- duration
- peak height
- spin
- wind vector
- curve modifier
```

Pas besoin de stocker chaque position intermédiaire.

Le rendu peut reconstruire la courbe à partir des paramètres.

Le simulateur peut aussi utiliser le vent en interne pour modifier :

- la trajectoire
- la longueur de balle
- la précision
- le rebond
- le risque d’erreur

---

## 21. Objectif de taille du replay

Objectif souhaité :

```txt
< 100 KB par match
```

C’est possible si :

- pas de frame-by-frame
- pas de JSON
- pas de floats 64 bits
- pas de positions absolues répétées partout
- usage d’enums
- positions quantifiées
- temps relatifs
- delta encoding
- compression Zstd
- événements haut niveau

C’est difficile si :

- on stocke des positions balle/joueur à haute fréquence
- on stocke trop de détails physiques
- on garde tous les micro-déplacements
- on écrit en JSON
- on sauvegarde tous les matchs en full replay

---

## 22. Format de fichier replay

Recommandation :

```txt
Protobuf + Zstandard
```

Extension custom possible :

```txt
.tnr
```

pour :

```txt
Tennis Replay
```

### Pourquoi Protobuf

Protobuf sert à définir une structure binaire typée.

Avantages :

- compact
- rapide
- compatible Java/Python
- génère des classes automatiquement
- versionnable
- beaucoup moins lourd que JSON

### Pourquoi Zstandard

Zstandard sert à compresser le binaire produit par Protobuf.

Avantages :

- très bon ratio de compression
- très rapide
- adapté aux données répétitives
- meilleur choix que gzip dans beaucoup de cas modernes

Pipeline :

```txt
Objets simulation
  -> Protobuf bytes
  -> Zstd compression
  -> fichier .tnr
```

Lecture :

```txt
fichier .tnr
  -> Zstd decompression
  -> Protobuf decode
  -> objets Java
  -> replay / analyse
```

---

## 23. Optimisations de taille

### Quantization

Ne pas stocker :

```txt
x = 12.348927123 mètres
```

Stocker plutôt :

```txt
x = 1235 centimètres
```

ou même :

```txt
x = 124 décimètres
```

Selon le niveau de précision nécessaire.

### Delta encoding

Ne pas stocker :

```txt
x = 5234
x = 5236
x = 5237
```

Stocker :

```txt
5234
+2
+1
```

Les petits nombres se compressent très bien.

### Temps relatifs

Ne pas stocker des timestamps complets.

Stocker :

```txt
dt = +120 ms
duration = 450 ms
```

### Enums

Ne pas stocker :

```txt
"AGGRESSIVE_BASELINE_FOREHAND_TOPSPIN"
```

Stocker :

```txt
shotType = 3
```

### Champs optionnels

Ne stocker un champ que s’il est utile.

Exemple :

- pas de vent si vent = 0
- pas de spin si non pertinent
- pas de détail de fatigue sur tous les événements

---

## 24. Exemple de structure Protobuf conceptuelle

```proto
message ReplayFile {
  ReplayHeader header = 1;
  MatchSummary summary = 2;
  repeated PointReplay points = 3;
}

message ReplayHeader {
  uint32 format_version = 1;
  uint64 match_id = 2;
  uint32 simulator_version = 3;
  uint32 ruleset_version = 4;
  uint32 court_type = 5;
  uint32 compression = 6;
}

message MatchSummary {
  uint32 winner_player_index = 1;
  string final_score = 2;
  uint32 total_points = 3;
  uint32 duration_seconds = 4;
}

message PointReplay {
  uint32 point_index = 1;
  uint32 server_index = 2;
  uint32 winner_index = 3;
  repeated ReplayEvent events = 4;
}

message ReplayEvent {
  uint32 dt = 1;
  oneof event {
    PlayerMoveSegment player_move = 2;
    BallFlightSegment ball_flight = 3;
    HitEvent hit = 4;
    BounceEvent bounce = 5;
    TacticChangeEvent tactic_change = 6;
    PointEndEvent point_end = 7;
  }
}

message PlayerMoveSegment {
  uint32 player_index = 1;
  sint32 dx = 2;
  sint32 dy = 3;
  uint32 duration_ms = 4;
  uint32 movement_type = 5;
}

message BallFlightSegment {
  sint32 dx = 1;
  sint32 dy = 2;
  sint32 dz = 3;
  uint32 duration_ms = 4;
  sint32 peak_height = 5;
  sint32 spin = 6;
  sint32 speed = 7;
  sint32 wind_x = 8;
  sint32 wind_y = 9;
}

message HitEvent {
  uint32 player_index = 1;
  uint32 shot_type = 2;
  uint32 hand = 3;
  uint32 quality = 4;
  uint32 risk = 5;
}

message BounceEvent {
  sint32 dx = 1;
  sint32 dy = 2;
  sint32 bounce_type = 3;
}

message TacticChangeEvent {
  uint32 player_index = 1;
  uint32 new_tactic = 2;
}

message PointEndEvent {
  uint32 winner_index = 1;
  uint32 reason = 2;
}
```

Ce n’est pas un schéma final, mais une base de réflexion.

---

## 25. Modes de sauvegarde par partie

Il est recommandé d’avoir des paramètres de sauvegarde.

Exemple :

```txt
saveReplay: true/false
replayDetail: NONE / LIGHT / FULL
```

### NONE

Pour les simulations massives.

Sauvegarde :

- résultat final
- stats principales
- classement mis à jour
- éventuellement stats très agrégées

Pas de fichier replay.

### LIGHT

Pour matchs importants mais sans replay visuel complet.

Sauvegarde :

- résultat
- stats
- résumé point par point
- momentum
- tactiques utilisées
- points clés

Fichier très petit.

### FULL

Pour matchs suivis par un utilisateur ou matchs importants.

Sauvegarde :

- événements détaillés
- trajectoires compactes
- déplacements joueurs
- tactiques
- points complets
- replay visuel possible

Fichier replay compressé.

---

## 26. Politique de stockage recommandée

Pour éviter l’explosion du stockage à long terme :

```txt
99.9 % des matchs : NONE ou LIGHT
0.1 % des matchs : FULL
```

Exemples :

### Matchs simulés en masse

```txt
replayDetail = NONE
```

### Matchs de l’utilisateur

```txt
replayDetail = FULL ou LIGHT
```

### Finales importantes

```txt
replayDetail = LIGHT ou FULL
```

### Matchs Discord consultables rapidement

```txt
replayDetail = LIGHT
```

---

## 27. Conséquence importante : nouvelles stats après mise à jour

Si certains détails ne sont pas sauvegardés, alors il sera impossible de recalculer de nouvelles statistiques historiques plus tard.

Exemple :

Si en version 1 tu ne sauvegardes pas les déplacements joueurs, et qu’en version 2 tu ajoutes une statistique :

```txt
distance parcourue
```

Alors tu ne pourras pas la calculer pour les anciens matchs.

Ce n’est pas forcément grave.

Il faut simplement assumer :

```txt
Les anciennes stats restent limitées aux données sauvegardées à l’époque.
```

Bonne pratique :

- versionner les stats
- versionner les replays
- versionner le moteur de simulation
- accepter que les anciens matchs ne contiennent pas toutes les nouvelles métriques

---

## 28. PostgreSQL : données à sauvegarder

### Table `matches`

Exemple de champs :

```sql
id
game_id
tournament_id
round
player_a_id
player_b_id
winner_id
status
surface
started_at
finished_at
final_score
duration_seconds
simulation_mode
replay_detail
created_at
updated_at
```

### Table `match_sets`

```sql
id
match_id
set_number
player_a_games
player_b_games
tiebreak_score
winner_id
```

### Table `match_stats`

```sql
id
match_id
player_id
aces
double_faults
first_serve_percentage
first_serve_points_won
second_serve_points_won
break_points_created
break_points_converted
winners
unforced_errors
net_points_won
total_points_won
distance_covered
fatigue_end
created_at
```

### Table `match_replays`

```sql
id
match_id
storage_path
format_version
simulator_version
ruleset_version
replay_detail
compressed_size_bytes
checksum
created_at
```

### Table `match_tactic_events`

Optionnelle, si on veut historiser les changements tactiques même sans replay full.

```sql
id
match_id
player_id
point_index
sequence
old_tactic
new_tactic
created_at
```

---

## 29. Stockage des fichiers replay

En développement :

```txt
stockage local
```

En production :

```txt
S3 ou MinIO
```

Organisation possible :

```txt
match_replays/
  2026/
    05/
      20/
        match_12345.tnr
```

En DB :

```txt
storage_path = match_replays/2026/05/20/match_12345.tnr
```

---

## 30. Cycle complet d’un match live avec replay FULL

```txt
1. L’utilisateur lance ou rejoint un match.
2. Spring crée le match en PostgreSQL.
3. Spring publie un job RabbitMQ.
4. Python worker démarre le match.
5. Python initialise l’état Redis.
6. NextJS récupère le snapshot via REST.
7. NextJS ouvre la WebSocket.
8. Python simule événement par événement.
9. Python écrit l’état courant dans Redis.
10. Python écrit les events live dans Redis Streams.
11. Spring relaie les events via WebSocket.
12. L’utilisateur change la tactique.
13. Spring écrit une commande Redis.
14. Python lit la commande et l’applique.
15. Python continue la simulation.
16. À la fin du match, Python calcule les stats.
17. Python produit le fichier replay .tnr si demandé.
18. Spring ou Python sauvegarde les stats finales en PostgreSQL.
19. Spring marque le match comme terminé.
20. NextJS affiche le résumé final.
```

---

## 31. Cycle complet d’un match simulé en masse sans replay

```txt
1. Un job de simulation est créé.
2. RabbitMQ envoie le job à un worker.
3. Python simule le match sans publier tous les événements live.
4. Python calcule uniquement les stats utiles.
5. Résultat et stats principales sauvegardés en PostgreSQL.
6. Aucun fichier replay n’est produit.
```

Ce mode est indispensable pour tenir le volume long terme.

---

## 32. Roadmap technique recommandée

### Phase 1 — MVP simple

Objectif :

Avoir un jeu fonctionnel sans sur-ingénierie.

À faire :

- NextJS
- Spring Boot
- PostgreSQL
- modèle joueur
- modèle match
- modèle tournoi simple
- simulation basique
- résultat final
- stats simples
- pas encore de replay détaillé
- pas encore de Redis Streams complexe si pas nécessaire

Simulation possible directement en Java ou Python simple.

---

### Phase 2 — Match live basique

À ajouter :

- Redis
- WebSocket
- état live du match
- events simples
- changement tactique entre les points
- UI live
- snapshot initial via REST
- rattrapage minimal

Events simples :

```txt
POINT_WON
GAME_WON
SET_WON
TACTIC_CHANGED
MATCH_END
```

---

### Phase 3 — Worker externe

À ajouter :

- RabbitMQ
- Python worker
- jobs de simulation
- séparation backend / simulation
- meilleure scalabilité
- simulations en arrière-plan

---

### Phase 4 — Replay LIGHT / FULL

À ajouter :

- Protobuf
- Zstd
- format `.tnr`
- stockage fichier local puis S3/MinIO
- table `match_replays`
- replay événementiel compact
- lecture côté Java
- affichage côté frontend

---

### Phase 5 — Simulation avancée

À ajouter :

- fatigue avancée
- vision du jeu
- anticipation
- micro-déplacements
- styles de jeu
- surfaces détaillées
- météo / vent
- momentum
- mental
- tactiques plus fines
- statistiques avancées

---

### Phase 6 — Simulation massive

À ajouter :

- batchs de simulation
- workers multiples
- monitoring
- optimisation stockage
- politiques de suppression/archivage
- sauvegarde uniquement des données importantes
- éventuellement partitionnement PostgreSQL

---

### Phase 7 — Discord bot

À ajouter :

- bot Discord
- commandes de consultation
- notifications
- changements tactiques depuis Discord si souhaité
- appels à l’API Spring Boot

---

## 33. Décisions techniques retenues

### Décision 1

**PostgreSQL comme base principale.**

Raison :

- données relationnelles fortes
- fiable
- robuste
- adapté aux stats
- flexible avec `jsonb`

---

### Décision 2

**Redis pour le live.**

Raison :

- faible latence
- état temporaire
- Redis Streams pour rattrapage d’événements
- commandes tactiques

---

### Décision 3

**RabbitMQ pour les jobs.**

Raison :

- fiable
- simple
- adapté aux workers
- moins lourd que Kafka

---

### Décision 4

**WebSocket entre Spring Boot et NextJS.**

Raison :

- nécessaire pour le match live
- updates instantanés
- changement tactique possible

---

### Décision 5

**Replay événementiel compact, pas frame-by-frame.**

Raison :

- objectif < 100 KB
- replay visuel possible
- pas besoin de déterminisme
- compatible avec l’évolution du moteur

---

### Décision 6

**Protobuf + Zstd pour les fichiers replay.**

Raison :

- compact
- rapide
- compatible Java/Python
- versionnable
- bien meilleur que JSON pour ce cas

---

### Décision 7

**Sauvegarde configurable selon le type de match.**

Raison :

- éviter de stocker trop de données pour les simulations massives
- garder les replays seulement pour les matchs importants
- contrôler le coût de stockage

---

## 34. Points à surveiller

### Versioning du simulateur

Il faut versionner :

- format replay
- moteur de simulation
- ruleset
- modèle de stats

Sinon, les anciens fichiers peuvent devenir difficiles à lire.

---

### Compatibilité des replays

Même si le replay n’est pas déterministe, il faut conserver la compatibilité du format.

Un fichier `.tnr` version 1 doit rester lisible en version 2.

---

### Taille des fichiers

Il faudra benchmarker rapidement.

Faire des tests avec :

- 100 points
- 300 points
- 500 points
- replay LIGHT
- replay FULL
- compression Zstd différents niveaux

Objectif :

```txt
FULL moyen < 100 KB si possible
LIGHT très inférieur à 100 KB
NONE = aucun fichier
```

---

### Fréquence des événements

Le plus gros risque est de trop détailler.

À éviter :

```txt
position toutes les 50 ms
```

À préférer :

```txt
segments paramétriques
```

---

### Écritures PostgreSQL

Ne pas écrire trop souvent.

Préférer :

- snapshots
- événements importants
- stats finales

---

### Complexité du MVP

Ne pas tout construire dès le début.

Le MVP doit valider :

- boucle de jeu
- simulation
- interface
- persistance
- plaisir de jeu

Le replay détaillé peut venir après.

---

## 35. Architecture recommandée finale

```txt
Frontend:
  - NextJS
  - TypeScript
  - WebSocket client

Backend:
  - Java
  - Spring Boot
  - REST API
  - WebSocket
  - PostgreSQL access
  - Redis access
  - RabbitMQ producer

Simulation:
  - Python workers
  - simulation live
  - simulation batch
  - Redis live state
  - Redis Streams events
  - replay generation

Database:
  - PostgreSQL

Realtime:
  - Redis
  - Redis Streams
  - WebSocket

Jobs:
  - RabbitMQ

Replay:
  - Protobuf
  - Zstd
  - .tnr files
  - local storage in dev
  - S3/MinIO in prod

Future:
  - Discord bot
  - Monte Carlo
  - advanced stats
  - tactical AI
```

---

## 36. Résumé court

Le projet peut être structuré ainsi :

```txt
PostgreSQL = vérité durable
Redis = vérité live temporaire
RabbitMQ = lancement et distribution des jobs
Spring Boot = API, WebSocket, orchestration
Python = moteur de simulation avancé
NextJS = interface joueur
Protobuf + Zstd = replay compact
S3/MinIO = stockage des replays
Discord Bot = client externe de l’API
```

Le point clé :

> Ne pas confondre simulation interne, live state, données statistiques et replay.

Chaque couche a son rôle :

```txt
Simulation interne :
  très détaillée, temporaire

Redis :
  état live, events récents, commandes

PostgreSQL :
  stats, résultats, données durables

Replay file :
  seulement si nécessaire, compact, événementiel

Frontend :
  affichage, interpolation, interactions
```

---

## 37. Conclusion

Les bases du projet sont cohérentes.

La stack recommandée est :

```txt
NextJS + TypeScript
Spring Boot + Java
PostgreSQL
Redis
RabbitMQ
Python workers
Protobuf + Zstd
S3/MinIO plus tard
Discord bot plus tard
```

Le point d’architecture le plus important est de séparer les usages :

- PostgreSQL ne doit pas gérer le temps réel.
- Redis ne doit pas remplacer la base durable.
- RabbitMQ ne doit pas servir à streamer chaque micro-événement.
- WebSocket sert uniquement à pousser le live au frontend.
- Le replay détaillé ne doit être activé que pour les matchs qui le justifient.
- Les simulations massives doivent sauvegarder uniquement les données utiles.

Cette approche permet de commencer simplement, tout en gardant une vraie trajectoire vers un jeu ambitieux et scalable.
