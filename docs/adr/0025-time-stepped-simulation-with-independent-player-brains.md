# Time-stepped simulation with independent PlayerBrain per player

Le modèle séquentiel actuel (un attaquant frappe, on swap, l'autre frappe) ne permet pas à un joueur de prendre des décisions pendant le vol de la balle — monter au filet après un bon coup, adapter sa position en lisant le déplacement adverse, ou anticiper la zone cible probable avant que la balle n'atterrisse. On introduit un modèle à ticks discrets avec un `PlayerBrain` indépendant par joueur.

## Décision

Chaque joueur possède son propre `PlayerBrain` (interface Java pluggable), appelé à chaque `SimulationTick`. Le simulateur avance par pas de temps discrets : à chaque tick, il interpole la position de la balle depuis la Trajectoire Rust pré-calculée, met à jour les positions joueurs, et appelle chaque brain indépendamment avec un `GameTickState` + `ObservableOpponentState`. Chaque brain maintient une `TacticalState` continue ; au tick de contact, le simulateur lit cette `TacticalState` pour construire le `ShotSpec`.

Le Rust sidecar reste batch : la Trajectoire est calculée en un seul appel par Coup, puis interpolée tick par tick côté Java — aucun appel Rust supplémentaire par tick.

## Alternatives écartées

**Événements discrets enrichis** — ajouter des points de décision entre les événements existants (post-frappe, pré-contact). Insuffisant : ne modélise pas la décision continue pendant le vol, et la montée au filet ne peut pas être déclenchée par l'évaluation de la qualité de sa propre balle en vol.

**Décision séquentielle par Coup** — chaque joueur décide au moment de son tour de frappe, en prenant en compte la position statique de l'adversaire au moment du Coup précédent. Élimine toute adaptation pendant le vol et toute réaction au déplacement adverse.

## Conséquences

La simulation d'un Point passe d'une boucle de N coups à une boucle de N×K ticks (K ticks par Coup en vol). Coût de calcul plus élevé par Point, compensé par l'absence d'appel Rust supplémentaire par tick.

Le déterminisme est maintenu : les brains reçoivent le même snapshot par tick, appelés dans un ordre fixe par playerId. Les seeds aléatoires sont gérés par brain.

L'interface `PlayerBrain` permet de substituer une implémentation ML (`NeuralBrain`) sans modifier le simulateur — condition nécessaire pour entraîner des IA de joueur indépendamment.
