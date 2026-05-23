# ADR-0010 — Partial Mod override for Solo Monde initialisation

## Status
Accepted

## Context
Mode Solo supports a Mod system: a user-importable data file that initialises a Solo Monde with custom TennisPlayers, Tournois, NPC Clubs, and MondeSetting parameters. The primary use case is community-authored content (e.g. a real ATP tour pack with fictional player names).

The core design question: does a Mod have to define everything, or can it define only a subset of sections?

**Total override** — a Mod must define all sections (TennisPlayers, Tournois, Clubs NPC, MondeSetting). Absent sections are an error. Simple to implement; the Monde state after import is fully predictable.

**Partial override** — each Mod section is optional. Absent sections fall back to ConfigurationGlobale defaults. A Mod can define only TennisPlayers without touching the Tournoi calendar.

## Decision
**Partial override.** Each section of a Mod is independently optional.

The primary driver is authoring cost. A community author who wants to add real-world player analogues should not be required to also reproduce the full Tournoi calendar, which is already well-covered by the default ConfigurationGlobale. Forcing a total override would make Mod authoring prohibitively heavy for single-concern packs and would couple unrelated concerns in a single file.

Partial override also enables composition: a User can apply a player-focused Mod on top of the default calendar, or a calendar-focused Mod without replacing NPC Clubs. The template export (a blank-filled schema of all available sections) is the primary authoring aid.

## Consequences
- The Mod deserialiser must distinguish between "section absent" (use default) and "section present but empty" (override with nothing). This is a non-trivial contract in the serialisation format and must be explicit in the template schema.
- ConfigurationGlobale remains the authoritative fallback for all absent sections — a Mod cannot partially define a section (e.g. define 50 TennisPlayers and expect the system to generate the remaining 950). A present section fully replaces its counterpart from ConfigurationGlobale.
- The developer is not the publisher of community Mod content. Community-created Mods (e.g. real player names or likenesses) are authored and distributed by Users. The system provides tooling; responsibility for content rests with the Mod author.
