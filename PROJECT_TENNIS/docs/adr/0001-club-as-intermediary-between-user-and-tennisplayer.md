# ADR-0001 — Club as intermediary between User and TennisPlayer

## Status
Accepted

## Context
A User needs to manage TennisPlayers. The simplest model would be a direct link: `User → TennisPlayer`. However, two requirements make this insufficient:

1. **Multi-sport extensibility** — the system may support other sports in the future. A User should be able to hold a Club in tennis and a Club in another sport without mixing domains.
2. **Solo mode without special-casing** — solo career modes need a neutral container for a TennisPlayer. Rather than adding a nullable `userId` on TennisPlayer, a fictitious Club with an infinite Contract handles this uniformly.

## Decision
Introduce **Club** as an explicit entity between User and TennisPlayer. A User owns one or more Clubs (one per Monde). A Club signs Contracts with TennisPlayers. A TennisPlayer can be libre (no active Contract) or under Contract with exactly one Club at a time.

```
User → owns Club(s)
Club → holds Contract(s) with TennisPlayer(s)
TennisPlayer → libre | under Contract
```

## Consequences
- The schema has one more join when querying "which TennisPlayers does this User manage" — acceptable.
- Solo modes require creating a fictitious Club automatically; this is simple and keeps the rest of the system uniform.
- Transfers and market mechanics operate on Contracts, not on Users directly — cleaner for multiplayer.
- If a second sport is added, Clubs can be typed by sport without touching the User model.
