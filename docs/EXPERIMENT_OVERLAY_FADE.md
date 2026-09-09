# Experiment: fading overlays on zoom change

**Branch:** `exp/overlay-fade` · **Status:** text overlays done, icons not started

## Goal

POI icons and labels currently pop in and out the instant a zoom threshold is
crossed. We want them to fade over ~200-300ms instead, so zooming feels
continuous rather than stepped.

Scope is deliberately limited to **overlays** (icons and text). Areas and lines
are a different rendering path and are out of scope here.

## What already exists

Findings from reading the renderer, so we don't rebuild what's there:

| Piece | State |
|---|---|
| `u_opacity` in text/icon shaders | **Present and already applied.** `text.fsh.glsl` multiplies its computed alpha by it, `texturing.fsh.glsl` does `finalColor.a *= u_opacity`. |
| `OpacityAnimation` class | Exists, `libs/drape_frontend/animation/opacity_animation.hpp`. |
| `RenderGroup::m_animation` | Exists, but only ever constructed for the bookmark drop animation (`gpu::Program::BookmarkAnim`, 0.25s). Nothing else uses it. |
| Per-overlay dynamic vertex data | `OverlayHandle::GetAttributeMutation()` / `AddDynamicAttribute()` — already used to push per-frame positions. |
| Overlay visibility | **Hard boolean.** `OverlayHandle::m_isVisible : 1`, flipped by `OverlayTree` in `StartOverlayPlacing` / `EndOverlayPlacing`. This is what makes things pop. |

So the shader end is done. The gap is that visibility is binary and there is no
per-overlay alpha to drive.

## Phase 1 checkpoint: PASSED

Temporarily forced `m_opacity = 0.5f` for the overlay programs only. Icons and
labels rendered half-transparent while roads, buildings and land stayed opaque.
`u_opacity` reaches the overlay shaders, so the fragment-shader end needs no
work. Smoke test has been removed.

## Correction: text and icons hide by different mechanisms

The original plan assumed both overlay kinds could reuse the per-frame
mutation path. That is only true for text.

`RenderBucket::Render()` applies two independent mechanisms:

```cpp
if (handle->IndexesRequired()) {
  if (handle->IsVisible())
    handle->GetElementIndexes(rfpIndex);   // icons: hidden by dropping indices
  hasIndexMutation = true;
}
if (handle->HasDynamicAttributes())
  handle->GetAttributeMutation(rfpAttrib); // text: hidden by zeroing vertices
```

- **Text** uses `TextHandle`, which owns a dynamic stream
  (`gpu::TextDynamicVertex` = position + normal). Hiding memsets it to zero,
  collapsing the quad. Adding an alpha here is cheap: extend the struct, the
  binding, and the vertex shader.
- **Icons** use plain `dp::SquareHandle` with `gpu::SolidTexturingVertex` /
  `MaskedTexturingVertex`, which are **static** streams. There is no dynamic
  attribute and no `GetAttributeMutation` override — the base one is a no-op.
  Icons disappear because their indices are omitted from the index buffer.

So fading icons needs a new dynamic alpha stream on the symbol path plus keeping
their indices alive during the fade. That is a bigger change than fading text,
and it is the part the user notices most.

**Revised sequencing:** do text first (Phase 2a). It exercises the alpha
plumbing and, more importantly, the overlay-tree lifetime problem in Phase 3,
which is the real risk. If lifetime handling turns out to be unworkable, we
learn it before paying for the icon vertex-format work.

## The key constraint

`m_opacity` is a **per-draw-call uniform**, set once per render group
(`render_group.cpp:28` hardcodes `1.0f`). A render group covers many overlays in
a tile, so the uniform alone cannot fade one icon while its neighbour stays put.

That leaves two approaches.

### Option A — per-overlay alpha via dynamic vertex attribute

Give each `OverlayHandle` a current and target alpha, and push the current value
into a vertex attribute each frame through the existing mutation path.

- Genuinely per-overlay; only the icons actually appearing or disappearing fade.
- Reuses machinery that already runs every frame for positions.
- Costs a float per vertex and touches the vertex layout for text and symbols.

### Option B — fade the whole render group

Drive the existing `m_opacity` uniform per group when the zoom level changes.

- Much smaller change, no vertex format work.
- Fades *everything* in the group, including overlays that were already on
  screen and should stay put. Likely reads as the whole map flickering.

**Recommendation: Option A.** Option B is a day's work but probably looks wrong,
which defeats the point of the experiment.

## Plan

**Phase 1 — make alpha exist and reach the shader**
1. Add `m_alpha` / `m_targetAlpha` to `OverlayHandle`, defaulting to 1.0 so
   current behaviour is unchanged.
2. Add an alpha vertex attribute to the text and symbol bindings, fed from
   `GetAttributeMutation()`.
3. Multiply it into the existing `u_opacity` result in the two fragment shaders.
4. Checkpoint: hardcode alpha 0.5 for all overlays and confirm everything
   renders half-transparent. If this doesn't work, nothing later will.

**Phase 2 — drive the alpha**
5. In `OverlayTree::EndOverlayPlacing`, set `m_targetAlpha` to 1 for handles that
   won a slot and 0 for those that didn't, instead of flipping `m_isVisible`.
6. Step `m_alpha` toward the target each frame using `OpacityAnimation`
   (~250ms, matching the bookmark animation's feel).
7. Keep drawing a handle while `m_alpha > 0`, even when it has no slot.

**Phase 3 — the hard part: lifetime**
8. `OverlayTree` currently drops handles as soon as they lose their slot. Fading
   out requires keeping them alive for the duration of the fade. Add a short
   grace period before removal.
9. Verify this doesn't leak handles when zooming fast through several levels.

**Phase 4 — tune and measure**
10. Duration and easing; probably faster out than in.
11. Profile at low zoom in a dense city, where overlay counts are highest.

## Risks

- **Overlay tree churn** is the real risk. The tree culls aggressively for
  performance, and holding onto fading handles works against that. Phase 3 is
  where this experiment most likely dies.
- **Overdraw** while both the outgoing and incoming icon are partly visible.
- **Vertex format changes** touch shared buffer layouts; a mistake here shows up
  as corrupted geometry rather than a clean failure.
- **Upstream divergence.** This touches core drape files that CoMaps changes
  regularly, so future merges will conflict.

## Verification

- Desktop Designer for iteration; it renders the same drape pipeline.
- Compare frame timings before and after in a dense area — the Designer has a
  "Get statistics" button.
- Check both light and dark themes, and 3D building mode, since all share the
  overlay path.

## Bail-out criteria

If Phase 1's half-transparency checkpoint doesn't work cleanly, or Phase 3 shows
measurable frame-time regression in a dense city, stop and revert. The map
popping is a cosmetic annoyance; a stuttering renderer is not worth trading for
it.


## Result

Text overlays fade in and out, and the blinking is much reduced. Confirmed by
eye in the Designer app.

Two things the plan did not anticipate, both found by testing rather than
reading:

**The fade froze when the map stopped moving.** `StepFade()` runs from
`GetAttributeMutation()`, which only happens when a frame is drawn, and drape
skips frames when nothing changes. A fade started by a zoom would stall
partway. Fixed by flagging an in-flight fade and folding it into
`isActiveFrame` in `FrontendRenderer`, next to the existing animation checks.

**Fading was not the whole problem.** The visible annoyance was labels
blinking and fighting for space, which is overlay-tree churn, not transition
sharpness. The tree re-decided winners from scratch every placement pass with
no hysteresis. Adding "prefer the incumbent at equal priority" addressed the
cause; the fade addresses the symptom. Both were needed.

Phase 3 (lifetime) turned out to be a two-line change in `RenderBucket` -
keep a fading overlay's indices - rather than the blocker the plan expected.

### Debugging note

"Feels the same" was reported twice before the alpha path was verified. Two
diagnostics settled it quickly and should have come first: pin alpha to a
constant to prove the plumbing, then set an absurdly slow fade to prove the
logic. Guessing at tuning while the mechanism was unverified wasted a cycle.

### Not done

- **Icons still pop.** They are plain `SquareHandle`s with static vertices and
  need their own dynamic alpha stream, as described above.
- **GL only.** `libs/shaders/Metal/map.metal` needs the same vertex change
  before iOS renders this correctly.
- **Battery.** Fades keep the render loop awake, so a longer fade means more
  awake frames. Worth measuring on device before shipping the current ~3.3s.
