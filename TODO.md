# TODO

1. **Placeholder art.** Every texture this mod ships is a magenta/cyan checker placeholder sized to
   the right canvas, meant to be painted over:
   - `textures/item/talaria.png`, `textures/item/wax_wings.png`,
     `textures/item/orb_of_levitation.png` — 16x16 inventory icons.
   - `textures/entity/equipment/humanoid/talaria.png` — 64x32, the worn boots.
   - `textures/entity/equipment/humanoid_baby/talaria.png` — **64x64**, a different canvas, not a
     scaled copy of the adult one. Vanilla routes every armor slot on a baby humanoid through the
     `humanoid_baby` layer, so a set without it renders babies untextured.
   - `textures/entity/equipment/wings/wax_wings.png` — 64x32, elytra layout.

2. **Shadow is wired but unverified in-world.** `Main` calls `Shadow.init()`, datagen emits the
   enchantment json and `shadow_enchantable` tag, and `mixins.json`/`fabric.mod.json` register the
   three client mixins and `ClientMain`. Its payoff is still purely client-side rendering — a
   vanilla client never sees the hidden model, only a client running this mod's jar does — see
   `docs/plan/shadow-vanilla-fallback.md` for the resource-pack-only fallback that was scoped for
   held tools/weapons but not built.

3. **In-world verification.** Build, datagen, lint and dedicated-server boot are all green, but
   the orb's flight and barrier fence, the wings' glide, melt and empty-hand boost, the speed
   bonus, the equipped-armor textures, both themed death messages and Shadow's hidden-model
   rendering can only be confirmed from a connected client. The staged checklist for the orb and
   the wings is each stage's "Done when" in `docs/plan/orb-and-glider-wings.md`.
