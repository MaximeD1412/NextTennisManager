# ADR-0012 — Sprite compositing over runtime AI generation for TennisPlayer Portraits

## Status
Accepted

## Context

Every TennisPlayer in the game needs a visual Portrait (bust illustration, head-to-torso). The Pool contains ~1 000 TennisPlayers per circuit per Ligue, plus unlimited NPC TennisPlayers across Mode Solo Mondes. Three design pressures shaped this decision:

1. **Style consistency** — thousands of portraits must look like they belong to the same illustrated visual universe. Runtime AI generation (DALL-E 3, Flux via API) cannot guarantee this: each API call is independent, and style drift across a large corpus is unavoidable without heavy prompt engineering.

2. **Dynamic sponsor equipment** — a TennisPlayer's Sponsor changes each Saison. If the jersey is embedded in a single AI-generated image, the portrait must be regenerated on every Sponsor change, with no guarantee the same face and morphology are preserved.

3. **MyPlayer immersion** — in the MyPlayer sub-mode, the User creates a single TennisPlayer they will follow for an entire career. A random portrait from a generic pool is insufficient; user-driven composition is required. The same composition mechanism must cover both automatic NPC generation and user-driven MyPlayer creation, avoiding two parallel systems.

Alternatives considered:
- **Per-player runtime AI generation** (text-to-image API call at TennisPlayer creation): inconsistent style across the corpus, expensive at scale, face cannot be preserved across Sponsor changes, no path to user customization.
- **Full-body portrait** (head to foot): 2–3× the composition complexity (additional layers for shorts, shoes, full body pose); 90% of the visual identity of a player card resides in the face and torso. Deferred to v2.
- **Visual aging over the career** (portrait updates as the TennisPlayer ages): requires 4–5 face variants per character; multiplies the Catalogue de Sprites by that factor. Deferred to post-MVP.

## Decision

TennisPlayer Portraits are produced by a **server-side sprite compositing pipeline**, not by runtime AI generation.

**Asset production (one-time):** The Catalogue de Sprites is built once during production using Midjourney for style reference and art direction, then a Stable Diffusion LoRA fine-tuned on those references for automated batch generation. This ensures uniform style across the entire catalogue without per-character API calls.

**Composition (at Personne creation):** The backend composes Portrait layers (body silhouette → face → hair → optional facial hair) into a single PNG, stores it in object storage (Cloudflare R2), and serves it via CDN for all subsequent requests. Sponsor branding (jersey colours, logo) is displayed as a UI overlay on the player card, not embedded in the Portrait image — this decouples the static Portrait from dynamic Sponsor state.

**Format:** Bust (head, shoulders, torso). Full-body format deferred to v2.

**MyPlayer:** The Créateur de Personnage UI allows the User to select components from the same Catalogue de Sprites at session creation. The final Portrait is composed and stored by the same server-side pipeline.

**Mods:** Portrait customisation for Mod-defined TennisPlayers is expressed as component IDs referencing the Catalogue de Sprites, included in the Mod JSON. No binary image assets are embedded in Mod files.

## Consequences

- The Catalogue de Sprites is a one-time production asset effort (estimated 200–300 sprite components covering sexe × skin tone × face shape × hair style × hair colour × facial hair). This must be completed before any portrait-related feature can ship.
- Portrait images are static assets for the lifetime of a TennisPlayer. Regeneration is never required except as an explicit user action in the Créateur de Personnage (MyPlayer only).
- The compositing pipeline is a server-side Java component (Java ImageIO or equivalent) invoked at Personne creation. It must be synchronous from the caller's perspective — portrait generation is part of the Personne creation flow, not a background job.
- Sponsor branding must never be embedded in Portrait images; any feature that assumes brand-visible portraits must use the UI overlay mechanism instead.
- Visual aging (portrait changes across career stages) is out of scope until the Catalogue de Sprites aging variants are produced. Until then, the Portrait reflects the TennisPlayer's appearance at creation.
