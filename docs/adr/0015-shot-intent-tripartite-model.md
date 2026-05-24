# ADR-0015 — Modèle d'Intention de Coup tripartite (AGGRESSIVE / NEUTRAL / DEFENSIVE)

## Status
Accepted

## Context

Le simulateur doit distinguer des comportements techniques qui s'activent dans des situations radicalement différentes :

- Un joueur en bonne position (ReachResult = COMFORTABLE) peut jouer agressivement ou se contenter de remettre.
- Un joueur en situation difficile (ReachResult = STRETCHED) peut tenter un contre-attaque percutante ou simplement remettre la balle en jeu.
- Un joueur en situation quasi-impossible (near-MISSED) n'a qu'un seul objectif : survivre.

Deux Attributs distincts ont été identifiés pour ces scénarios : **Contre** (capacité à produire un Coup AGGRESSIVE depuis une position STRETCHED) et **Remise difficile** (probabilité de remettre en jeu depuis near-MISSED). Ces deux Attributs ont des hooks différents et ne peuvent pas être réduits à un seul Attribut "En course".

Un modèle sans Intent aurait rendu ces distinctions inimplémentables : sans catégorisation de l'intention, il est impossible de savoir quel Attribut activer pour le calcul du HitQuality.

## Decision

Chaque Coup reçoit une **Intention de Coup** avant le calcul du HitQuality :

- `AGGRESSIVE` — le joueur cherche à faire mal (profondeur, vitesse, angle, conversion défense→attaque).
- `NEUTRAL` — le joueur joue sans prise de risque offensive.
- `DEFENSIVE` — le joueur cherche uniquement à remettre en jeu.

**Détermination de l'Intent :**

1. La Tactique exprime un intent préféré (niveau d'agressivité configuré par l'utilisateur).
2. Le ReachResult contraint l'intent : `COMFORTABLE` → intent Tactique maintenu ; `STRETCHED` → dégradation d'un cran (`AGGRESSIVE` → `NEUTRAL`) ; `STRETCHED` sévère → forcé à `DEFENSIVE`.
3. L'Attribut Intelligence de jeu Choix des coups module la qualité de cette décision — un joueur à faible Choix des coups peut tenter un AGGRESSIVE depuis une position STRETCHED sans avoir les moyens de l'exécuter.

**Attributs activés par Intent :**

| Intent | Situation | Attribut principal |
|---|---|---|
| `AGGRESSIVE` depuis `COMFORTABLE` | Normal | Puissance + Précision (wing correspondant) |
| `AGGRESSIVE` depuis `STRETCHED` | Contre | Contre |
| `NEUTRAL` | Normal | Régularité (wing correspondant) |
| `DEFENSIVE` depuis `STRETCHED` | En course | En course (wing correspondant) |
| `DEFENSIVE` depuis near-MISSED | Remise difficile | Remise difficile |

## Consequences

- Le simulateur doit déterminer l'Intent avant chaque calcul de HitQuality.
- L'Intent est une valeur éphémère par Coup — non persistée entre les points.
- Les Attributs Contre, Remise difficile et En course ont des zones d'activation mutuellement exclusives, ce qui évite les interactions non intentionnelles.
- La Tactique reste le seul point de configuration de l'intent préféré — l'utilisateur ne configure pas l'Intent directement, il configure la Tactique.
- Un modèle à Intent unique (e.g. "toujours NEUTRAL quand STRETCHED") aurait rendu Contre non-implémentable et supprimé un vecteur de différenciation stratégique important entre joueurs.
