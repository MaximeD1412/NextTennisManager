# ADR-0014 — Modèle positionnel continu : CourtPosition joueur persistante entre les Coups

## Status
Accepted

## Context

Lors de la conception du simulateur, la question s'est posée de savoir depuis quelle CourtPosition un joueur commence à se déplacer vers la balle suivante.

Deux approches ont été considérées :

1. **Modèle sans mémoire** — la CourtPosition de départ du joueur pour chaque Coup est réinitialisée à un point fixe (typiquement le centre du fond de court). Simple à implémenter ; chaque Coup est calculé indépendamment. Ne modélise pas la construction du point, ne produit pas de statistiques de déplacement réalistes.

2. **Modèle positionnel continu** — chaque joueur maintient une CourtPosition courante mise à jour en continu. Après avoir frappé un Coup, le joueur commence à se déplacer vers sa position de base (Replacement). La CourtPosition effective au moment du prochain RequiredTime est la position atteinte pendant le temps de replacement disponible.

Le modèle sans mémoire a été rejeté parce qu'il rend impossible la modélisation de la construction du point : un joueur n'est jamais au centre du court après avoir couru sur une balle excentrée, et sa position au moment du Coup suivant dépend directement de l'échange précédent.

## Decision

Le simulateur maintient une **CourtPosition courante par joueur**, persistante entre les Coups. Après chaque frappe :

1. Le joueur commence à se déplacer vers sa CourtPosition de base (Replacement).
2. La vitesse de Replacement est calculée depuis les Attributs Physique : Vitesse latérale (composante x), Vitesse avant-arrière (composante y), Agilité (accélération et changement de direction).
3. La CourtPosition de base visée n'est pas un point fixe — elle est déterminée par l'Attribut Intelligence de jeu Placement, qui encode la capacité du joueur à identifier la position optimale selon la situation courante.
4. Quand la balle adverse est prête à être calculée, la CourtPosition de départ pour le RequiredTime est la position réelle du joueur à cet instant (position atteinte pendant le Replacement disponible, pas la position de base idéale).

## Consequences

- Le simulateur doit maintenir un état `currentPosition: CourtPosition` par joueur, mis à jour après chaque Coup.
- RequiredTime n'est plus calculé depuis une position fixe mais depuis la position courante réelle.
- Les Attributs Placement (Intelligence de jeu) et Vitesse/Agilité (Physique) ont des hooks simulateur distincts et complémentaires.
- La construction du point devient émergente : une balle profonde et excentrée force l'adversaire dans une mauvaise position de Replacement, ce qui dégrade son RequiredTime et son HitQuality sur le Coup suivant.
- Les statistiques de déplacement total (distance parcourue par point et par match) sont produites naturellement par le modèle sans calcul supplémentaire.
- Un modèle sans mémoire serait un retour en arrière incompatible avec le présent ADR.
