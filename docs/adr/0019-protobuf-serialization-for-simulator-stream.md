# ADR-0019 — Protobuf comme format de sérialisation du stream simulateur

## Status
Accepted

## Context

Le simulateur émet un stream d'événements consommé par deux voies (ADR-0018) :

- **Live** — WebSocket depuis le simulateur vers le frontend Next.js, via Redis. Haute fréquence : un Match avec Vent sur surface extérieure génère de nombreux `BALL_FLIGHT_SEGMENT` et `PLAYER_MOVE`.
- **Batch** — persisté pour les stats et le replay différé. Volume modéré (events structurels + `SHOT`).

La stack est Spring Boot (simulateur, producteur) et Next.js / TypeScript (frontend, consommateur). Le simulateur est un worker isolé dispatché via RabbitMQ (ADR-0006).

Deux formats de sérialisation ont été évalués : JSON et Protocol Buffers (Protobuf).

## Decision

**Protobuf** est retenu comme format de sérialisation du stream d'événements du simulateur — pour le stream Live (WebSocket) et pour la persistance Batch.

### Pourquoi pas JSON

JSON a été rejeté pour trois raisons :

1. **Verbosité à haute fréquence.** Les événements `BALL_FLIGHT_SEGMENT` et `PLAYER_MOVE` transportent des coordonnées 3D flottantes et des timestamps émis en grande quantité par Match. La verbosité JSON (noms de champs répétés, absence de compression de types numériques) représente un surcoût non négligeable sur le stream WebSocket.

2. **Absence d'enforcement du contrat à la compilation.** Le contrat du simulateur (ADR-0004) est une interface fixe entre deux services distincts — le simulateur Spring Boot et le frontend TypeScript. JSON laisse la dérive de schéma silencieuse jusqu'au runtime. Une erreur de typage sur un payload `SHOT` n'est détectée que lors de l'exécution, potentiellement en production.

3. **Migration coûteuse.** "Commencer en JSON et migrer plus tard" a été rejeté car la migration touche simultanément les deux extrémités du WebSocket — le producteur (simulateur) et le consommateur (frontend) doivent être déployés ensemble. La complexité de la migration justifie de partir directement sur Protobuf.

### Pourquoi Protobuf

1. **Encodage binaire compact.** Les types numériques (CourtPosition, HitQuality, timestamps) sont encodés efficacement — les payloads sont significativement plus petits qu'en JSON pour les mêmes données.

2. **Schéma comme contrat compilé.** Le fichier `.proto` définit l'ensemble des 14 types d'événements (ADR-0018) et leurs champs. Spring Boot et Next.js génèrent des clients typés depuis ce fichier — toute modification du schéma produit une erreur de compilation dans les deux services avant le déploiement.

3. **Évolution de schéma balisée.** Protobuf assure la compatibilité ascendante via les numéros de champs — un champ ajouté en version N+1 n'invalide pas les consommateurs de la version N. Les champs supprimés sont marqués `reserved`. La gestion de version du schéma est explicite.

### Gouvernance du `.proto`

Le fichier `.proto` devient partie intégrante du contrat du simulateur (ADR-0004). Il vit dans un module partagé référencé par le simulateur Spring Boot et par le frontend Next.js. Toute modification nécessite une mise à jour coordonnée des deux services.

Les types partagés (CourtPosition, ScoreSnapshot, PlayerTactic, MatchStats) sont définis dans le même fichier ou dans des fichiers `.proto` importés — jamais redéfinis localement dans chaque service.

## Consequences

- Un fichier `.proto` (ou ensemble de fichiers) définit les 14 types d'événements du stream (ADR-0018) et les types de données partagés (`CourtPosition`, `ScoreSnapshot`, etc.). C'est la source de vérité des types pour les deux services.
- Spring Boot utilise `protoc` + le plugin Java pour générer les classes d'événements. Next.js utilise `protoc` + le plugin TypeScript (e.g. `ts-proto`) pour les types et helpers de sérialisation.
- Un changement de schéma (ajout, renommage, suppression de champ) exige : (1) mise à jour du `.proto`, (2) régénération des clients dans les deux services, (3) déploiement coordonné.
- Les champs supprimés doivent être marqués `reserved` dans le `.proto` — jamais réutilisés avec un numéro de champ existant.
- Les paramètres physiques internes du simulateur (coefficients d'accumulation de fatigue, poids d'ErrorProbability, etc.) ne font pas partie du `.proto` — ils restent dans ConfigurationGlobale et ne transitent pas dans le stream.
- Le format Protobuf s'applique au stream simulateur uniquement. Les autres API du projet (REST WebApp, WebSocket de gestion) ne sont pas concernées par cette décision.
