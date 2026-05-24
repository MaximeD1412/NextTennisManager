# ADR-0013 — Internationalisation strategy: next-intl, hybrid ownership, AI-assisted translation

## Status
Accepted

## Context

The game targets French-speaking and English-speaking players at launch, with additional languages possible post-launch via community contributions. The system spans two layers — a Next.js (TypeScript) frontend and a Spring Boot backend — each of which surfaces text to users in different ways:

- **Frontend UI** — labels, tooltips, form validation messages, game status strings.
- **Synchronous API responses** — error codes and metadata returned from the backend to the browser.
- **Asynchronous notifications** — emails and WebSocket push events sent when the User may not be online (e.g. "Your Sponsor has renewed", "A TennisPlayer entered your final contract season"). These must be localised server-side because the browser is not involved.

Two alternative ownership models were considered:
1. **Frontend-only** — the backend returns codes and raw data; the frontend translates everything. Clean, but breaks down for offline notifications (emails), which would have to be sent in a fixed language regardless of the User's preference.
2. **Backend-only** — the backend resolves all strings via `Accept-Language` headers. Simple for emails, but duplicates the translation catalogue between frontend and backend and couples API responses to a language.

## Decision

**Hybrid ownership:**
- All UI labels, game-state strings, and error codes in API responses are translated **by the frontend** (next-intl). The backend never returns translated prose in API response bodies — it returns structured codes and parameters (e.g. `{ "error": "TENNISPLAYER_NOT_ELIGIBLE", "params": { ... } }`).
- Asynchronous notifications (emails, offline WebSocket push events) are translated **by the backend** using Spring's standard `MessageSource` with `ResourceBundle` `.properties` files, keyed on `User.preferredLanguage` read from the database.
- `User.preferredLanguage` (ISO 639-1 code: `fr`, `en`, etc.) is a first-class field on the User entity, persisted in PostgreSQL. It is the only source of truth for the backend localisation path.

**Frontend library — next-intl:**
- Chosen over react-i18next (requires manual App Router/RSC wiring) and Lingui (compile-time extraction adds build complexity at this stage).
- next-intl is designed for Next.js App Router; React Server Components are supported out of the box; translation keys are TypeScript-typed, preventing silent missing-key bugs.
- Translation files are JSON, namespaced by domain area, located in the frontend repository.

**Languages at launch:** French (primary, authored first) and English.

**Vocabulary policy:** all game-specific French terms are translated to English using the equivalents documented in the WebApp CONTEXT.md glossary (e.g. Créneau → Time Slot, Saison → Season, Rythme → Match Rhythm). No French terms are retained as brand terms in the English UI — the English experience must be self-contained for non-French speakers.

**Translation workflow:**
- French strings are authored by the developer (native language).
- English translations are produced by an LLM (Claude or equivalent) with the WebApp CONTEXT.md provided as context, ensuring domain-vocabulary consistency.
- Additional languages post-launch are contributed by the community via the same JSON file structure (compatible with Crowdin or direct PR contribution).

## Consequences

- The backend must never return user-visible prose in API response bodies. Any error or status that surfaces in the UI must be expressed as a machine-readable code with optional structured parameters; translation is the frontend's responsibility.
- `User.preferredLanguage` must be set at registration and exposed as a user setting. The default value is inferred from the `Accept-Language` header at registration if possible, falling back to `fr`.
- The Spring Boot layer requires a `MessageSource` configuration and `.properties` files for email and notification templates in each supported language. These are maintained in parallel with the frontend JSON files — they are separate catalogues (the backend catalogue covers only notification prose; it is a strict subset of the frontend catalogue).
- Community contributions for new languages touch only JSON files (frontend) and `.properties` files (backend). No code changes are required to add a new language.
- When the LLM translates new frontend strings, it must be given the current WebApp CONTEXT.md to ensure that domain terms are translated consistently with the established glossary.
