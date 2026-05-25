# ADR-0021 — Humidité de Surface : modèle d'effets détaillé

## Status
Accepted

## Context

ADR-0020 a acté que `surface_wetness` est un état dynamique du terrain transmis au Simulateur via deux champs du Contrat (`surface_wetness_initial: float`, `precipitation_active: bool`). Mais l'ADR laissait ouverts les effets exacts par surface, la question des Effets (Lift/Slice/Amortie), et le comportement du terrain sur le mouvement des joueurs. Plusieurs décisions de modélisation ont été prises en session.

## Décisions

### Effets de `surface_wetness` par surface — distinctifs, pas uniformes

`surface_wetness` produit des effets qualitativement différents selon la surface. Un coefficient universel a été rejeté : wet CLAY et wet GRASS ont des physiques opposées (wet CLAY ralentit la balle, wet GRASS la fait skider plus bas et plus vite). Les courbes sont configurables séparément pour `CLAY`, `GRASS` et `HARD` dans ConfigurationGlobale.

Deux paramètres du Rebond sont modulés : hauteur du rebond et vitesse horizontale post-rebond.

### Interaction explicite avec Lift, Slice, Amortie

`surface_wetness` module l'efficacité des Effets qui agissent sur le Rebond (`Lift`, `Slice`, `Amortie coup droit`, `Amortie revers`) via des coefficients d'interaction configurables par surface dans ConfigurationGlobale. Le cascade naturel (wetness modifie le Rebond de base → Effets s'appliquent par-dessus) a été rejeté : il ne capture pas les cas où wet GRASS amplifie Slice au-delà de la simple somme des effets, ni où une Amortie sur CLAY humide se comporte différemment. La simulation vise le réalisme, pas la simplicité.

### `surface_wetness` affecte aussi RequiredTime et Replacement

`surface_wetness` ne touche pas que la balle — il modifie aussi la mobilité des joueurs, avec des courbes distinctes par surface :
- `CLAY` humide : légère réduction du RequiredTime (amplification de la mécanique Glissade)
- `GRASS` et `HARD` humides : augmentation du RequiredTime (appuis instables)

Le même modificateur s'applique à la vitesse de Replacement — c'est la même physique.

### Glissement accidentel — mécanique dédiée

Sur les trois surfaces extérieures humides, un changement de direction pendant le Replacement peut provoquer un glissement involontaire. Ce n'est pas la même chose que la `Glissade` CLAY (intentionnelle). Le glissement se traduit par :

1. `slipped: bool` sur l'événement `PLAYER_MOVE` existant — visible dans le stream Live, ignoré en Batch comme le reste de `PLAYER_MOVE`.
2. CourtPosition sévèrement dégradée pour le **Coup suivant dans l'Échange en cours** — pas le Point suivant.
3. Déclencheur de Blessure additionnel, indépendant de la Fatigue, configurable par surface dans ConfigurationGlobale.

Probabilité de glissement par surface : CLAY très faible, GRASS et HARD plus élevées. Réduite par `Équilibre` sur toutes les surfaces ; réduite additionnellement par `Glissade` sur `CLAY` (maîtrise du sliding = meilleurs appuis sur CLAY humide). L'Attribut `Équilibre` acquiert deux rôles : prévention (réduction de la probabilité de glissement) et mitigation (HitQuality quand le joueur frappe en position dégradée après un glissement).

### Protection du terrain — propriété statique du Tournoi

`Protection du terrain` est une propriété statique du Tournoi (pas de la Ville — deux Tournois dans la même Ville peuvent avoir des infrastructures différentes). Stockée comme un float (0.0–1.0) dans ConfigurationGlobale, non upgradeable en cours de jeu. Affichée au User comme un label qualitatif dans la vue détaillée du Tournoi uniquement.

Elle agit sur deux paramètres transmis au Simulateur :
1. Plafonnement de `surface_wetness_initial` — le court démarre moins humide même après une nuit de pluie forte.
2. Multiplicateur sur le taux de séchage (`surface_drying_rate`) — un court bien drainé sèche plus vite entre les Points.

Le taux de séchage est calculé par le WebApp (base ConfigurationGlobale × coefficient de protection) et transmis au Simulateur comme `surface_drying_rate: float` — nouveau champ du Contrat du Simulateur. Le Simulateur reçoit un paramètre physique pur, pas le concept WebApp de Protection du terrain.

Scoper la protection à la Ville a été rejeté : Roland Garros et un Challenger dans la même ville n'ont pas la même infrastructure. Un effet uniquement sur `surface_wetness_initial` (sans `surface_drying_rate`) a été rejeté : un terrain bien drainé sèche aussi plus vite en cours de match, ce qui est stratégiquement distinct et observable par le User.

### `precipitation_active` — aucun effet direct sur HitQuality

Un malus HitQuality direct quand `precipitation_active = true` (grip mouillé, balle plus lourde) a été évalué et rejeté pour le MVP : l'effet serait trop minime pour mériter un sous-système dédié. `precipitation_active` conserve son unique rôle : stabiliser `surface_wetness` entre les Points (empêche le séchage).

## Consequences

- Le Contrat du Simulateur (ADR-0004, ADR-0020) est étendu d'un champ : `surface_drying_rate: float` — à ajouter au fichier `.proto` (ADR-0019).
- L'événement `PLAYER_MOVE` (ADR-0018) est étendu d'un champ : `slipped: bool` — à ajouter au `.proto`.
- `Équilibre` a désormais deux rôles dans le Simulator : prévention du glissement et mitigation de HitQuality en position dégradée.
- `Glissade` a désormais deux rôles sur `CLAY` : réduction du RequiredTime et réduction de la probabilité de glissement accidentel.
- La Blessure (WebApp CONTEXT.md) a désormais deux déclencheurs indépendants : Fatigue élevée et glissement accidentel.
- Les courbes d'effets par surface (Rebond, Lift/Slice/Amortie, RequiredTime/Replacement, probabilité de glissement) sont toutes data-driven dans ConfigurationGlobale — aucun changement de code pour les tuner.
