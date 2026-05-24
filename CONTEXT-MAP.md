# Context Map

This is a multi-context monorepo. Each subfolder has its own bounded context.

| Subfolder | Context | CONTEXT.md |
|---|---|---|
| `NextManagerTennis_WebApp/` | Online game — domain vocabulary, game rules, User-facing concepts | [CONTEXT.md](NextManagerTennis_WebApp/CONTEXT.md) |
| `NextManagerTennis_Simulator/` | Simulation engine — match physics, progression, event model | [CONTEXT.md](NextManagerTennis_Simulator/CONTEXT.md) |
| `NewtManagerTennis_DiscordBot/` | Discord bot — future external client of the Spring Boot API | *(not yet created)* |

System-wide architectural decisions live in `docs/adr/`.
