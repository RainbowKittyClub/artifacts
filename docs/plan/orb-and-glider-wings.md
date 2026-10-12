# Orb of Levitation and the Wax Wings glider

Splits today's Wax Wings into two artifacts. The creative-style flight and every guard around it
move to a new held item, the Orb of Levitation, whose cap drops from 60 to 16 blocks above the
ground and gains a per-player barrier fence. Wax Wings then become an elytra: half its durability,
repaired with honeycomb, worn faster the higher they fly, and boosted like a firework by
right-clicking with an empty hand.

Every vanilla claim was checked against the 26.2 decompile at `libs/decompiledjava/minecraft-26.2/`
or `javap` on the Loom-cached deobf jar, and every Polymer claim against the `libs/polymer/` checkout
(`dev/26.2`); citations below are to those trees. Tuning numbers the user left open are defaults
chosen here, collected in "Reference: Tuning defaults" and flagged under "Open decisions". Nothing in
this document has been observed in-world yet.

**Stage order.** S1 comes first: it moves the flight effect off the `wax_wings` item, which S4 needs
vacated before it can turn the same item into a glider. S2 then S3 finish the orb (S3's fence reads
the ground rule and cap S2 introduces). S4 makes the wings glide; S5, S6 and S7 each build on S4 and
are independent of one another and of S2–S3. S8, added after the first pass, reworks S2's ground rule
and smooths S3's fence; S9, after the first load test, reshapes S8's scan and smoothing. S10 eases
a flyer down under a ceiling that drops a block or two ahead of them, S11 extends that to any
ledge up to the cap, and S12 drops the walls for good and adds a lift over water. The feature is
complete when S7, S9, S12, S13, S14, S15, S16, S17 and S18 are done. S13 makes the ceiling a game rule
and trims the orb's flight cutoffs to water and lava; S14 makes a wet orb an item of its own; S15
keeps one click from reaching both hands; S16 lets the orb soak for a while before it waterlogs;
S17 makes a wet orb wait out of the water and fail at random; S18 dries it only while carried or
on the ground, a whole point at a time.

## Findings that reshape the work

**Finding 1 — a vanilla client sends nothing for an empty-hand right-click into air, so the boost
needs an invisible entity in front of the player's eye.** `Minecraft.startUseItem` only reaches
`gameMode.useItem` behind `!heldItem.isEmpty()` (`Minecraft.java:1743`); a block or entity under the
crosshair would send `useItemOn`/`interact`, but mid-glide there usually is neither. The fix is a
per-player Polymer `InteractionElement` whose box contains the eye:
`ProjectileUtil.getEntityHitResult` picks an entity whose inflated box contains the ray origin at
distance zero, ahead of any block (`ProjectileUtil.java:120-125`, and `LocalPlayer.java:1274-1278`
prefers it over the block hit). The client then sends `ServerboundInteractPacket`, and Polymer routes
an interact on an id the level does not know to the element's `InteractionHandler` on the server
thread (`ServerPlayNetworkHandlerMixin.java:73-81`). Client-side, `Interaction.interact` returns a
`Success` (`Interaction.java:119-121`), so the use loop stops at the main hand.

The element cannot simply ride the player. A passenger of the camera entity is only ever picked from
inside (`ProjectileUtil.java:130-134`), and a rider sits at the pose's passenger attachment, the top
of the 0.6-tall `FALL_FLYING` box, 0.2 above its 0.4 eye (`Avatar.java:27`). So the element is moved
every tick to the eye, led by `getKnownMovement()` (the client's last reported per-tick movement,
`ServerGamePacketListenerImpl.java:2205`) times the connection latency in ticks. `Interaction` has
no client interpolation (`Entity.getInterpolation` returns null, `Entity.java:2563`), so it snaps to
each update. Its position must stay near the pick box, since the client's entity lookup only scans
sections within 2 blocks horizontally and 4 below of it (`EntitySectionStorage.java:38-43`).

**Finding 2 — the catcher swallows every other hand action while it is up.** Any use or attack
while it contains the eye lands on it. It is therefore shown only while the wings are gliding and
the main hand is empty — the exact state the boost needs — so a rocket or sword in the main hand
keeps vanilla behaviour. What remains lost in that state is listed under "Open decisions".

**Finding 3 — a Polymer disguise over `Items.ELYTRA` already carries everything the client needs to
glide.** Polymer copies `GLIDER`, `EQUIPPABLE`, `DAMAGE`, `MAX_DAMAGE` and `REPAIRABLE` onto the
client stack (`PolymerItemUtils.java:126-170`), and the client starts gliding off its own copy
(`LivingEntity.canGlideUsing`, `LivingEntity.java:3989-3995`). It also stamps a `USE_COOLDOWN` group
of the server item's id (`PolymerItemUtils.java:443-445`), so `ItemCooldowns.addCooldown` on the
wings draws a cooldown sweep on them client-side with no extra work.

**Finding 4 — vanilla glide wear never breaks an elytra; extra wear through `hurtAndBreak` would.**
`canGlideUsing` stops gliding once `nextDamageWillBreak()` (`ItemStack.java:440-442`), so vanilla's
one point per second (`LivingEntity.java:3178-3185`) halts at one durability left. Altitude and boost
wear go through `hurtAndBreak`, which destroys the stack at zero, so both clamp to leave one point:
the wings stop working rather than vanish.

**Finding 5 — water has to count as ground, or the 16-block cap walls off every ocean.** Today's
`AltitudeGuard.isGround` skips fluids (empty collision shape). At 60 blocks that rarely mattered; at
16, hovering one block over a 20-deep sea already reads as overheight, and S3's fence would build a
wall along every coastline. Water is a safe landing, so S2 counts it. Lava still is not.

## How these stages get tested

Build and lint first, per `docs/TESTING.md`: `./gradlew build` for compilation, `./gradlew
generateAssets` after deleting `src/main/generated` for any stage that touches datagen (the tree is
gitignored, so a stale tag file survives otherwise), `cd scripts/lint && ./gradlew
checkstyleArtifacts` for `src/main/java`, and `checkstyleTarget
-PtargetDir=../../rkc/mods/artifacts/src/datagen/java` for the datagen tree nothing else lints. S1
and S4 touch a mixin's call target, so `scripts/devserver.sh rkc/mods/artifacts restart` then
`mixins` must print `None.`.

Every "Done when" below ends in an in-world observation from `./gradlew runClient` joined to that
dev server, and that observation is the requirement: none of these mechanics has a check short of a
client. A stage is not finished until its "Done when" is observed, so a failure at stage N implicates
stage N and not the ones before it.

## S1 — Orb of Levitation takes over flight — **in progress**

**Build.** Move the five flight classes out of `waxwings/` into a new
`club.rainbowkitty.artifacts.orb` package with `git mv`: `AltitudeGuard`, `FallTracker`,
`FlightSpeed`, `ObstructionGuard`, and `WaxWingsHooks` renamed `OrbHooks`. Add
`orb/OrbOfLevitation.java` carrying what `WaxWings` holds today — `init()`, `isUnprotectedFall`,
`isManaged`, the effect — with a new `ITEM`: a `PolymerModelItem` over `Items.HEART_OF_THE_SEA`
(orb-shaped, no use action for the client to predict), `stacksTo(1)`, name "Orb of Levitation",
lore "Pondering this will only get you so far..." in the same gray non-italic style as Talaria's.
Its effect source is `PlayerEffectSource.held(ITEM)`: either hand.

`FallTracker`'s message becomes `death.attack.artifacts.orb_of_levitation`, "%1$s stopped pondering
the orb"; the Icarus key moves to the wings in S7. `WaxWings` shrinks to its item alone — no
effect, no `init()` — with its chest `EQUIPPABLE` kept. Rewire `Main` (call `OrbOfLevitation.init()`),
`mixin/OverheightFallDamageMixin` (`OrbOfLevitation.isUnprotectedFall`), `ArtifactsLoot` (an orb pool
on a new table list), `ArtifactsLangProvider` (orb name, orb death message) and
`ArtifactsItemTagProvider` (the orb joins no tag). Add `assets/artifacts/items/orb_of_levitation.json`,
`models/item/orb_of_levitation.json`, and a placeholder `textures/item/orb_of_levitation.png`.

**Done when.** Build, datagen, both lint passes and a `mixins` → `None.` boot succeed; then in-world,
holding the orb in either hand grants flight with every behaviour Wax Wings had (60-block cutoff
still, rain slowdown, obstruction cutoff, fall damage while not flying), switching hotbar slots away
from it mid-air drops the player with "stopped pondering the orb" on a fatal landing, and wearing Wax
Wings grants nothing.

### As built

`WaxWings` kept an empty `init()`, contrary to the Build text: `ITEM` registers from a static
initializer, and nothing else touches the class before the registry freezes — `ArtifactsLoot` reads
it only inside the loot-modify lambda, which runs after. `Main` calls it next to
`OrbOfLevitation.init()`. The orb's icon started as a copy of the wings' placeholder checker; S8
gave it its own.

Build, a from-scratch `generateAssets` (the only generated change is the two new lang keys), both
lint passes (19 files in `src/main/java`) and a dev-server boot with `mixins` → `None.` are green.
Over RCON, `give @a artifacts:orb_of_levitation` answers `No player was found` where an unknown id
answers `Unknown item`, so the item registers. The in-world leg is not yet observed.

Code review found that `OrbHooks.onDeactivate` marked any airborne player as falling, flying or
not, which a chest-slot item rarely hit but a held one does: scrolling past the orb mid-jump or
mid-glide on Wax Wings tracked the fall, and the orb's override, registered first, then took the
Icarus message. It now marks only when `flying` was true before the revoke.

## S2 — Sixteen-block cap on a shared ground rule — **in progress**

**Build.** Add `orb/Ground.java` holding `isGround` (moved from `AltitudeGuard`, now also true for any
block whose fluid is water, waterlogged blocks included) and `heightAbove(level, pos, limit)` — the
scan `AltitudeGuard.heightAboveGround` does today, parameterised by start position so S3 can measure
any cell. `AltitudeGuard.MAX_SAFE_HEIGHT` becomes 16 and package-private so S3 can read it;
`OVERHEIGHT_TRIGGER_CHECKS` drops from 10 to 3, since S3 makes this cutoff a backstop rather than the
main limit.

**Done when.** In-world, flying 17+ blocks above solid ground for three seconds cuts flight (mayfly
kept, so a double-jump catches), 16 does not, and hovering 5 blocks over a 30-deep ocean does not.

### As built

As planned. `Ground.isGround` takes the already-read `BlockState` so S3's column walk reads each
cell once. Build and lint are green; the in-world leg is not yet observed.

## S3 — Per-player barrier fence — **in progress**

**Build.** Add `orb/AltitudeFence.java`, called from `OrbHooks.onTick` after both guards (they can
end flight that tick) and cleared from `onDeactivate` and on leave. While the holder is alive,
managed and `flying`, each tick it:

1. Sweeps the player's box along `getKnownMovement()` for `LOOKAHEAD_TICKS` plus the latency in
   ticks, capped at `MAX_LOOKAHEAD_BLOCKS`, and inflates it `SCAN_MARGIN` horizontally. Every column
   under that box is scanned; the cell window is the swept box's floor to one above its top.
2. Walks each loaded column upward from `MAX_SAFE_HEIGHT + 2` below the window, remembering the last
   ground cell, so each window cell's height is measured against the ground directly beneath it —
   not the column's topmost, which would read an overhang as the floor.
3. Marks an air cell inside build height as a barrier when its height is at least
   `MAX_SAFE_HEIGHT + 2` (see "Reference: Fence geometry"), except that in a column the player's
   current box overlaps only cells at or above the box's top qualify. That exception is what lets
   an overheight player always descend instead of being caged.
4. Diffs the set against last tick's: new cells get `ClientboundBlockUpdatePacket(pos, BARRIER)`,
   dropped cells get the real state back. Every `REFRESH_INTERVAL_TICKS` the whole set is resent,
   since a real update at a fenced cell overwrites the client's copy.

One rule covers both requirements: over a column whose ground is within reach the qualifying cells
form a ceiling, and over a drop they fill the whole window — a wall at the ledge. Packets go only to
the holder; the server world is never touched.

**Done when.** In-world, flying up stops with the feet 16 blocks above the ground, flying toward a
cliff edge at any height that would put the player more than 16 above the lower side stops at the
edge (and passes once low enough), a second player sees no barrier and walks through those cells,
and landing, unequipping, or toggling flight off leaves no barrier behind (`F3` targeted-block
readout shows air). Flying out over the edge of an End island is refused.

### As built

The own-column exception in step 3 was too wide. A player standing at a cliff edge has their box
hanging over the drop, so the moment they start flying that column counts as theirs, takes no wall,
and they can drift one column out over the drop — overheight, and three seconds from S2's cutoff.
Now only the column under the player's centre (`blockPosition()`) is limited to a ceiling; a column
the box merely hangs into keeps its walls and only drops cells wholly below the feet. A wall cell
cutting into the box is safe because a barrier suffocates by the default rule
(`BlockBehaviour.java:1007`) and the client pushes itself out of suffocating blocks toward free space
every tick (`LocalPlayer.java:794-798`), which moves the player back toward the centre column.

Code review then found two more gaps. A column the box barely overhangs took cells above the box,
and the client collides with a shape just above even while ignoring the ones it already overlaps;
under about 0.09 of overhang its push-out probes (±0.21 from centre) never fire, so a player who
started flying there could not rise. An overhung column now takes only the cells beside the box.
And only air cells were fenced, so a column of weeping vines, glow lichen or similar left a hole a
flyer could slip into; now any cell with no collision and no fluid is fenced, hiding the block for
that one player while the fence stands.

The chunk check uses `hasChunkAt(x, z)`, not `isLoaded(pos)`: the latter answers false for any y
outside build height (`Level.java:719-722`), which would have skipped columns near the ceiling.

Build and lint are green; the in-world leg is not yet observed.

## S4 — Wax Wings become a glider — **in progress**

**Build.** In `waxwings/WaxWings.java`, give the item `durability(ELYTRA_DURABILITY / 2)` (read off a
fresh `ItemStack(Items.ELYTRA)`, the same way `FlightSpeed` reads vanilla's default instead of
duplicating a literal), `repairable(Items.HONEYCOMB)`, `DataComponents.GLIDER`, and an `EQUIPPABLE`
matching the elytra's (`Items.java:894-908`: equip sound `ARMOR_EQUIP_ELYTRA`, `setDamageOnHurt(false)`)
with the existing `wax_wings` asset. Lore stays. Match vanilla's elytra tags in
`ArtifactsItemTagProvider`: `enchantable/durability` and `enchantable/equippable` (vanishing follows
from durability), and leave `enchantable/armor` and `enchantable/chest_armor`, which the elytra is
in neither of; stay in `shadow_enchantable`. Existing stacks pick up the new defaults by themselves,
since the item id is unchanged and a stack stores only its patch against the prototype.

**Done when.** In-world, jumping from height with the wings on glides; the durability bar reads out
of 216; an anvil repairs them with honeycomb; an enchanting table offers Unbreaking and Mending but
not Protection.

*Corrected:* the enchanting-table check cannot pass for any artifact. The table also needs an
`ENCHANTABLE` component, which neither the elytra nor these items carry, and Mending is treasure-only
regardless. The real check: at an anvil, Unbreaking and Mending books apply to the wings and a
Protection book does not.

### As built

The durability is a literal `432 / 2`, not read off `ItemStack(Items.ELYTRA)` as planned: an item's
components bind only when the registry freezes (`Item.components()` → `Holder.Reference.components`,
`Holder.java:265`, `"Components not bound yet"`), and this item registers before that. The planned
read compiles and would have thrown at boot.

Generated tags now hold `wax_wings` in `enchantable/durability` and `enchantable/equippable`, and
`talaria` alone in `enchantable/armor`; `enchantable/chest_armor` is no longer emitted. The dev
server boots clean, and over RCON a chest-held stack answers `Test passed` to
`[minecraft:max_damage=216]`, `[minecraft:glider]` and `[minecraft:repairable={items:"minecraft:honeycomb"}]`
while the controls `max_damage=432` and `phantom_membrane` fail. The in-world leg is not yet observed.

## S5 — Altitude melt — **in progress**

**Build.** Add `waxwings/WaxMelt.java`, ticked from a new `WaxWingsHooks.onTick` (the effect returns
as `wax_wings_glide`). While gliding, once every 20 glide ticks — vanilla's own cadence — it adds
`multiplier(y) - 1` points of wear on top of vanilla's one, where `multiplier(y)` is 1 at or below
`MELT_START_Y` and rises by 1 every `MELT_BLOCKS_PER_STEP` above it. The fractional part is rolled
against the player's random so the average holds, and `wear` clamps per Finding 4. `hurtAndBreak`
already applies Unbreaking and skips creative.

**Done when.** In-world, on unenchanted wings, a minute of gliding at y≈60 costs about 60
durability and a minute at y≈190 about 180, and the wings never break outright — they stop gliding at one point left.

### As built

As planned. Build and lint are green and the dev server boots clean; the in-world leg is not yet
observed.

## S6 — Empty-hand boost — **in progress**

**Build.** Add `waxwings/BoostCatcher.java`: one `ElementHolder` with one `InteractionElement`
(`SIZE`×`SIZE`, offset down by half so it centres on its point) per player, created lazily and kept
for the session. A `ManualAttachment` supplies the point; with it no watcher is ever added
automatically (`ManualAttachment.java:41-46` are no-ops), so only `startWatching(player)` shows it.
Each tick, while the wings are gliding and the main hand is empty, it is attached in the player's
current level, its point set to the eye plus `getKnownMovement()` × (1 + latency / 50) ticks, and
ticked; otherwise it is destroyed. A level change re-attaches.

The handler, on a main-hand interact, calls `waxwings/WingBoost.tryBoost`: re-check gliding, empty
hand and `ItemCooldowns`, then do what `FireworkRocketItem.use` does (`FireworkRocketItem.java:58-75`)
with a plain `Items.FIREWORK_ROCKET` stack — drop leash connections, spawn a `FireworkRocketEntity`
attached to the player — plus a server-side arm swing (the client's own interaction returns
`CONSUME`, which does not swing), a `BOOST_COOLDOWN_TICKS` cooldown on the wings, and
`BOOST_WEAR` × `multiplier(y)` wear through `WaxMelt.wear`. Add `polymer-virtual-entity` to
`fabric.mod.json`'s `depends`.

**Done when.** In-world, right-clicking with an empty main hand while gliding launches a rocket-style
boost with its sound and trail, a second click inside two seconds does nothing and the wings show a
cooldown sweep, a real rocket in the main hand still boosts and is consumed, and nothing happens on
the ground or when not wearing the wings. Checked at ping above 100 ms as well, since the catcher's
lead is the part latency breaks.

### As built

As planned, with a plain `ElementHolder`: `HoverLabel`'s owner-only subclass exists because
`ChunkAttachment` pushes every chunk watcher at its holder, which `ManualAttachment` never does. The
handler is a nested `ClickHandler` class, since `VirtualElement.InteractionHandler` has only default
methods and so is not a functional interface. The shaded jar's `WaxWings`, `OrbOfLevitation` and
`OrbHooks` reference only `club/rainbowkitty/artifacts/shaded/kalic0re/...`, and its
`fabric.mod.json` lists `polymer-virtual-entity`. Build, lint and a clean dev-server boot are green;
the in-world leg, including the high-ping check, is not yet observed.

Code review found the catcher staying up during the cooldown, where it swallowed every click for
nothing — including an off-hand rocket or a water bucket meant to save a landing. It now hides
while the wings are on cooldown. The constants landed as `WingBoost.COOLDOWN_TICKS` and
`WingBoost.WEAR`, not the `BOOST_`-prefixed names above.

## S7 — Icarus moves to the wings — **in progress**

**Build.** Register a second `DeathMessageOverrides` entry after the orb's (so the orb's stateful one
still clears its entry first): a fall death (`DamageTypeTags.IS_FALL`) while wearing Wax Wings at
`nextDamageWillBreak()` reads "%1$s flew too close to the sun". Stateless — the chest slot is still
populated when the message is built (`ServerPlayer.java:906` precedes the drop at `:941`).

**Done when.** In-world, gliding until the wings give out and dying from the landing shows the Icarus
message; a fatal fall with healthy wings shows vanilla's.

### As built

As planned. `Main` now calls `OrbOfLevitation.init()` before `WaxWings.init()` to get that
registration order. Build and lint are green; the in-world leg is not yet observed.

## S8 — Walk-through ground rule and fence smoothing — **in progress**

**Build.** Replace S2's ground rule: any fluid or any block with collision is ground, and anything a
player walks through is not. Leaves, lava and the like now count; S2's leaves, scaffolding and
water special cases go. With that rule, "not ground" and "passable" are the same test, so S3's
separate open-cell check folds into it.

Smooth the fence so a small bump in the ground cannot leave a pocket in the ceiling to fly up into
and get walled in. `AltitudeFence` classifies every cell of the scanned box once — ground, open
within reach of its own ground, or open and too high — into a `CellGrid` that reaches
`2 × SMOOTHING_RADIUS` past the candidate columns. Within each level it then applies a
morphological opening to the allowed cells: an open cell stays allowed only if some square of
allowed cells `SMOOTHING_RADIUS` wide on each side covers it. Ground counts as allowed, so a gap
between two terrain walls is never mistaken for a hole. The opening only ever removes allowed
cells, so the fence is still never looser than S3's raw rule, and S2's cutoff, which keeps the raw
rule, never fires on a player the fence lets through.

Replace the orb's borrowed icon with its own placeholder: a centred 10×10 square in the same
dark-purple outline and 2×2 magenta/cyan checker as the other two.

**Done when.** In-world, flying at the cap over flat ground past a lone one-block pillar or a
two-wide one leaves no pocket above it (`F3` shows barrier in the pillar's column at ceiling
height), a three-wide one still does, the ceiling over a leaf canopy sits 16 above the leaves, a
sea or lava lake counts as ground, and a two-wide canyon flown low stays open. The orb shows the
square icon with the server pack.

### As built

As planned, with `SMOOTHING_RADIUS = 1`. Before building, a throwaway Python model of the classify-
and-open rule on one level confirmed the intended shapes: a 1×1 and a 2×2 pillar's pockets fill, a
3×3 one survives, a cliff edge and a 2-wide canyon between solid walls come through unchanged. It
also showed a consequence: the airspace over a 1-wide bridge across a ravine fills too, so a narrow
bridge or ridge can be walked but not flown along low. A column in an unloaded chunk reads as
ground, so it is neither fenced nor the reason a neighbour is.

The scan now reads a box two columns wider on each side than before. That was first estimated as
roughly doubling its block reads per tick, but for S3's small boxes it is about 3.5× at a typical
lookahead and more when hovering. S9's lazy column reads recover most of it. Build, lint and a
clean dev-server boot are green; the in-world leg is not yet observed.

## S9 — Linear footprint, still skip, room around pillars — **in progress**

**Build.** Four changes to the orb's fence, driven by the load test in `scripts/loadtest/`.

*Footprint.* S3 swept the player's box along the lookahead as an axis-aligned box, so a diagonal
heading read a square whose area grows with the square of the lookahead. Place barriers instead in
a rectangle aligned with the direction of travel: from `REAR_BUFFER` behind the box to
`FORWARD_BUFFER` past where the lookahead puts it, `SIDE_BUFFER` wider on each side (a hovering
player gets a square). The forward buffer absorbs a latency spike the averaged ping behind the
lookahead has not caught up with. The grid behind it keeps an axis-aligned box but reads a column
only on first use, so only the columns near the rectangle are ever read. With growth linear, raise
`MAX_LOOKAHEAD_BLOCKS` from 8 to 12, so a sprinting holder is capped only past ~400 ms of ping
rather than ~200.

*Still skip.* Skip the scan while the client reported no movement and the box sits where the last
scan saw it. Each holder still rescans and resends on a refresh tick every 20, staggered by UUID so
holders do not all refresh on the same tick.

*Room around pillars.* S8's opening flattened every bump narrower than 3×3 away. Keep that, but
give back 25% (`ROOM_FRACTION`) of each shaved bump's height as headroom over the ground within
`SMOOTHING_RADIUS` of it — the top-hat of the opening, dilated — so a 1×1 or 2×2 pillar raises the
ceiling over at least a 3×3 area rather than leaving a hole. Pits are untouched (the opening never
raises one). A bump counts only when it stands on measured ground, and a cell with no ground in
reach stays forbidden: a pillar over the void must not open the void around it. This makes the
fence looser than the raw rule near a pillar, so S2's cutoff moves onto the same rule: it now trips
when the body, in the column under its centre, overlaps a cell the fence forbids. Both read a new
`orb/HeightGrid`, which replaces S8's `CellGrid` and `Ground.heightAbove`.

**Done when.** A lookahead sweep with the harness shows the fence's cost growing linearly with
lookahead and a hovering holder costing about a twentieth of a moving one. In-world, a 1×1 pillar 8
tall lets a flyer rise 2 blocks higher over the 3×3 around it, a 3×3 one keeps its full pocket, a
pillar at the edge of an End island opens nothing over the void, and flying diagonally at speed
still meets the fence before passing through it.

### As built

As planned, with three changes found along the way:

- **The ground walk goes deeper.** A throwaway Python model of the room rule found that walking
  down only `FORBIDDEN_HEIGHT` below the window mixed up flat ground at that depth with "no ground",
  so a tall pillar got no room. `HeightGrid` now walks `MAX_DEPTH` = ⌈19 / 0.75⌉ = 26 below the
  window, the deepest ground any bump's room can make safe, and a cell with nothing that deep is
  forbidden outright.
- **Cells well under the ceiling take a shortcut.** A cell below the line even by the lowest ground
  in its 3×3 skips the opening and the room, and the columns beyond its neighbours they read. The
  shortcut changes nothing: the model gave identical output with and without it on seven terrain
  shapes, and the sweep's barrier rates match the build without it within noise.
- **The cutoff reads the same rule.** `AltitudeGuard` trips when the body, from feet cell to head
  cell in the column under its centre, overlaps a cell `HeightGrid` forbids.

The model, with the deeper walk:

| Terrain | Result |
| --- | --- |
| 1×1 pillar, 8 tall | ceiling rises 2 blocks over its 3×3 |
| 2×2 pillar | ceiling rises 2 blocks over its 4×4 |
| 3×3 pillar | keeps its full pocket |
| Pit, cliff | unchanged from S8 |
| Pillar over the void | opens nothing |

A bump counts only up to the level being tested, so room stops growing once a pillar reaches about
25 above the ground, about 7 blocks of extra room.

`Footprint.touches` tests each column's square against the rectangle with the separating axis test
(the rectangle's two axes and the world's two). The fence record is kept while flying even with no
cells, so stillness over open ground is still detected.

**Sweep.** `orb-fence.js --bots 18 --legs orb --speeds 0,0.1,0.45,1,2,3` on the dev server, 45 s
windows, µs of `AltitudeFence.update` per flying bot per tick. On loopback the lookahead is about
4 × speed blocks, so speed 3 reaches the 12-block cap.

| Speed (blocks/tick) | S8 | S9, no shortcut | S9 | S9 barrier sets/bot/s |
| --- | --- | --- | --- | --- |
| 0 (hover) | 15.2 | 4.9 | 1.9 | 0 |
| 0.1 | 38.4 | 54.2 | 33.1 | 7.2 |
| 0.45 | 35.4 | 59.5 | 26.6 | 16.2 |
| 1 | 37.8 | 76.5 | 35.7 | 28.6 |
| 2 | 63.0 | 94.5 | 50.0 | 58.4 |
| 3 | — | 117.0 | 61.2 | 102.2 |

The harness half of "Done when" is met:

- **Linear.** Cost grows linearly with lookahead, about 3 µs per block from 4 to 12 blocks. At the
  new 12-block cap it costs what S8 did at 8.
- **Hover.** A hovering holder costs 1/17 to 1/19 of a moving one.

At speed 1, 73% of the fence's time is reading columns (`HeightGrid.readColumn`). The smoothing
arithmetic itself is about 15%.

The speed-2 leg had one 168 ms tick. It lines up with vanilla's 6000-tick autosave: the server
booted at 12:15:44 and the spike fell in the sample ending about 12:20:46, with ~2700 chunks
force-loaded. The fence's own total for that leg sits on the line between its neighbours.

Build, lint and a clean dev-server boot are green. The in-world legs are not yet observed.

## S10 — Step-down under a dropping ceiling — **done**

**Build.** Flying at the cap toward lower ground, the fence ceiling drops a block and its lip
catches the top of the box. The vanilla client cannot help here:

- **Step-up only on the ground.** `Entity.collide` (`Entity.java:1153`) steps up only when on the
  ground or landing, so a flyer never gets it.
- **No step-down, no slopes.** Collision shapes are boxes, so no fake block makes a ramp.

The lip is a wall that zeroes horizontal speed. Instead, push the flyer down just before they reach
it.

*The push.* The server adds velocity with the `playerKnockback` of a `ClientboundExplodePacket`,
which the client adds to its own (`ClientPacketListener.handleExplosion`). The blast has no side
effects:

- **Silent:** its sound is `SoundEvents.EMPTY`, which the client skips outright.
- **Invisible:** it sits 64 blocks below the player, past the client's 32-block particle cull,
  with a particle that does not override the cull and no block particles.

The other two ways were rejected:

- **Motion packet:** it overwrites the client's velocity with the server's stale copy.
- **Relative teleport:** the server ignores the player's moves until the client acknowledges it.

*Detection.* In `AltitudeFence`'s scan, `orb/StepDown` walks the box along the heading, over the
lookahead plus a block, reusing the fence's grid extended `MAX_DROP` levels down. If the first
thing it meets at body height is a fence cell, it looks for the smallest drop of at most
`MAX_DROP` (2 blocks) that keeps the lowered box clear to a block past that cell, fence and real
blocks alike. Anything deeper is a ledge and stays a wall, and real terrain is never stepped under.

*Sizing.* Flight keeps 0.6 of vertical speed each tick (`Player.java:1412`), and holding jump or
sneak adds 3 × the flying speed a tick (`LocalPlayer.aiStep`). The push is solved from that
recurrence to land the box top 0.1 under the lip by the time the client reaches it, after the
round trip:

- **Normal:** it aims at the time left before reaching the lip, capped at 6 ticks.
- **Holding jump:** it waits until within 3 ticks and pushes harder, since jump's climb would undo
  an early push.

One push is outstanding at a time, until the box is seen below the lip or a timeout.

*Backstop.* A client pressing a movement key into a wall reports `horizontalCollision`. That holder
is rescanned even when still, and walked in the direction of its keys, so one caught anyway (a
turn, a lag spike) is pushed from a standstill.

**Done when.** In-world at the cap:

- Flying down a hillside of 1-block steps, walking and sprinting, never stops at a lip.
- Holding jump while doing it still gets through.
- A drop of 3 or more stops you as before.
- Nothing is seen or heard when it fires.
- Neither the dev client log nor the server log shows a warning when it fires.

### As built

As planned, with two additions:

- **Minimum push time.** A push is never sized for fewer than 2 ticks. A late push aimed at 1
  tick would carry the flyer 2.5× past the lip, since flight damps it only after the first tick.
- **Already at the lip.** A holder already overlapping a fence cell is pushed like one stuck
  against it.

To extend the fence's grid `MAX_DROP` levels down for `StepDown`, its missing-ground marker (the
point below which a column counts as having no ground) now sits 2 levels deeper as well.
Ground 27–28 below the window, which used to read as missing, now reads as measured:

- **The cell's own answer is unchanged.** A cell over ground that deep is forbidden either way,
  since 0.75 × 27 − 1 > 18.
- **A neighbour can now gain room.** A bump standing on that ground now counts toward the room of
  cells beside it. That needs a pillar over a drop of 27 or more next to shallow ground, so it is
  rare.

**Load check.** The same harness, 18 bots, speeds 1 and 3, 45 s windows:

| Measure | Speed 1 | Speed 3 |
| --- | --- | --- |
| `StepDown.check` per bot | 1.9 µs/tick | 4.6 µs/tick |
| Share of `StepDown.check` in `walk` | ~80% | ~80% |
| `AltitudeFence.update` per bot, with step-down | 36.8 µs | 64.3 µs |
| `AltitudeFence.update` per bot, S9 | 35.7 µs | 61.2 µs |
| Pushes sent | 0 | 0 |

`walk` builds the path's column list through a boxed `HashSet`. The bots fly below the ceiling and
the course's drops are all deeper than `MAX_DROP`, so no push was sent: the harness proves the
cost, not the push.

**Test lanes.** Built in the dev world east of the load-test course. Each lane is 8 wide, runs
along +x from an 8-high run-up at x 2132–2139, and steps down from x 2140:

| Lane | z | Block | Drops | Expect |
| --- | --- | --- | --- | --- |
| A | 2040–2047 | stone | 1 block every 4 | no catch |
| B | 2052–2059 | andesite | 2 blocks every 4 | no catch |
| C | 2064–2071 | granite | 1 block every block | catches when sprinting; a stress lane |
| D | 2076–2083 | diorite | 3 at x 2152, 5 at x 2176 | a wall both times |

Build, lint and a clean dev-server boot are green.

Observed on 2026-09-27 by the user, flying on real Nether terrain: small drops no longer catch, and
it "works pretty well". The user asked to go further, so S11 replaces the single push with a
forced descent off any ledge up to the cap. S10's remaining "Done when" items carry into S11's.

## S11 — Soft ledges and forced descent — **done**

**Build.** Take S10 from small steps to any ledge up to the orb's cap. The fence stops walling off
a drop the flyer could be pushed down: the wall moves out to the lower ceiling beyond it, and the
flyer is flown down to it.

*Soft walls.* A forbidden cell at or below the flyer's head is a wall. It stays out of the fence
when the flyer's box top stands at most `MAX_LEDGE` (= `MAX_SAFE_HEIGHT`, 16) above that column's
line. The first build also kept a narrow hole walled, so that it wouldn't dip every flyer crossing
it; see "As built" for why that was replaced.

Everything above the head stays fenced, so a flyer over a drop has a lid at their own height and
cannot climb.

*The line.* A column's line is its first forbidden level:

- **Formula.** The opened ground plus the room, plus `FORBIDDEN_HEIGHT` + 1, read at the head's
  level. `HeightGrid.ceilingLevel` computes it.
- **Why it can be read there.** Over open air the smoothed ground is the same at every level, so
  it holds from the head down to the line.

`HeightGrid` walks down `WALK_DEPTH`, far enough to find the ground under a 16-block drop below the
cap, rather than `MAX_DEPTH`. Deeper ground was already forbidden by height alone, so only a bump
standing on it newly counts for room.

*Forced descent.* `orb/ForcedDescent` replaces `StepDown`. Each scanned tick it follows the ray
under the flyer's centre along the heading, from their own column to the lookahead. Each soft
column on it caps the box top at that column's line plus `DESCENT_SPEED` × the ticks before the
centre gets there, after the round trip. The lowest cap is the target, so the descent starts ahead
of a ledge and reaches the line as the flyer crosses it.

Each tick the box top is predicted: where the server sees it, plus what the pushes already sent will
still do. If that prediction is more than `DEADBAND` over the target, one more push goes out,
sized so its whole damped effect is the shortfall, capped at `DESCENT_SPEED`. A push every tick
settles at `DESCENT_SPEED` blocks a tick. The push is S10's silent explosion knockback. While jump
is held, each push also cancels that tick's climb. Counting pushes already sent means the descent
stops on target however high the ping, without a counter-push.

*The guard.* A legit forced descent can keep a flyer over the line for several checks, for example
flying down a long slope faster than `DESCENT_SPEED` follows. `AltitudeGuard` therefore does not
extend a streak on a check that finds the box top at least `DESCENT_PROOF` (0.5) lower than the
check before. A client that ignores the pushes stops dropping and is cut off as before.

**Done when.** In-world, flying at the cap in the dev world's lanes and on Nether terrain:

- **Lanes A and B:** glide down with no catch.
- **Lane D:** its 3-block and 5-block drops are flown down, not walled.
- **Deep cliffs:** a drop of more than 16 from the cap is still a wall.
- **Narrow gaps:** a 1–2 wide gap between ledges is crossed, freely below the rims' cap and with a
  small shove down at it.
- **Holding jump:** it neither stops a descent nor makes it stutter at the target.
- **Hovering past an edge:** a flyer hovering just past a ledge is brought down to the line.
- **No side effects:** nothing is seen or heard, and neither log shows a warning.

### As built

As planned, with three changes. The first two came from a throwaway Python model of the controller
against the client's flight physics. The model covered:

- Vertical speed kept at 0.6 a tick, and jump adding 0.15 a tick.
- A lid one level over the server's stale view of the box top.
- Round trips of 1 and 6 ticks, and speeds of 0.5 and 1.1 blocks a tick.

The changes:

- **Jump is cancelled every tick of a descent, not only on ticks that push.** Cancelling only when
  pushing left a flyer holding jump bobbing up to a block over the line. Jump's climb went
  uncounted, so the reckoning kept deciding it had pushed enough.
- **`MARGIN` is 0.15, not 0.05.** Aim plus deadband now stays under the line, so a finished descent
  ends under it and the fence's own ceiling takes over. At 0.05 a flyer holding jump could rest
  just over the line, which kept the descent, and a cancel packet a tick, alive indefinitely.
- **`ceilingLevel` takes `isForbidden`'s cheap lower bound.** The first build spent 8.8 µs per bot
  per tick at speed 1 and 15.4 at speed 3 in `ForcedDescent.update`, nearly all of it smoothing
  columns the flyer was nowhere near the line of.

With the first two changes the model settles every case: 0.1–0.15 under the line without jump, at
the line with it, and the pushes stop. Peak excursion over the line along the way:

| Terrain | Over the line at worst |
| --- | --- |
| 1-block ledge | 0.4–1.0 |
| 3-block ledge | 0.9–2.1 |
| 16-block ledge | 12–15, the descent itself, about a second |
| 1-block steps every 4 | 0.5–1.3 |
| 1:1 staircase, sprinting | 1.9–3.2, since it drops faster than `DESCENT_SPEED` |
| Hovering just past a 10-block ledge | brought down in 14 pushes |

The flyer is never stopped in any case, since nothing walls a soft drop.

**Load check.** 18 bots, 45 s windows, µs per bot per tick:

| Measure | Speed 1 | Speed 3 |
| --- | --- | --- |
| `AltitudeFence.update`, S9 | 35.7 | 61.2 |
| `AltitudeFence.update`, S11 | 34.7 | 61.5 |
| `ForcedDescent.update`, S11 | 0.4 | 1.0 |
| Pushes | 0 | 0 |

The course's only ledge is the plateau's 24-block edge, deeper than `MAX_LEDGE`, so the harness
still proves only the cost.

**Fix: pits are filled on the opened ground, not the raw ground.** On 2026-09-27 the user got
walled into a pocket over a Nether basalt delta at (58, 51, −368):

- **The terrain.** Basalt spikes from 32 to 39 stand over lava at 31, with 1-wide lava gaps between
  them.
- **Reading it.** A Python replay of the fence read the terrain straight from the dev world's
  region file (`save-all flush` first).
- **The cause.** The opening shaves the spikes, so the line over the gaps sits at 50–52, under the
  box top of 53.04. Those cells are forbidden. But the first build closed the *raw* ground, where
  the spikes fill the gaps back in, so it put the "filled" line at 54–57 and kept the walls hard.

`HeightGrid.filledGround` now closes the opened ground instead, and `REACH` grows from 3 to 4
columns to cover the closing's reads. On the replay:

| Filter | Wall encounters kept hard, of 5,550 |
| --- | --- |
| Raw ground closed (first build) | 474 |
| Opened ground closed (fix) | 0 |

The encounters cover the 21×21 columns around the spot, with box tops from 49.5 to 57. A 1- and a
2-wide hole in flat ground still keep their stubs under the fix.

**Change: narrow gaps are filled in the height rule, not walled.** The user then couldn't cross a
1–2 wide gap between two ledges at (140, 50, 97): a diagonal crack down to the lava at 31, between
basalt at 47–49. They were flying a block above the basalt. The crack's line is about 52, so the
filter above kept its walls hard at every height. The user asked for gaps like this to be
passable, with a small downward shove.

The narrow-hole filter is gone. `HeightGrid` now fills the gap in the height rule itself:

- **The fill.** A pit narrower than 3 is filled to `PIT_DIP` (1) under the line of its closed rim
  (`pitLevel`: a 3×3 closing of the opened ground, per the fix above).
- **Who reads it.** `isForbidden` clears cells under that line and `ceilingLevel` returns at least
  it, so the fence, the soft walls, `ForcedDescent` and `AltitudeGuard` all follow the one rule.
- **Wide drops.** Closing leaves a wide drop unchanged, so their line is as before.

Replayed on the crack:

| Flying | Walls |
| --- | --- |
| A block over the basalt | none |
| At the basalt's cap | all soft, lines 65–68; the flyer is pushed down 1–3 blocks crossing |

The basalt delta above still replays as all soft.

`HeightGrid.mayBePit` bounds the closing by the highest opened ground in the centred square, which
the room already read. That spares cells away from pits the closing's reads two columns further
out.

Build and lint are green.

Observed by the user on Nether terrain on 2026-09-27: forced descent off ledges works. Each place
they were caught led to one of the two fixes above. S12 then removes the walls S11 softened, so
S11's remaining "Done when" items carry into S12's.

## S12 — No walls, a lift over water — **in progress**

**Build.** The user's call after flying S11: no barrier walls at all, only the nudge, but keep
the ceiling. And the orb now dislikes water. Its lore becomes two lines: "It is said that you can
ponder this to great heights..." and "It also doesn't like water."

*Ceiling only.* `AltitudeFence` places only forbidden cells over the head. The columns covered:

- the one under the centre;
- any the box doesn't hang into.

A ledge of any depth is left open and flown down: `MAX_LEDGE` is gone. Where the ground lies past
the grid's walk, the nudge aims at `HeightGrid.deepestLine` and refines the aim as the ground comes
into reach.

The one exception is a void column (`HeightGrid.isVoid`): no ground in reach, and a world-surface
heightmap at the world's floor, as off an End island. It keeps its wall beside and below the head,
since a nudge there would fly the holder down into the void.

*Water.* While the orb is held and the first ground at or under the feet is water, the flyer is
treated as being over water:

- **Forced flight.** `WaterLift.keepAloft` puts flight back on every tick, so double-tapping
  jump, a guard cutoff or sinking into the water can't end it. It skips a passenger, and a holder
  whose flight `ObstructionGuard` withholds.
- **Water is no obstruction.** `ObstructionGuard` now withholds flight in lava only, not in water,
  so a holder in water is flown up out of it.
- **The floor.** `ForcedDescent` becomes `AltitudeNudge` and nudges both ways. Over water it
  floors the feet at `WATER_HEIGHT` (= `MAX_SAFE_HEIGHT` / 2, 8) over the surface. It uses the
  same reckoning and the same approach along the heading as the ceiling, never within a box of
  the ceiling.
- **Sneak and jump.** Sneaking is cancelled within `FLOOR_BAND` (1) over the floor, so resting
  there holds still. While nudging up it cancels sneak, and while nudging down it cancels jump.

**Done when.** In-world:

- **Ledges:** flying off any ledge, including one deeper than 16, is a glide down with no wall,
  and a holder hovering just past an edge is brought down.
- **The End:** an island's edge over the void is still a wall.
- **Narrow gaps:** crossed with at most a small shove.
- **Water, from anywhere:** walking into water, swimming, or falling toward it with the orb held
  flies the holder up to 8 over the surface.
- **Water, staying up:** double-tapping jump or holding sneak there can't take them lower.
- **Water, leaving:** once over land again they can descend and land as usual.
- **Lore:** the new lore shows.
- **No side effects:** nothing is seen or heard when a nudge fires, and neither log shows a
  warning.

S13 revises the water half: in water the orb stops working again, and the floor is a flat 6.

## S13 — Ceiling game rule, a wet orb, gold names — **in progress**

**Build.** Four changes from the user.

- **Gold names.** Talaria, Wax Wings and Orb of Levitation get gold, bold, italic `ITEM_NAME`s.
  They are the mod's only items.
- **The orb stops working when it gets wet.** `ObstructionGuard` becomes `LiquidGuard` and withholds
  flight in water or lava again, reversing S12's lava-only change. Over water, but not in it,
  `WaterLift` still keeps the holder flying and the nudge holds them above it, so they don't get
  wet. Once in the water they swim.
- **A flat 6 over water.** `WaterLift.WATER_HEIGHT` is 6 whatever the ceiling. It was
  `MAX_SAFE_HEIGHT` / 2. Where a low ceiling leaves no room for it, the nudge caps each column's
  floor a box height plus `MARGIN` under that column's own line.
- **The ceiling is a game rule.** `artifacts:levitation_orb_flight_ceiling` is an integer, 0–384,
  default 16, under the player rules, registered in `orb/OrbGameRules`. It is read live, never
  cached. It replaces `AltitudeGuard.MAX_SAFE_HEIGHT`: each `HeightGrid` reads it once when built,
  and its forbidden height (ceiling + 2) and ground walk (2 × ceiling + 3) follow. So do the
  fence, the nudge, and `WaterLift`'s search for water under a falling holder. Lang entries are
  added through datagen.

The user also asked to remove every flight cutoff except water and lava. `LiquidGuard` drops
fire, cobwebs, sweet berry bushes and vines. `AltitudeGuard` was deleted too, then restored at the
user's word as the fallback for a client that ignores the ceiling and the nudges. It keeps its
three-check streak and S11's `DESCENT_PROOF`.

Forced flight over water is also narrowed:

- **Only near the water.** `WaterLift.keepAloft` forces flight only within `WATER_HEIGHT` of the
  surface. Higher up a holder may stop flying and falls until caught there.
- **Catching a fall.** A falling holder is caught early by as far as they will still fall. That is
  a round trip at their current fall speed, plus 2.5 ticks of it, which is what flight's 0.6
  damping lets run on.
- **How deep it looks.** The walk for water goes at least `CATCH_REACH` (48) deep, whatever the
  ceiling.

**Done when.**

- **Game rule:** `/gamerule artifacts:levitation_orb_flight_ceiling` reads 16, takes a new value,
  and refuses anything over 384. Checked over RCON on the dev server on 2026-09-27.
- **Ceiling follows the rule:** in-world, changing the rule moves the ceiling on the next tick.
- **In water:** flight stops, and holding the orb there does nothing.
- **Leaving water:** surfacing, jumping out or swapping the orb away and back gives no flight;
  standing on land 3 s does.
- **Over water:** flying over it holds the flyer at least 6 over the surface.
- **Stopping over water:** stopping flight high over water is allowed, and the fall is caught
  near 6 over it.
- **No other cutoffs:** fire, cobwebs, berry bushes and vines no longer cut flight.
- **Fallback cutoff:** a client that ignores the ceiling is still cut off after 3 s overheight,
  while a long nudged descent is not.
- **Gold names:** the three names show gold, bold and italic, with and without the server's
  resource pack.

### As built

As planned, with one fix. The names first shipped as `Item.Properties` components and still showed
as plain white on the dev client. In 26.2, `Item.Properties.finalizeInitializer`
(`Item.java:682`) sets `ITEM_NAME` to the translatable name after every component the properties
hold. So the literal name had never applied, styled or not. The white text was the lang entry.

The fix is `club.rainbowkitty.artifacts.ArtifactNames.named`. It registers one more
`DataComponentInitializers` entry right after the item is constructed. An item's initializers run
in the order they were added, so it runs last and its name sticks.

Checked over RCON on a dropped stack of each item: `execute if items … [item_name={text:…,
color:"gold",bold:true,italic:true}]` passes for all three, and a match against the translatable
name fails. The other mods' `ITEM_NAME`s are written onto stacks, which vanilla leaves alone, so
only artifacts had the bug.

**Fix: the orb stays wet for `DRY_TICKS` (3 s).** The user tested, and swimming to the surface
with the orb gave immediate upward flight. `LiquidGuard` lifted its cutoff on the first tick the
box left the water, which a swim bob or a jump at the surface gives. Being within `WATER_HEIGHT`,
`WaterLift` then forced flight on and the nudge lifted them away.

The orb now stays wet until its holder has been out of every liquid for `DRY_TICKS` in a row.
Getting back in restarts the count, so a jump out of the water, well under a second of airtime,
never dries it, while climbing onto land does.

Wetness is kept as the tick the holder was last in a liquid, and survives letting go of the orb.
Before, `onActivate` granted flight and cleared the guard, so scrolling away and back dried the
orb at once. Now `onActivate` grants flight only to a dry orb, and only logging out clears the
state. Time spent with the orb put away still counts toward drying.

Build, lint, datagen and a clean dev-server boot are green, and the game rule and name legs are
observed. The in-world legs are not yet observed. S14 replaces the drying delay and drops the bold.

## S14 — The waterlogged orb — **in progress**

**Build.** The user tested S13's drying delay on the dev server while it still ran the build
before the fix above, so swapping the orb away and back still dried it at once. They asked for
this instead:

- **The waterlogged item.** An orb that gets wet becomes
  `artifacts:waterlogged_orb_of_levitation`, "Waterlogged Orb of Levitation". It gives no flight,
  has its own lore, "It needs to be dried before it will work again." (the user's wording, after
  a first version that also said where), and has its own blue-recoloured placeholder texture over
  the same Heart of the Sea fallback.
- **When it waterlogs.** In water, `LiquidGuard` turns every orb in either hand into it with
  `ItemStack.transmuteCopy`, which keeps a rename or anything else on the stack. It ends flight
  on that tick, not a tick later when the effect deactivates, so no fall is marked.
- **Drying it.** It dries back into the orb by smelting in a furnace (200 ticks) or cooking on a
  campfire (600), vanilla's own times, in datagen's `ArtifactsRecipeProvider`. These are the
  mod's first recipes.
- **What goes.** `DRY_TICKS` and the wet map. Only an orb in hand gets wet: one in the inventory
  stays dry.
- **Lava.** It still just withholds flight while the holder is in it. That state survives letting
  go of the orb, and only logging out clears it.

The user also asked for the names without bold: `ArtifactNames` now makes them gold and italic.

**Done when.**

- **Waterlogging:** swimming, or dipping into water, with the orb in either hand turns it
  waterlogged and ends flight at once.
- **The wet item:** a waterlogged orb gives no flight, and swapping it about changes nothing.
- **Furnace:** smelting it gives a working orb back.
- **Campfire:** cooking it on one does the same. This also needs checking on a vanilla client,
  since a campfire shows the item it is cooking.
- **Lava:** flight is off only while in it.
- **Names:** gold and italic, not bold.

**Other furnaces.** The smelting recipe is `RecipeType.SMELTING`, which a modded furnace built on
`AbstractFurnaceBlockEntity`, or one that looks up that type itself, runs as it is. The blast
furnace and smoker have types of their own and do not dry it; nor does a machine with its own
recipe type. A modded campfire reads `CAMPFIRE_COOKING` and the `CAMPFIRE_INPUT` property set the
same way the vanilla one does.

## S15 — One hand per click — **in progress**

**Build.** On the dev server, a waterlogged orb in each hand went on a campfire together from one
click, where two stacks of beef go one per click. The client offers a click to its hands in turn
and stops at the first attempt it predicts will succeed. It sees the orb as a Heart of the Sea,
which no campfire takes, so it predicts a pass and offers the click to the off hand too, and the
server, holding no notion of a click, answers both. minecart-tweaks met the same thing with a link
item in each hand (`e895f8b`) and fixed it for that item alone. The user asked for a generic fix
in kalic0re.

- **kalic0re `common/click`.** `HandClicks` rebuilds the click on the server from the order its
  attempts arrive in: each hand, main first, at the block or entity, then with the item alone. An
  attempt out of that order, or more than a tick after the last, starts a new click. Once an
  attempt succeeds on the server, the rest of the click is dropped, and the first drop resends
  the inventory in case the client acted one out. A failure does not take the click, though the
  vanilla client stops at one at a block: that is how a claim mod refuses a block, and players
  have always gone on past it to the item.
- **Fabric's entity callback.** `UseBlockCallback` and `UseItemCallback` answer inside the wrapped
  calls, so they count. `UseEntityCallback` answers in the packet handler and returns from it, so
  a callback taking an entity click calls `HandClicks.take` itself. The drop still comes before
  any callback.
- **The artifacts mixin.** `HandClicksMixin` on `ServerGamePacketListenerImpl` wraps `useItemOn`,
  `useItem` and `interactOn` and injects at `handleInteract`'s `packet.hand()`. Mixins are never
  shared code, so each consumer carries its own.
- **kalic0re 0.21.0.** artifacts moves up from 0.13.0 for it. Its four other packages are unchanged
  since then, except that `PolymerModelItem` now gives a vanilla client without the resource pack
  the fallback item's own model instead of a missing one.

**Done when.**

- **Campfire:** a waterlogged orb in each hand goes on one per click, like the beef.
- **Plain use:** a vanilla item in the off hand still works when the main hand has nothing to do
  with the click. Eating from the off hand while holding a sword is enough.
- **minecart-tweaks:** open. Its own fix stands until it takes `common/click` and its link
  callback calls `HandClicks.take`.

## S16 — The orb soaks before it waterlogs — **in progress**

**Build.** The user asked for the orb to take durability damage over 7 seconds held in water, and
get it back over 10 minutes out of it, becoming waterlogged only when it runs out. S14's orb
waterlogged on the first tick in water.

- **Durability is wetness.** The orb has 140 points, `Wetness.SOAK_TICKS`, and loses one for each
  tick held in water, so a dry orb waterlogs 7 seconds in. It gets them all back over
  `DRY_TICKS`, 12000.
- **What the stack holds.** `artifacts:dry_at` is the game time the orb will be dry at. A tick in
  water adds 86 ticks of drying (12000 / 140, rounded up so 140 of them reach 12000), and the
  damage shown is the drying left, rounded to the nearest point, so each tick in water reads as
  exactly one. Because it is a time rather than a count, the orb dries wherever it is, a chest
  included, and catches up the next time it ticks in an inventory (`OrbItem.inventoryTick`). Game
  time is shared by every dimension and saved with the world. Polymer keeps the component from
  vanilla clients, which it sends the damage and maximum instead, so they see the bar. It skips
  the swap animation, since it changes every tick in water.
- **Waterlogging.** When a tick in water would soak it through, the orb becomes
  `waterlogged_orb_of_levitation` as before, keeping a rename but not its damage or `dry_at`.
- **Flight in water.** `LiquidGuard` now withholds it while the holder is in water, as it already
  did in lava, since the orb no longer waterlogs to end it. So an orb that survives a swim gives
  flight again on surfacing, and `WaterLift` lifts the holder to 6 blocks over the water. S13's
  "swimming up gives immediate flight" returns for any swim under 7 seconds with the orb held.
- **Unchanged.** Only an orb in hand soaks; one in the inventory keeps drying while its holder
  swims. A creative or spectator holder is not managed, so their orb never soaks.

**Done when.**

- **Soaking:** in survival, holding the orb in water drains its bar over 7 seconds, then it
  becomes the waterlogged orb.
- **A short dip:** leaving the water before then keeps a working orb, part damaged, whose bar
  refills over 10 minutes, faster in proportion when it was only part soaked.
- **In a chest:** a part-soaked orb left in one comes out drier by the time spent there.
- **Vanilla client:** the heart of the sea shows the durability bar.

## S17 — A wet orb drips and sputters — **in progress**

**Build.** The user asked for S13's wait out of the water back, as "a fraction of 3s inversely
proportional to the remaining durability", and for a wet orb's flight to stop at random, "the
average duration being proportional to the remaining durability, between 1s and 120s", with no
failures from a dry orb.

- **Which orb decides.** With an orb in each hand, the driest one does (`Wetness.driest`), since
  flight comes from whichever is held.
- **The wait.** Out of every liquid, flight comes back after `DRIP_TICKS` (60) times how soaked the
  orb is, rounded up: a tick after a dip, about 2.2 s after a 5-second swim, 3 s a tick from
  waterlogged. Read as a share of 3 s that grows as durability falls. Taken literally, 3 s over the
  points left would wait under a tenth of a second until the last dozen points, which would do
  little against surfacing into flight. `LiquidGuard` counts it only in ticks it sees with the orb
  held, and `OrbHooks.onActivate` starts it over, so swapping the orb away and back never shortens
  it. It follows lava too, which does not wet the orb, so only an orb already wet waits there.
- **The failures.** `Sputter` gives every tick of flight the same chance, one over the mean, so the
  flight between failures averages the mean. That runs in a straight line from 1 s a tick from
  waterlogged to 60 s barely damaged: about 18 s after a 5-second swim. The user first asked for
  120 s, then halved it. A failure cuts `flying`
  as `AltitudeGuard` does, with mayfly kept, and marks the fall for the death message. `WaterLift`
  runs after it, so within 6 blocks of water it puts the holder straight back into flight.

- **Rain.** The user asked for the orb not to dry in the rain. A held orb out of the water but in
  the rain (`Entity.isInWaterOrRain`, which counts rain only where it falls, not snow) has its
  `dry_at` moved on a tick each tick, so it keeps as wet as it is. As with the water, only an orb
  in hand is reached: one in the inventory dries on. The user then asked for one lying on the
  ground in the rain not to dry either. Nothing ticks a dropped stack, so `DroppedOrbMixin` hands
  every `ItemEntity` to `OrbOfLevitation.tickDropped` at the head of its tick, which does the same
  there. Checked over RCON with a floating dropped orb: in steady rain its `dry_at` moved 61 ticks
  in about 62; after `weather clear` it dried again. Vanilla's rain fades in and out over a few
  seconds (`isRaining` is a rain level over 0.2), so drying stops and starts that much after the
  weather changes.
- **Thrown into water.** The user asked for a dropped orb in water to soak as fast as a held one.
  `Wetness.soak` now takes the stack and hands back the waterlogged orb for its caller to put in
  place, so `tickDropped` soaks a dropped orb in water a tick at a time and swaps the entity's
  item once it is soaked through. Checked over RCON in a glass tube of water: 42 damage after
  about 41 ticks, and the waterlogged orb, with no damage or `dry_at` left, by 8 seconds.
- **Item frames.** A framed orb still dries in the rain. Nothing ticks the item in a frame
  (`BlockAttachedEntity.tick` only checks the frame still hangs), so it would take another mixin,
  and the user chose to leave it.
- **Switching flight off.** The user fell to their death after switching flight off themselves and
  got vanilla's death message: only the cutoffs, Sputter and letting go of the orb marked a fall.
  `FallTracker.follow`, replacing `resolveCatch`, now remembers who was flying at the end of their
  last tick, and marks a fall when flight stops mid-air, out of any liquid, whatever stopped it.
  A holder who never took off, stepping off a ledge, still gets vanilla's message.

**Done when.**

- **The wait:** in survival, leaving the water after a few seconds' swim gives flight back a moment
  later, longer the longer the swim; swapping the orb meanwhile starts it over.
- **Failures:** a soaked orb's flight cuts out often, a barely wet one rarely, a dry one never. A
  failure away from water drops the holder, who can take off again.
- **Rain:** a wet orb held in the rain, or lying on the ground in it, keeps its durability;
  pocketed, it dries.
- **Thrown in:** an orb dropped into water waterlogs in 7 seconds.
- **Death message:** switching flight off high up and dying of the fall gives "stopped pondering the
  orb".

## S18 — Drying by the point, only while ticked — **in progress**

**Build.** The user asked whether S16's stored dry time had any performance edge over plainly
ticking the durability up in water and down out of it. Hardly any: both need a per-tick hook to
notice water and rain, so the per-tick check happens either way. The one saving was in the
network. A fractional drying counter on the stack changes every tick, and a changed stack in a
player's inventory is resent to them each time, where `dry_at` changed nothing while drying but
the damage, once a point. Its real point was behaviour: drying wherever the orb is, exactly, with
no drift under lag. The user would rather orbs dried only while ticked, as long as it costs no
per-tick update to the player.

- **20 points, on the clock's beats.** 140 points over the 7-second soak changes a point a tick, so
  the durability drops to 20, the most points on which both the soak (140 ticks) and the drying
  (12000) come out in whole ticks: a point every 7 ticks in water, and every 600 out of it. Each
  lands where the game time is a multiple of its step, not on a count kept per orb, so the damage
  is all the stack holds, and it changes, and is resent, only when a point does: about three
  times a second while soaking, once every 30 seconds while drying. The first point in water comes
  0 to 7 ticks in, so waterlogging takes 7 seconds give or take a third of one, and a dip under 7
  ticks may cost nothing.
- **Where it dries.** In an inventory (`OrbItem.inventoryTick`) and on the ground
  (`OrbOfLevitation.tickDropped`), not held or lying in the rain, which now costs nothing: the orb
  just skips drying. Anywhere nothing ticks it, a chest or an item frame, it keeps as wet as it
  is, which also settles S17's item-frame gap.
- **What goes.** The `artifacts:dry_at` component and its Polymer registration. A wet orb saved by
  an earlier build still carries it, and an unregistered component makes its whole stack fail to
  load, so it is lost; only dev-world test orbs ever had one.
- **Unchanged.** The wait out of the water and the failure rate read the driest held orb's damage
  over 20 instead of its drying time, so they move in twentieths now.

**Done when.**

- **Soaking:** in survival, holding the orb in water, or throwing it in, waterlogs it in about 7
  seconds, its bar stepping down about three times a second.
- **Drying:** out of the water a wet orb gains a point every 30 seconds while carried or on the
  ground, and none in a chest or held in the rain.

## Open decisions

- **Rain slowdown on the orb.** It moved with the flight, but it was themed on wax. Settle by playing
  with it; delete `FlightSpeed`'s rain factor if it reads wrong on an orb.
- **Narrow bridges under smoothing.** S11 fills narrow pits to `PIT_DIP` under their rim at the
  user's request, which raises the ceiling over drops narrower than 3: a flyer can hang briefly
  over a 2-wide chasm at the rim's height. A 1-wide bridge over a drop still reads as the drop
  beneath it, since the opening shaves it, so flying low along one nudges the flyer down. Settle
  by whether either bothers anyone in play.
- **Water floor: a minimum, not a fixed height.** S12 keeps a flyer at least 8 over water, and the
  ceiling still allows up to 16. If "keeps the player 8 above the water" meant exactly 8, the
  floor becomes a band. Settle by playing it.
- **Orb loot tables.** Stronghold library, woodland mansion, End city treasure, ancient city, each at
  the existing 1%. The wings keep their current list. Settle by whether the orb turns up too early.
- **Death message wording.** "%1$s stopped pondering the orb" is a placeholder in the lore's voice.
- **Melt curve and boost numbers.** Every constant in "Reference: Tuning defaults". Settle after a
  play session; they all live in `WaxMelt` and `WingBoost`.
- **What the catcher swallows.** With an empty main hand mid-glide, a left-click hits the catcher
  (no punching), an offhand rocket is replaced by the free boost, and a fake barrier can be targeted
  to air-place a block (S3). Left-click could be forwarded to whatever kalic0re `common/aim`'s
  `Crosshair` finds; settle by whether anyone misses it.
- **Fence cost.** Every moving holder rescans the columns under its footprint each tick. A still
  one rescans once every 20 ticks, when its whole fence is also resent as one packet per cell.
  Measured 2026-09-27 with `scripts/loadtest/orb-fence.js` (spark, wall-clock sampling on the dev
  server). After S9, `AltitudeFence.update` costs:
  - 2 µs per hovering holder;
  - 27–36 µs per holder moving up to 1 block/tick;
  - 61 µs per holder at the 12-block lookahead cap;
  - 1.1 ms/tick for 18 holders all flying at 3 blocks/tick.

  About three quarters of it is block reads. That is well inside the tick budget at this server's
  scale. Revisit, most likely by caching ground heights between ticks since a moving footprint
  mostly overlaps the last one, only if dozens fly at once.

  A hundred at once, measured the same way at S18 over real terrain (`--terrain`, see
  `scripts/loadtest/README.md`): 26 µs per holder at 0.55 blocks/tick and 33 µs at 1.1, so 2.6 and
  3.3 ms/tick. Everything else the orb does added under 0.1 ms. That was 11 to 12% of a server
  thread busy 23 to 27 ms of its 50; vanilla's own handling of those hundred players' movement
  packets was 9 to 11 ms. It still scales linearly and still fits.
- **Altitude reference.** The melt reads absolute y in every dimension, so the Nether roof melts at
  twice the base rate and the End barely at all. Settle by whether that feels wrong in play.

## Reference: Tuning defaults

| Constant | Value | Class |
| --- | --- | --- |
| `artifacts:levitation_orb_flight_ceiling` | 16 (game rule, 0–384) | `orb/OrbGameRules` |
| `OVERHEIGHT_TRIGGER_CHECKS` | 3 (1 s apart) | `orb/AltitudeGuard` |
| `DESCENT_PROOF` | 0.5 blocks between checks | `orb/AltitudeGuard` |
| `LOOKAHEAD_TICKS` | 4, plus latency | `orb/AltitudeFence` |
| `MAX_LOOKAHEAD_BLOCKS` | 12 | `orb/AltitudeFence` |
| `SIDE_BUFFER` / `REAR_BUFFER` / `FORWARD_BUFFER` | 1 / 1 / 2 blocks | `orb/AltitudeFence` |
| `REFRESH_INTERVAL_TICKS` | 20, staggered by UUID | `orb/AltitudeFence` |
| `STILL_EPSILON` | 0.01 blocks | `orb/AltitudeFence` |
| `SMOOTHING_RADIUS` | 1 (shaves bumps under 3 wide) | `orb/HeightGrid` |
| `ROOM_FRACTION` | 0.25 of a shaved bump's height | `orb/HeightGrid` |
| `PIT_DIP` | 1 block under a filled pit's rim line | `orb/HeightGrid` |
| `NUDGE_SPEED` | 0.75 blocks/tick | `orb/AltitudeNudge` |
| `MARGIN` / `DEADBAND` | 0.15 / 0.1 blocks | `orb/AltitudeNudge` |
| `FLOOR_BAND` | 1 block over the water floor | `orb/AltitudeNudge` |
| `WATER_HEIGHT` | 6 blocks | `orb/WaterLift` |
| `CATCH_REACH` | 48 blocks | `orb/WaterLift` |
| `POINTS` | 20 (the orb's durability) | `orb/Wetness` |
| `SOAK_TICKS` / `SOAK_STEP_TICKS` | 140 / 7 (in water, dry to waterlogged; a point) | `orb/Wetness` |
| `DRY_TICKS` / `DRY_STEP_TICKS` | 12000 / 600 (out of water, while ticked; a point) | `orb/Wetness` |
| `DRIP_TICKS` | 60 × how soaked (wait out of a liquid) | `orb/LiquidGuard` |
| `SHORTEST_MEAN_TICKS` / `LONGEST_MEAN_TICKS` | 20 / 1200 (mean flight between failures) | `orb/Sputter` |
| Drying | furnace 200 ticks, campfire 600 ticks | `datagen/ArtifactsRecipeProvider` |
| Wings durability | elytra ÷ 2 = 216 | `waxwings/WaxWings` |
| `MELT_START_Y` | 64 | `waxwings/WaxMelt` |
| `MELT_BLOCKS_PER_STEP` | 64 | `waxwings/WaxMelt` |
| `COOLDOWN_TICKS` | 40 | `waxwings/WingBoost` |
| `WEAR` | 2 × multiplier | `waxwings/WingBoost` |
| Catcher `SIZE` | 4 × 4 blocks | `waxwings/BoostCatcher` |

## Reference: Fence geometry

A cell's height is `y - groundY - 1`: a cell resting on the ground is height 0. The numbers below
use the default flight ceiling of 16; since S13 it is a game rule. Flying players are always in `STANDING` pose (1.8 tall;
`Player.updatePlayerPose` only crouches when not flying), so feet in the height-16 cell can reach
0.8 into the height-18 cell. A barrier at height 18 holds the box top at that cell's floor, the feet
at most 0.2 into height 17's cell — still the height-16 block by `blockPosition()`. So the threshold
is the ceiling + 2 (`HeightGrid.forbiddenHeight`). Since S9 the altitude cutoff reads the same
cells: it trips when the body overlaps a forbidden cell in its centre column, so a player resting
against the ceiling, head in the height-17 cell, is never over the line.

The window-and-exception rule means barriers never appear below an overheight player's feet or
above their head in a column their box overlaps, never in their centre column below the head, and
never where the real block has collision or holds fluid. A barrier over a vine, torch or flower
hides it from that one player until the fence moves on; that is accepted over leaving a hole.

Smoothing (S8, reshaped in S9) works on one level at a time.

1. **Ground field.** At that level, each column's ground is the highest ground at or below it: the
   cell itself when solid, so an overhang is never taken for the floor.
2. **Opening.** A 3×3 erosion then dilation of that field shaves every bump narrower than 3×3 and
   leaves wider ones intact.
3. **Room.** The opened ground is raised by `ROOM_FRACTION` of the tallest bump shaved within
   `SMOOTHING_RADIUS`, counting only bumps that stand on measured ground.
4. **Pit fill (S11).** A 3×3 closing of the opened ground fills every pit narrower than 3 up to its
   rim. The line over it sits `PIT_DIP` under the rim's.
5. **Forbidden test.** A cell is forbidden when it is open, not under a filled pit's line, and
   either:
   - its height over the raised ground is `FORBIDDEN_HEIGHT` or more, or
   - its column has no ground within `WALK_DEPTH` (35, since S11; `MAX_DEPTH`, 26, before).

Before S11 a pit was never raised, so a 1-wide hole dropped a one-barrier stub from the ceiling.

**Ceiling only (S12).** Only forbidden cells over the head are fenced. The walls S11 softened are
gone, except over a void column. The ceiling over a drop therefore sits at the flyer's own height,
and `AltitudeNudge` brings them down to the column's line (`HeightGrid.ceilingLevel`, the pit fill
included). Over water it also holds their feet at least `WaterLift.WATER_HEIGHT` over the
surface.

The footprint is a rectangle along the heading, not the world axes:

- **Length.** It runs from `REAR_BUFFER` behind the box to `FORWARD_BUFFER` past the lookahead
  position.
- **Width.** It extends `SIDE_BUFFER` past the box on each side.
- **Placement.** A column gets barriers when its square overlaps the rectangle. The grid reaches
  `REACH` = 4 columns past the rectangle's bounding box (3 before S11), but only columns a queried
  cell's smoothing touches are ever read.
