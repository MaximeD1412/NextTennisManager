# ADR-0017 — Modèle de fatigue intra-match

## Status
Accepted

## Context

The simulator already receives the inter-match Fatigue as a fixed 0.0–1.0 coefficient at match start (part of the simulator contract, ADR-0004). Two Attribut hooks already existed in the Simulator context with no backing mechanic:

- `Endurance` — "coefficient de résistance à l'accumulation de fatigue intra-match"
- `Récupération inter-points` — "taux de dissipation de la fatigue intra-match entre les points"

The existing `RequiredTime` and `HitQuality` formulas reference a Fatigue coefficient (0.0–1.0) that was implicitly the static inter-match snapshot. A point-by-point simulator demands a fatigue model that evolves *within* the match — a player who has played 40 hard points is physically more degraded than at point 1, independently of their pre-match condition.

Four design questions needed resolution simultaneously:

1. At what granularity does intra-match fatigue accumulate?
2. Is it a separate state from inter-match Fatigue, or a derived coefficient?
3. How does it dissipate between points?
4. Does it feed back into the persistent inter-match Fatigue at match end?

## Decision

### Two independent states

The simulator maintains two independent Fatigue states during a match:

- **Inter-match Fatigue** (snapshot) — the 0.0–1.0 coefficient received from the WebApp at match start. Fixed for the entire match duration; never mutated by the simulator. The WebApp is the sole owner of the persistent Fatigue state.
- **Fatigue Intra-Match accumulator** — an internal simulator state, initialised at 0.0 at match start, that evolves point by point. Never persisted; discarded after the match result is emitted.

A single-state model (letting the simulator mutate the inter-match value directly) was rejected because it blurs ownership: the WebApp must apply post-match fatigue effects consistently regardless of whether a match was followed in Live or ran in Batch.

### Effective Fatigue coefficient

The Fatigue coefficient consumed by `RequiredTime` and `HitQuality` is no longer the static inter-match snapshot. It is computed as:

```
effective_fatigue = inter_match + intra_match × (1 − inter_match)
```

This is an additive-with-saturation ("screen blending") formula. The intra-match accumulator fills the remaining headroom above the inter-match level. A player entering at 80% inter-match Fatigue has only 20% headroom before reaching saturation; a fresh player has the full range available. The result is bounded in [0, 1] by construction.

Simple addition (`min(inter_match + intra_match, 1.0)`) was rejected: a player already at 70% plus a mid-match accumulator of 50% would immediately saturate at match start, making intra-match dynamics irrelevant for fatigued players.

Max (`max(inter_match, intra_match)`) was rejected: it ignores the additive nature of the two states — a player fatigued both from prior matches and from the current match is not equivalently fatigued to one who only has one source.

### Accumulation: per Coup, driven by ReachResult and Intention de Coup

The intra-match accumulator grows on every Coup, modulated by Endurance:

```
increment = ReachResult_weight × Intent_weight / Endurance_resistance_coefficient
```

**ReachResult weights (tunable via ConfigurationGlobale):**

| ReachResult | Weight |
|---|---|
| `COMFORTABLE` | `base_effort` |
| `LATE` | `late_effort` (`base_effort < late_effort < stretched_effort`) |
| `STRETCHED` | `stretched_effort` |
| `DESPERATE` | `desperate_effort` (`> stretched_effort`) |
| `MISSED` | `min(AvailableTime / RequiredTime, 1.0) × desperate_effort` |

MISSED uses a continuous weight proportional to the effort actually expended: a near-miss (`AvailableTime ≈ RequiredTime`) costs as much as DESPERATE; a clear miss (perfectly unreachable ball, `AvailableTime ≪ RequiredTime`) costs near zero. The ratio is already computed during ReachResult evaluation — no additional data is required.

The MISSED reference was updated from `stretched_effort` to `desperate_effort` when ReachResult was expanded from 3 to 5 values: a ball that nearly falls into MISSED territory involves the same all-out physical effort as DESPERATE — referencing `stretched_effort` would underestimate the toll.

A fixed cost per Coup was rejected: it would make a 30-stroke rally identically taxing to an ace, removing any physical meaning from rally length.

Accumulation per Point (rather than per Coup) was rejected for the same reason: within a long rally, later shots are physically harder than earlier ones — a per-Coup model captures this; per-Point does not.

**Intention de Coup weights:** AGGRESSIVE > NEUTRAL > DEFENSIVE (exact values tunable via ConfigurationGlobale). Explosive, offensive shots cost more than blocking returns.

**Endurance** applies as a resistance coefficient: higher Endurance → smaller increment per equivalent Coup. The exact normalisation formula is an implementation detail; all coefficients are externalised to ConfigurationGlobale.

### Dissipation: variable by pause type, modulated by Récupération inter-points

Between each Point, the accumulator decreases. The dissipation amount is proportional to the type of pause:

| Pause type | Duration coefficient |
|---|---|
| Inter-point | 1.0 |
| Inter-jeu / changement de côté | ~4.5 (ratio 90s / 20s) |

```
dissipation = pause_duration_coefficient × Récupération_inter_points_normalized
```

The simulator already knows the score structure (points, games, sets) — distinguishing a changement de côté from a standard inter-point requires no new data.

A fixed dissipation per Point was rejected: it would make a match with frequent changements de côté (e.g., many short games) identical in recovery to one with long games, ignoring a significant physical difference.

### Post-match feedback to inter-match Fatigue

At match end, the simulator reports a `fatigue_delta` to the WebApp:

```
fatigue_delta = mean(intra_accumulator_value_per_point) × format_scaling_coefficient
```

The mean of the intra-match accumulator sampled once per Point (at the moment the Point ends) captures both match length (more Points = higher cumulative stress) and match intensity (harder rallies = higher accumulator throughout). A short, easy match produces a low mean; a long, gruelling match produces a high mean.

The peak value was considered (captures the worst physical moment of the match) but rejected: it underrepresents the toll of sustained effort across many points — a player who peaked at 0.8 for two points and coasted is less affected than one who held 0.7 for 200 points.

The `format_scaling_coefficient` is tunable per match format (BEST_OF_3 vs BEST_OF_5) via ConfigurationGlobale. The WebApp applies the reported delta to the TennisPlayer's persistent Fatigue state.

## Consequences

- The `effective_fatigue` coefficient used by `RequiredTime` and `HitQuality` is now dynamic — it changes per Coup throughout the match, not a fixed snapshot per match.
- The Simulator CONTEXT.md is updated to define **Fatigue Intra-Match** as a named concept and to clarify the `effective_fatigue` combination formula.
- The simulator must emit a `fatigue_delta` field in its match result payload. The WebApp must consume this field and apply it to the persistent Fatigue state post-match.
- All numeric coefficients (ReachResult weights, Intent weights, Endurance normalisation, pause duration ratios, format scaling) are externalised to ConfigurationGlobale and overridable in MondeSetting. Calibration against expected physical distributions (rally length, fatigue curves over a 5-set match) happens through the existing Monte Carlo pipeline (ADR-0011).
- The inter-match Fatigue snapshot received at match start remains fixed — the simulator never writes back to it mid-match. This preserves the Batch/Live symmetry: both modes produce the same `fatigue_delta` for the same match outcome.
