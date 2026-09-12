# TODO

1. **Placeholder art.** Every texture this mod ships is a magenta/cyan checker placeholder sized to
   the right canvas, meant to be painted over:
   - `textures/item/talaria.png`, `textures/item/wax_wings.png` — 16x16 inventory icons.
   - `textures/entity/equipment/humanoid/talaria.png` — 64x32, the worn boots.
   - `textures/entity/equipment/humanoid_baby/talaria.png` — **64x64**, a different canvas, not a
     scaled copy of the adult one. Vanilla routes every armor slot on a baby humanoid through the
     `humanoid_baby` layer, so a set without it renders babies untextured.
   - `textures/entity/equipment/wings/wax_wings.png` — 64x32, elytra layout.

2. **Shadow is unwired.** The enchantment, its datagen and the three client mixins are all still in
   the tree but disconnected (`Main` does not call `Shadow.init()`, `ArtifactsDataGenerator` does
   not emit the enchantment json, `mixins.json` lists no client mixins, and `fabric.mod.json` has no
   client entrypoint). Its whole payoff is client-side rendering, and the server it deploys to is
   played on vanilla clients. Rewiring it needs either a server-side approach or an accepted
   client-mod requirement.

3. **In-world verification.** Build, datagen, lint and dedicated-server boot are all green, but
   flight, the speed bonus, the equipped-armor textures and the Icarus death message can only be
   confirmed from a connected client.
