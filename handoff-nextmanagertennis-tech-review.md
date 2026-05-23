# Handoff — NextManagerTennis : MyClub/MyPlayer/Sandbox finalisé, système Mod

## Project

**NextManagerTennis** — multiplayer online tennis management game. Multi-context monorepo at `/mnt/c/Users/MaximeDUPRE/PROJECTS/NextTennisManager/`.

- Domain glossary: [`NextManagerTennis_WebApp/CONTEXT.md`](NextManagerTennis_WebApp/CONTEXT.md)
- Context map: [`CONTEXT-MAP.md`](CONTEXT-MAP.md)
- System-wide ADRs: [`docs/adr/`](docs/adr/) (0001–0010 accepted)
- Tech architecture reference: [`docs/architecture_jeu_management_tennis.md`](docs/architecture_jeu_management_tennis.md)
- No production code exists yet — project is in design/planning phase.

## What happened in this session

Session `/grill-with-docs` couvrant tous les items de l'agenda du handoff précédent. Toutes les décisions sont reflétées dans `CONTEXT.md`. Do not re-litigate closed decisions.

### Contrats TennisPlayer en MyClub (MC1)

| # | Decision |
|---|---|
| MC1 | Contrats `TENNIS_PLAYER` en MyClub **identiques à Multi** — durée 1–3 Saisons, même compensation de résiliation, même frais de renouvellement. La tension stratégique (verrouiller un Salaire bas sur un joueur jeune) est préservée. |

### Réputation du Club (RC1)

| # | Decision |
|---|---|
| RC1 | Réputation = **Tours atteints pondérés par Catégorie de Tournoi**, agrégé ATP+WTA. Cohérent avec le modèle Objectifs de Sponsor. Poids exacts data-driven et tunables via ConfigurationGlobale/MondeSetting. |

### Système Mod (Mod1–Mod4)

| # | Decision |
|---|---|
| Mod1 | Nouveau concept **Mod** : fichier importable initialisant un Monde Solo avec données custom. Disponible pour tous les sous-modes (MyClub, MyPlayer, Sandbox). |
| Mod2 | **Partiel** — chaque section du Mod est optionnelle. Sections absentes → fallback ConfigurationGlobale. Sections : TennisPlayers / Tournois / Clubs NPC / MondeSetting. Ligues hors scope (une seule, invisible en Solo). |
| Mod3 | Section présente = **remplacement intégral** de son équivalent ConfigurationGlobale (pas de merge). |
| Mod4 | Le développeur n'est pas l'éditeur des Mods communautaires (ex. vrais joueurs IRL). Un Mod "base officielle" avec attributs réels + noms fictifs est envisageable sans problème IP identifiable. |

ADR-0010 créé : [`docs/adr/0010-partial-mod-override-for-solo-monde-initialisation.md`](docs/adr/0010-partial-mod-override-for-solo-monde-initialisation.md)

### MyPlayer — inscription aux Tournois (MP1)

| # | Decision |
|---|---|
| MP1 | Inscription aux Tournois en MyPlayer **identique à Multi et MyClub** — délai 3 Semaines, Priorité d'Inscription, Wildcards, Qualifications. Le `USER_CONTROLLED` absorbe la friction temporelle sans simplifier la mécanique. |

### Persistance Mode Solo (Sol1)

| # | Decision |
|---|---|
| Sol1 | Mondes Solo stockés **côté serveur** (lié au compte User), cross-device. Quota de slots par User (nombre configurable) pour borner les coûts de stockage. Créer au-delà du quota nécessite de supprimer un Monde existant. |

### NPC Clubs en MyClub (NPC1–NPC2)

| # | Decision |
|---|---|
| NPC1 | **20 NPC Clubs par défaut**, entièrement configurable via MondeSetting ou Mod. |
| NPC2 | Membership NPC Club **sans effet mécanique** sur les TennisPlayers — un TennisPlayer libre est aussi fort qu'un TennisPlayer sous Contrat NPC. Les Clubs NPC existent pour la compétition de structure et l'atmosphère. |

### Compétition Interclub (CI1)

| # | Decision |
|---|---|
| CI1 | Concept **Compétition Interclub** acté comme *(future)* — événement Club-vs-Club distinct des Tournois individuels, sans coût de Fatigue, présent en Multi et optionnel en Solo. Format TBD post-launch. |

### Création du TennisPlayer en MyPlayer (MP2)

| # | Decision |
|---|---|
| MP2 | Trois modes de création disponibles au démarrage d'une partie MyPlayer, au choix du User : **Aléatoire** (tout généré), **Libre** (contrôle total), **Contraint** (contrôle total mais budget de points plafonné). |

### Files changed this session

- `NextManagerTennis_WebApp/CONTEXT.md` — updated/added : MyClub (Contrats identiques Multi, NPC Clubs 20 par défaut, membership sans effet mécanique), Réputation du Club (input Tours pondérés), Mode Solo (persistance serveur avec quota), MyPlayer (création 3 modes, inscription Tournois identique), Sandbox (référence Mod), Mod (nouveau), Compétition Interclub (nouveau, future).
- `docs/adr/0010-partial-mod-override-for-solo-monde-initialisation.md` — nouveau.

---

## Prochain objectif de session

**Discussion technologique** — le User veut prendre en main de nouveaux outils et discuter des solutions possibles pour le projet :

- **AWS** — services pertinents pour l'architecture (compute, messaging, storage, etc.)
- **ElasticSearch** — cas d'usage dans le projet (recherche de TennisPlayers, logs, analytics ?)
- **Monitoring** — outils à implémenter (observabilité, alerting, dashboards)

Contexte important : le User veut *apprendre* ces outils, pas juste les choisir abstraitement. La discussion doit donc inclure la courbe d'apprentissage et les chemins d'entrée pratiques, pas seulement les trade-offs architecturaux.

Référence technique de base : [`docs/architecture_jeu_management_tennis.md`](docs/architecture_jeu_management_tennis.md) — à relire avant la session pour avoir le contexte de l'architecture actuelle (Spring Boot, RabbitMQ, Redis, PostgreSQL, React).
