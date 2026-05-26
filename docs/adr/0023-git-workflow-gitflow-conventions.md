# ADR-0023 — Git Workflow : Gitflow, Conventional Commits, CI par contexte

## Statut
Accepté

## Contexte

Le projet est un monorepo avec deux contextes distincts (WebApp Next.js, Simulator Spring Boot). Tous les travaux sont tracés via GitHub Issues. En phase de conception, l'équipe est solo mais le workflow doit tenir à l'échelle d'une équipe plus large.

L'objectif principal : garder `main` propre (production stable) et `develop` propre (intégration continue testée). Toute dérive de qualité sur ces branches est coûteuse à rattraper.

## Décision

### Stratégie de branches — Gitflow

| Branche | Rôle |
|---|---|
| `main` | Production stable. Ne reçoit que des `release/*` et `hotfix/*`. |
| `develop` | Intégration continue. Toujours en état de marche et testé. |
| `feat/<issue>-<description>` | Fonctionnalité liée à une issue GitHub. |
| `fix/<issue>-<description>` | Correction de bug liée à une issue GitHub. |
| `release/vX.Y.Z` | Préparation d'une release. Branche depuis `develop`, merge dans `main` + `develop`. |
| `hotfix/<description>` | Correction urgente. Branche depuis `main`, merge dans `main` + `develop`. |

**Règle fondamentale** : toute branche de travail (`feat/*`, `fix/*`) est obligatoirement associée à une issue GitHub. Le numéro d'issue apparaît dans le nom de la branche. Une issue peut avoir plusieurs branches (cas multi-repo futur).

### Nommage des branches

Format : `type/<issue-number>-<description-kebab-case>`

Exemples :
- `feat/42-ajout-systeme-meteo`
- `fix/87-regression-attribut-vitesse`
- `release/v1.2.0`
- `hotfix/crash-simulator-null`

### Convention de commits — Conventional Commits

Format : `type(scope): description (#issue)`

Scopes valides : `simulator`, `webapp`

Types : `feat`, `fix`, `chore`, `refactor`, `docs`, `test`, `perf`

Exemples :
- `feat(simulator): add weather progression system (#42)`
- `fix(webapp): correct attribute regression on save (#87)`
- `feat!: breaking change description (#99)` → déclenche un major bump

### Règles de merge

**`develop`** (via PR `feat/*` ou `fix/*`) :
- Stratégie : **squash merge** (un commit par issue sur `develop`)
- Reviewers : 0 (configurable quand l'équipe grandit)
- CI requise : build + tests unitaires verts

**`main`** (via PR `release/*` ou `hotfix/*`) :
- Stratégie : **merge commit** (pas de squash, pour que le tag soit ancré sur un vrai commit)
- Reviewers : 1 approbation obligatoire
- CI requise : build + tests unitaires + build de production
- Tag `vX.Y.Z` obligatoire à chaque merge

Les pushes directs sont bloqués sur `main` et `develop` — tout passe par une PR.

### Processus de release

Trois workflows automatisés gèrent le cycle complet :

1. **`release.yml`** — déclenché manuellement (`workflow_dispatch`) avec un paramètre version optionnel :
   - Si version non fournie : calcule le bump depuis les Conventional Commits sur `develop` (`feat` → minor, `fix` → patch, `feat!` / `BREAKING CHANGE` → major)
   - Génère le `CHANGELOG.md` via git-cliff (`cliff.toml`)
   - Crée la branche `release/vX.Y.Z` depuis `develop`
   - Ouvre automatiquement une PR `release/vX.Y.Z` → `main`

2. **`tag-release.yml`** — déclenché automatiquement quand une PR `release/*` merge dans `main` :
   - Crée le tag `vX.Y.Z` annoté sur `main`
   - Crée la GitHub Release avec le CHANGELOG
   - Merge automatiquement `main` dans `develop` (merge commit)

Le `CHANGELOG.md` est généré par **git-cliff** configuré dans `cliff.toml`. Les commits `chore(release):` sont exclus du CHANGELOG.

### CI par contexte (path filters)

Deux workflows indépendants dans `.github/workflows/` :
- `ci-webapp.yml` — déclenché si `NextManagerTennis_WebApp/**` change
- `ci-simulator.yml` — déclenché si `NextManagerTennis_Simulator/**` change

## Alternatives considérées

**GitHub Flow** (une seule branche stable) : plus simple, mais ne garantit pas la séparation entre travail en cours et code prêt à shipper. Insuffisant pour maintenir `main` propre pendant des cycles de développement longs.

**Trunk-based development** : requiert une CI très rapide, des feature flags matures, et une culture de tests solide. Prématuré pour ce stade du projet.

## Conséquences

- Chaque PR doit référencer une issue via `Closes #<n>` — GitHub ferme l'issue automatiquement au merge.
- L'historique de `develop` est lisible : un commit squashé par issue, avec référence d'issue.
- L'historique de `main` est linéaire et tagué : chaque point de l'historique correspond à une version shippée.
- Le calcul de version automatique ne fonctionne que si les messages de commit respectent strictement Conventional Commits.
