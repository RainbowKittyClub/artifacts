# Wax Wings package split

Splits the single 392-line `club.rainbowkitty.artifacts.WaxWings` class into a
`club.rainbowkitty.artifacts.waxwings` package of five single-concern files, with one real
(rare, edge-case) behavior fix and one comment/doc-drift fix surfaced while reading the code
closely (see "Findings that reshape the work").

Since superseded in part: `docs/plan/orb-and-glider-wings.md` moved these classes to
`club.rainbowkitty.artifacts.orb`, where they serve the Orb of Levitation, and made Wax Wings an
elytra. The reference appendix below still describes the flight mechanics, under the old names.

Every citation was checked directly against the pre-refactor `WaxWings.java`, its five callers,
`scripts/lint/checkstyle.xml`, `rkc/mods/artifacts/build.gradle`, and — for the vanilla
fall-damage and gamemode claims — the Minecraft 26.2 decompile at
`libs/decompiledjava/minecraft-26.2/`, then re-checked by an independent adversarial pass before
S1 began. S1 itself then surfaced a further correction to the staging approach (see its body
below). Citations in S2 onward are by symbol name, not line number, since each stage edits the
file before the next one starts.

**Stage order.** S1 moves `WaxWings.java` itself into the new `waxwings` package immediately —
originally slated for the final stage — because the extracted classes are package-private, and
Java's package-private access is per-exact-package, not hierarchical: a class left behind in the
parent `club.rainbowkitty.artifacts` package would have had zero access to same-named siblings
one level down, regardless of import. This was found while implementing S1, not during planning;
see S1's body for the detail. With the shell already moved, S2 (`AltitudeGuard`) and S3
(`ObstructionGuard`) are pure same-package extractions with no caller rewiring left to do; S4 is
now the lighter cleanup stage — promoting the nested `Hooks` class to a top-level
`WaxWingsHooks`, the datagen compile+lint gap, and the full in-world regression pass.

## Findings that reshape the work

**Finding 1 — the obstruction check's own comments already drifted from its code before this
refactor.** Three copies of the same description — the class Javadoc, the `OBSTRUCTED` field
comment, and the `isObstructed` method comment — say the withheld set is "a cobweb or sweet berry
bush," but the actual check also tests `state.is(Blocks.VINE)`. S3 is where `isObstructed` and
the `OBSTRUCTED` field relocate; that's the point to add "vine" to whatever survives of these
comments (the full narrative itself lives in the Reference appendix below) rather than carry the
drift forward. Separately, the `OBSTRUCTED` field comment names `Hooks#onTick` — after S4 that
class is `WaxWingsHooks`, so this reference needs updating too, along with a pre-existing
misindentation bug on that exact line.

**Finding 2 — every other flight-affecting check in the file exempts creative/spectator players
except one, and the gap is real but narrower than "no behavior change."** `checkObstruction`,
`checkAltitude`, and `updateFlyingSpeed` all skip when `player.isCreative() ||
player.isSpectator()`, but `Hooks.onDeactivate`'s fall-seed
(`FALLING.put(player.getUUID(), 0)` on `!player.onGround()`) did not. Verified against
`libs/decompiledjava/minecraft-26.2/net/minecraft/world/entity/player/Player.java:1443-1446`
(`causeFallDamage` returns early purely on `abilities.mayfly`) and
`.../world/level/GameType.java:67-71` (creative and spectator both force `mayfly = true`) that
this entry can never itself cause a spurious Icarus message *while the player stays in that
gamemode* — but the landing-scan only clears the entry once the player is grounded for several
ticks in a row, dies, logs out, or re-equips the wings; it does not clear on a `/gamemode`
change. So the actual, narrow, real bug: a creative or spectator player who unequips Wax Wings
mid-air, then `/gamemode survival`s while still airborne, then dies to fall damage, incorrectly
gets the `death.attack.artifacts.icarus` message despite never having used the wings' flight in
survival at all. Fix: give the fall-seed the same inline `isCreative() || isSpectator()` guard as
the other three checks (a fourth copy, not a shared helper — sharing one would put a compile
dependency on the facade/hooks class into classes built before that facade exists, in S1–S3).
**Applied in S1**, since that stage is what touches this exact line.

*(Checked and needs no action: `mixin/DeathMessageMixin.java:16` mentions `{@code WaxWings}` —
`{@code}`, not `{@link}`, and the class name doesn't change, so this and the four mentions in
`docs/plan/shadow-vanilla-fallback.md` stay accurate.)*

## How these stages get tested

Every stage's "Done when" is an in-world observation on the dev server, naming the specific
behavior it exercises, per `docs/TESTING.md`'s rule that a green build proves compilation, not
that the moved logic still fires in the right order or sequence. `scripts/devserver.sh
rkc/mods/artifacts restart` boots the server; `scripts/devserver.sh rkc/mods/artifacts mixins`
checks mixin application (expected output: the literal `None.`). `cd scripts/lint && ./gradlew
checkstyleArtifacts` is the lint gate `artifacts` is already wired into
(`scripts/lint/build.gradle:20`), and it only covers `src/main/java` — it does not reach the two
datagen files S4 also touches. A stage is not finished until its "Done when" is actually
observed, not merely written.

## S1 — FallTracker, FlightSpeed, and the package move — **done**

**Build.** Move `WaxWings.java` into `club.rainbowkitty.artifacts.waxwings` now (originally S4's
job — see the stage-order note above), adding `import club.rainbowkitty.artifacts.Main;` since a
subpackage gets no implicit access to `Main.MOD_ID`. Rewire all five callers immediately: add a
new `import club.rainbowkitty.artifacts.waxwings.WaxWings;` to `Main.java` and
`ArtifactsLoot.java` (both shared `WaxWings`'s old package with no existing import to update),
and correct the existing import path in `ArtifactsLangProvider.java`,
`ArtifactsItemTagProvider.java`, and `mixin/OverheightFallDamageMixin.java`. Shrink the
pre-refactor 49-line class Javadoc to a short pointer at the same time, since its `{@link}`
targets start moving out from under it this stage and the rest follow over S2–S3 — the full
narrative moves to the "Reference: Flight tracking mechanics" appendix below.

Create `waxwings/FallTracker.java`: a package-private `final class` with a private no-arg
constructor, carrying the `FALLING` map and `LANDING_GRACE_TICKS`, `clearLandedPlayers` renamed
`tick(MinecraftServer)`, `icarianDeathMessage`, `resolveCatch`, and new `markFalling(UUID)` /
`clear(UUID)` / `isFalling(UUID)` wrapping direct `FALLING` access. Each member gets a one-line
`//` descriptor per `docs/STYLE.md` (package-private access here follows the existing precedent
at `kalic0re/.../common/label/HoverLabel.java`).

Create `waxwings/FlightSpeed.java` the same way, carrying the three flying-speed constants,
`updateFlyingSpeed` renamed `update`, `isRainingOn`, and `resetFlyingSpeed` renamed `reset`.

In `WaxWings.java`: delete the moved fields/methods outright; change `init()`'s two registrations
to `FallTracker::tick` / `FallTracker::icarianDeathMessage`; change the `LEAVE` lambda's
`FALLING.remove(...)` to `FallTracker.clear(...)`; change `checkAltitude`'s two direct `FALLING`
accesses to `FallTracker.isFalling` / `FallTracker.markFalling` — not previously listed for S1,
but required immediately: `FALLING` no longer exists in this file once it moves, so
`checkAltitude` cannot compile without this change even though `checkAltitude` itself doesn't
relocate until S2; change `Hooks.onActivate`'s `FALLING.remove(...)` to `FallTracker.clear(...)`;
change `Hooks.onTick`'s `updateFlyingSpeed`/`resolveCatch` calls to `FlightSpeed.update` /
`FallTracker.resolveCatch`; change `Hooks.onDeactivate`'s `resetFlyingSpeed` call to
`FlightSpeed.reset`, and apply Finding 2's guard to its fall-seed line now, since this is the
stage touching it. `checkObstruction` and `checkAltitude`'s own bodies are otherwise untouched.

**Done when.** `cd rkc/mods/artifacts && ./gradlew build` succeeds, then on the dev server: flying
during rain shows the reduced speed (and full speed the moment rain stops or the player lands),
and unequipping Wax Wings mid-air followed by fall damage still shows the
`death.attack.artifacts.icarus` death message.

Build, lint, and both in-world checks (rain speed, Icarus message after mid-air unequip) are
confirmed.

## S2 — AltitudeGuard — **done**

**Build.** Create `waxwings/AltitudeGuard.java`, same conventions as S1, carrying
`OVERHEIGHT_STREAK`, `FLIGHT_CHECK_INTERVAL_TICKS`, `OVERHEIGHT_TRIGGER_CHECKS`, `MAX_SAFE_HEIGHT`,
`checkAltitude` renamed `check` (rewritten to call `FallTracker.isFalling`/`FallTracker.markFalling`
in place of direct `FALLING` access — already the shape it's in after S1), `heightAboveGround`,
`isGround`, and a new `clear(UUID)` wrapping `OVERHEIGHT_STREAK.remove`.

In `WaxWings.java`: delete the moved code; change `Hooks.onTick`'s `checkAltitude(player)` call to
`AltitudeGuard.check(player)`. Same early-rewiring gap as S1's `checkAltitude` finding: `init()`'s
`LEAVE` lambda and `checkObstruction`'s obstruction-transition branch both call
`OVERHEIGHT_STREAK.remove(...)` directly, and `checkObstruction` itself doesn't move until S3 — so
both call sites need `AltitudeGuard.clear(...)` now, this stage, since the field they reach into
stops existing in this file the moment it moves.

**Done when.** `./gradlew build` succeeds, then on the dev server: flying above 60 blocks for
roughly 10 seconds cuts `flying` (the player starts falling) while `mayfly` stays granted (an
immediate double-jump catch works), and dropping back under 60 blocks even once resets the streak
(confirmed by hovering just under/over the line and seeing the cutoff not fire early).

## S3 — ObstructionGuard — **done**

**Build.** Create `waxwings/ObstructionGuard.java`, same conventions, carrying `OBSTRUCTED` and
`OBSTRUCTION_BOX_EPSILON`, `checkObstruction` renamed `check` (rewritten to call
`AltitudeGuard.clear` in place of the inline `OVERHEIGHT_STREAK.remove` on the obstruction
transition), `isObstructed`, and new `isWithheld(UUID)` / `clear(UUID)` entry points wrapping
`OBSTRUCTED.contains`/`.remove`. Apply Finding 1's vine correction and the `Hooks` →
`WaxWingsHooks` reference fix to whatever one-line descriptor comment survives on this
package-private class — the full multi-paragraph narrative these comments summarize does **not**
move here; it lives in the "Reference" appendix below.

In `WaxWings.java`: delete the moved code; change `Hooks.onTick`'s `checkObstruction(player)` call
to `ObstructionGuard.check(player)`; change the "defend the grant" condition's
`!OBSTRUCTED.contains(player.getUUID())` to `!ObstructionGuard.isWithheld(player.getUUID())`;
change `Hooks.onActivate`/`onDeactivate`'s `OBSTRUCTED.remove(player.getUUID())` calls to
`ObstructionGuard.clear(player.getUUID())`.

**Done when.** `./gradlew build` succeeds, then on the dev server: flying into water, lava, fire,
a cobweb, a sweet berry bush, or a vine instantly revokes `mayfly` (an in-progress flight attempt
from inside one of these is also blocked, not just cut once already flying), and leaving it
restores `mayfly` on the next tick without needing to re-equip.

## S4 — Promote WaxWingsHooks, datagen lint, final pass — **done**

**Build.** By this point `WaxWings.java` holds only `ID`/`ASSET`/`ITEM`/`EFFECT`/`init`/
`isUnprotectedFall`/`lore` plus registration and pure delegation calls — no raw map/set access
remains. Promote the nested `Hooks` class to a top-level `waxwings/WaxWingsHooks.java`,
package-private, same one-line-descriptor style as the other classes in this package (it can stay
package-private, constructed once from `WaxWings`).

Compile and lint the two datagen files that reference `WaxWings` —
`./gradlew checkstyleArtifacts` (`scripts/lint/build.gradle:20`) only reaches `src/main/java`, and
`./gradlew build` does not compile `src/datagen/java` at all (`build.gradle:280-291`:
`generateAssets`/`runDatagen` are deliberately not wired into `build`). Run the datagen source
set's own compile task (Gradle's standard `compile<SourceSet>Java` naming implies
`compileDatagenJava`, but this hasn't been confirmed by running `./gradlew tasks` in this
environment — check the actual task name first) and a second `checkstyleTarget
-PtargetDir=../../rkc/mods/artifacts/src/datagen/java` from `scripts/lint`.

**Done when.** `./gradlew build`, the datagen compile task, and both lint passes all succeed;
`scripts/devserver.sh rkc/mods/artifacts restart` boots and `scripts/devserver.sh
rkc/mods/artifacts mixins` prints `None.`; and an in-world pass names each of: landing safely after
a normal fall (no Icarus message), catching yourself mid-air after an altitude cutoff (no Icarus
message on a later unrelated death), re-equipping the wings clearing all tracked state, a
`/gamemode` round-trip correctly re-defending the mayfly grant, taking fall damage while wearing
the wings but not actively flying (the `OverheightFallDamageMixin` contract), and Finding 2's exact
repro (unequip mid-air in creative, `/gamemode survival` while still airborne, take fall damage,
confirm no Icarus message). Also run the jar-level check `docs/TESTING.md` calls the only real
test of the Shadow-relocation arrangement: `unzip -l build/libs/artifacts-<version>.jar | grep
shaded` (name the shadowJar output exactly — `build/libs/` also holds a `-sources.jar` and an
`-unshaded.jar`, and `unzip -l` silently treats extra glob matches as name filters into the first
archive instead of listing multiple archives, so a wildcard here quietly checks nothing) plus a
`javap` check on one relocated call site.

## Reference: Flight tracking mechanics

Durable design rationale, condensed from `WaxWings.java`'s pre-refactor 49-line class Javadoc,
with its "vine" omission (Finding 1) corrected and its cross-references updated to the post-split
class names, since a package-private class doesn't carry a Javadoc block long enough to hold this
and `{@link}` targets across five files rot silently (nothing in `./gradlew build` or Checkstyle
validates them).

Wax Wings grants creative-like flight while worn, through kalic0re's `common/effect` framework and
its `PlayerFlight` helper — the grant holds only while the effect dispatcher sees the wings
equipped, and is released the tick they come off. Clients without the server's resource pack see a
vanilla Elytra; all art is still placeholder.

A player who loses the wings while airborne is tracked by `FallTracker` until they either land
safely (`FallTracker.tick`'s scan clears the entry once `onGround` has held for
`LANDING_GRACE_TICKS` in a row — a single grounded tick isn't enough, since that's also the tick a
fatal landing itself reports `onGround`) or regain flight (cleared by `FallTracker.resolveCatch`);
dying to fall damage in between gets the themed `death.attack.artifacts.icarus` message via
kalic0re's `common/death` registry, since the actual redirect mixin can't itself be shared code
(`mixin/DeathMessageMixin`).

`ObstructionGuard.check` runs every tick regardless of whether the player is currently flying:
whenever they're in any liquid, on fire, or any part of their own volume overlaps a cobweb, sweet
berry bush, or vine, both `flying` and `mayfly` are withheld immediately (no streak — there's no
measurement noise to tolerate the way altitude has), restored only once a later tick finds them
clear of it. Withholding `mayfly` itself, not just active flight, is deliberate: it's what stops a
launch from inside the obstruction, not only an in-progress one. `AltitudeGuard.check` is the
separate, gentler cutoff: throttled to once every `FLIGHT_CHECK_INTERVAL_TICKS` and only evaluated
while actually flying, it cuts just `flying` (leaving `mayfly` granted for a quick catch) after
`OVERHEIGHT_TRIGGER_CHECKS` consecutive checks reading the player more than `MAX_SAFE_HEIGHT`
blocks above solid ground (leaves and scaffolding don't count as ground; dropping back under it for
even one check resets that streak).

Vanilla's no-fall-damage short-circuit (`Player.causeFallDamage` returns early whenever `mayfly` is
true, at all, regardless of `flying`) is still in the way — that short-circuit is why the wings
would otherwise give total fall immunity merely by being worn. `mixin/OverheightFallDamageMixin`
corrects the rule itself instead of special-casing either cutoff: for a wings wearer specifically,
it reports `mayfly` as false whenever `WaxWings.isUnprotectedFall` says they aren't currently
flying, live at the moment vanilla asks. Catching themselves before landing is protected again for
free the same way. `AltitudeGuard.check` additionally seeds `FallTracker` on its own cutoff
specifically (not an obstruction one — "flew too close to the sun" wouldn't fit), purely so the
death message reads as Icarus, same as an unequip mid-air.

`FlightSpeed.update` sets flying speed to `FLYING_SPEED_MULTIPLIER` of vanilla's own default,
further cut to `RAIN_FLYING_SPEED_MULTIPLIER` of that while raining on the player, every tick so a
storm rolling in or clearing mid-flight takes effect immediately. `FlightSpeed.reset` restores
vanilla's value on unequip, since nothing else ever would.
