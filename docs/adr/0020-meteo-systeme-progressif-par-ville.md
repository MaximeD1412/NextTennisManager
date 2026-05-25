# ADR-0020 — Météo comme système progressif par Ville

## Status
Accepted

## Context

Le concept de Vent était déjà dans le Contrat du Simulateur (ADR-0004, Simulator CONTEXT.md) mais sa source — la Météo — était marquée "à définir". Trois questions restaient ouvertes :

1. Quels paramètres météo existent au-delà du vent ?
2. Où vit la Météo dans le domaine — WebApp, Simulator, ou les deux ?
3. La Météo est-elle visible du User, et à quel moment ?

La Météo interagit avec plusieurs concepts déjà acté : le Contrat du Simulateur (ADR-0004), le Rebond (Simulator CONTEXT.md), la Fatigue Intra-Match (ADR-0017), et le stream d'événements (ADR-0018). Elle touche aussi le périmètre MVP (richesse de la simulation vs charge de conception).

## Decision

### Scope MVP : Vent + Précipitations uniquement

Deux paramètres météo pour le MVP : Vent (déjà dans le Contrat du Simulateur) et Précipitations (nouveau). Température et luminosité créeraient des effets secondaires sur la Fatigue et la perception des joueurs qui traversent trop de sous-systèmes — différés post-lancement. Suspension de match pour cause de pluie exclue : trop coûteuse en mécanique calendaire, nuisible à l'expérience de jeu.

### Météo appartient au contexte WebApp ; le Simulator reçoit des coefficients

La Météo en tant qu'entité (génération, stockage, affichage) vit dans le contexte WebApp. Le Simulator ne reçoit que les effets physiques distillés dans son contrat : `VENT_MOYEN (direction, intensité)`, `surface_wetness_initial: float`, et `precipitation_active: bool`. Cette séparation préserve la frontière définie dans ADR-0004 — le Simulator ne connaît pas la Météo, il consomme ses conséquences physiques.

### Ville comme entité first-class

La Météo est scoped à la **Ville**, pas au Tournoi. Une Ville porte un profil climatique par Période Climatique (ÉTÉ/AUTOMNE/HIVER/PRINTEMPS) et un overlay optionnel Saison des Pluies. Plusieurs Tournois peuvent se tenir dans la même Ville — le profil s'applique à tous.

Scoper la Météo au Tournoi directement a été rejeté : cela dupliquerait les données climatiques sur chaque édition de chaque Tournoi, et efface la notion que c'est le lieu qui détermine le climat, pas l'événement.

La Saison des Pluies est modélisée comme un overlay borné par des game-Semaines (pas comme une cinquième Période Climatique) : une saison des pluies tropicale couvre souvent plusieurs Périodes Climatiques — l'imposer dans l'enum crée des cas impossibles à modéliser.

### Quatre états progressifs de la Météo

La Météo d'un Créneau de Tournoi passe par quatre états exposés au User :

| État | Disponible | Format |
|---|---|---|
| Météo précédente | Publication du calendrier | Labels qualitatifs (édition précédente) |
| Prévision initiale | 8 Semaines avant le premier Match | Labels qualitatifs, faible précision |
| Prévision affinée | 1 Semaine avant le premier Match | Labels qualitatifs, haute précision |
| Météo effective | Démarrage du Créneau | Coefficients précis — transmis au Simulator |

Les Prévisions utilisent des labels qualitatifs (`ENSOLEILLÉ | NUAGEUX | PLUIE_LÉGÈRE | PLUIE_FORTE` pour les précipitations, `CALME | VENTEUX | TRÈS_VENTEUX` pour le vent) — pas des coefficients numériques. Stocker des floats approximatifs dans les Prévisions donnerait une fausse précision. La Météo effective est la seule à transporter des valeurs précises.

La Météo précédente (état 0) résout le cas des Tournois inscrits dès le début de Saison, avant que le calcul de forecast ait commencé. En Saison 1 d'un Monde (pas d'édition précédente), fallback sur le profil Période Climatique de la Ville.

### surface_wetness dynamique pendant le Match

`surface_wetness` n'est pas un coefficient statique. Parallèle au modèle Vent existant, il évolue entre les Points : décroît si `precipitation_active = false` (terrain qui sèche), stable si la pluie continue. Taux de séchage configurable via ConfigurationGlobale/MondeSetting.

Le modèle statique (valeur fixée au démarrage) a été rejeté : il ne reflète pas le comportement réel d'un terrain qui sèche en cours de match sous le soleil, une mécanique que le User peut observer et dont il peut tenir compte dans ses choix tactiques.

### INDOOR_HARD : Météo nulle

Les surfaces intérieures sont protégées de la pluie et du vent. Pour `INDOOR_HARD` : `VENT_MOYEN = (0, 0)`, `surface_wetness_initial = 0.0`, `precipitation_active = false`. Le Contrat du Simulateur reste structurellement identique — les champs existent mais sont nuls.

## Consequences

- `Ville` est une nouvelle entité dans le domaine WebApp avec un profil climatique (quatre Périodes Climatiques + overlay Saison des Pluies optionnel). Le Tournoi référence sa Ville.
- Le Contrat du Simulateur (ADR-0004) est étendu : `surface_wetness_initial: float` et `precipitation_active: bool` s'ajoutent aux inputs existants.
- Le Rebond dans le Simulator est désormais modulé par deux états dynamiques : surface (déjà présente) et Humidité de Surface (nouveau).
- Les données de profil climatique par défaut doivent être authoriées depuis des données météo historiques réelles par Ville — c'est une tâche de data authoring, pas un changement architectural.
- En Solo Monde, les paramètres de génération Météo sont tunable via MondeSetting. Le Sandbox sub-mode permet l'override direct de la Météo effective pour un Créneau donné.
- L'extension du Contrat du Simulateur est un changement de schéma Protobuf (ADR-0019) : `surface_wetness_initial` et `precipitation_active` doivent être ajoutés au fichier `.proto`.
