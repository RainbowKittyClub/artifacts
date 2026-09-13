# Shadow's vanilla-client fallback

A resource-pack-only approximation of `Shadow`'s hide-model effect for a held tool or weapon, so a
client with no mod installed — only the server's Polymer resource pack, which every joining client
already receives — sees *something* change when a weapon or tool is Shadow-enchanted, instead of
nothing at all. This is a design sketch, not a build: nothing below has been written or tested.

Every claim about vanilla's item-model system was read directly out of
`libs/decompiledjava/minecraft-26.2`, cited by file. The armor half was ruled out by reading
`EquipmentClientInfo` itself, not inferred from the held-item finding.

**Stage order.** One stage (S1), independent of everything else in this mod: it only touches item
model JSON for `Talaria`/`WaxWings`' Polymer fallback items when Shadow is applied, alongside the
mixin-based rendering `Shadow` already has. It does not replace the mixin path for a client that
does run this mod — that client keeps seeing the real hidden-model-plus-glint effect regardless of
whether S1 is ever built.

## Findings that reshape the work

**Worn armor has no equivalent — `EquipmentClientInfo`'s layer list is static per equipment asset,
with no per-stack condition.** `EquipmentClientInfo.Layer` (`EquipmentClientInfo.java:84-111`) is
just a texture id plus a `dyeable`/`usePlayerTexture` flag; `EquipmentClientInfo.Builder` assembles
a fixed `List<Layer>` per `LayerType` at asset-load time, with nothing in the codec that reads the
wearer's `ItemStack` at render time. The worn-equipment texture an asset id resolves to cannot
branch on whether the piece is enchanted — that decision either has to happen before the asset id is
chosen (which `Equippable.setAsset` does not support per-stack either — it is a fixed
`ResourceKey<EquipmentAsset>` set once, at item construction) or in code, which is exactly what
`EquipmentLayerRendererMixin` already does for a client running this mod. So there is no resource-
pack-only path for armor at all — this finding is why S1 below covers only held tools/weapons, not a
placeholder for a `Talaria`/`WaxWings` case that also needs this. (Neither item is itself
Shadow-enchantable in a way that matters here — see `Shadow.SUPPORTED_ITEMS` — this finding is about
the general armor-slot case, not those two items specifically.)

**Held items have a real, vanilla, no-mixin hook: conditional item models.** The 1.21.2+ item model
system already ships exactly the branch armor lacks.
`ConditionalItemModel`/`ConditionalItemModel.Unbaked` (`ConditionalItemModel.java:23-101`) picks
between two child `ItemModel`s based on an `ItemModelPropertyTest` read fresh off the `ItemStack` at
resolve time (`update`, `:44-46`) — this is the same system the mace's "is broken" texture and the
compass's needle direction already use, just with a different property. `ConditionalItemModelProperties.bootstrap()`
(`ConditionalItemModelProperties.java:15-29`) registers the property types a `"property"` field can
name; `ComponentMatches` (`ComponentMatches.java:15-31`, registered as `minecraft:component`) tests
an arbitrary `DataComponentPredicate.Single<?>` against `itemStack.has`-style matching
(`predicate.predicate().matches(itemStack)`), which is general enough to ask "does this stack carry
enchantment X specifically" rather than merely "does it carry any enchantment at all" — the latter is
all `HasComponent` (`HasComponent.java:17-37`, property `minecraft:has_component`) can answer, since
it only checks `itemStack.has(componentType)`/`hasNonDefault`, with no way to name a specific
enchantment. `DataComponentPredicates.ENCHANTMENTS` (`DataComponentPredicates.java:9-11`) registers
exactly the predicate type this needs, backed by `EnchantmentsPredicate.Enchantments`
(`EnchantmentsPredicate.java:45-56`), a list of `EnchantmentPredicate`
(`EnchantmentPredicate.java:14-18`) — each one an optional `HolderSet<Enchantment>` (a single id, a
list, or a tag) plus an optional level range, so `{"enchantments": "artifacts:shadow"}` alone is a
complete "carries Shadow, any level" predicate.

**The resulting JSON shape, worked through each codec's field names rather than guessed:**
`ConditionalItemModel.Unbaked`'s codec (`:52-60`) contributes `"type": "minecraft:condition"`,
`"property"` (the dispatch key, flattened in because `ConditionalItemModelProperties.MAP_CODEC` is a
`MapCodec` merged via `i.group(...)`, not a nested field), `"on_true"`, `"on_false"`. `ComponentMatches.MAP_CODEC`
(`:16-18`) contributes one more flattened field, `"predicate"`, itself an object whose own shape comes
from `DataComponentPredicate.singleCodec("predicate")` (`DataComponentPredicate.java:32-34`) — a
dispatch on a field also (confusingly) named `"predicate"`, holding the predicate type id
(`"minecraft:enchantments"`), merged with that type's own `"value"` field
(`DataComponentPredicate.Single.wrapCodec`, `:66-73`) holding the `EnchantmentPredicate` list. Fully
assembled:

```json
{
  "model": {
    "type": "minecraft:condition",
    "property": "minecraft:component",
    "predicate": {
      "predicate": "minecraft:enchantments",
      "value": [ { "enchantments": "artifacts:shadow" } ]
    },
    "on_true": { "type": "minecraft:model", "model": "artifacts:item/empty" },
    "on_false": { "type": "minecraft:model", "model": "artifacts:item/talaria" }
  }
}
```

This is not cross-checked against a real vanilla data file using this exact combination — the field
names are read straight from the codecs above, not copied from an example — so verify it parses
before trusting it further.

**This fallback cannot carry the glint, for the same reason `HIDDEN_GLINT` needs a mixin at all —
see `enchant-framework.md` S7, not re-derived here.** `on_true` picking an empty model
(`"minecraft:model"` with no elements, or no `elements` key) submits no quads, so there is nothing
for the enchantment foil pass to draw over — S7's own finding is that the glint pass reuses the main
pass's quads and depth, and a discarded/absent fragment writes neither. The achievable fallback is
therefore `HiddenEquipmentStyle.HIDDEN` (no mesh, no glint), never `HIDDEN_GLINT` — a real client
running this mod's mixins still sees the richer effect; only a vanilla client sees the cheaper one.

## How these stages get tested

Not on this workspace's dev client — `runClient` always loads this mod's own client mixins, so it
can never observe what a client *without* them sees. The only real test is a genuinely vanilla
client (or one with an unrelated, uninstalled-here mod set) connecting to a server that has this
resource pack loaded, confirming the enchanted weapon/tool goes blank in hand while an
identically-enchanted one stays untouched in the inventory `GUI` context (per house convention,
"never hide in `GUI`" already governs `HiddenItemRendering.styleFor`, and any resource-pack
equivalent should keep that same carve-out even though nothing here builds it yet).

## S1 — Held-item conditional model, in the resource pack — **planned**

**Build.** For each item in `Shadow.SUPPORTED_ITEMS` that is a tool/weapon rather than armor (the
armor members get no fallback at all, per the Findings above), replace its plain
`assets/artifacts/items/<item>.json` with a `minecraft:condition` model in the shape worked out
above, `on_false` pointing at the item's existing real model and `on_true` at a new empty model.
Vanilla items (e.g. `Items.SHEARS`, also in the tag) cannot receive this treatment at all: their
model lives in the base game's own data, not this mod's pack, and this mod has no
vanilla-item-model-override mechanism today.

The mechanism itself now lives in `rkcore` — `common/enchant/HiddenItemModelFallback` and
`common/enchant/datagen/HiddenItemModelProvider`, wired in via
`EnchantDatagen.addHiddenItemModelProvider` (`enchant-framework.md` S8, done) — so this stage is
Java-free on this mod's side: declare one `HiddenItemModelFallback` per `Talaria`/`WaxWings`-style
tool, call the new `EnchantDatagen` method from `ArtifactsDataGenerator`, no hand-authored JSON.
That provider needs client-environment typed builders, which requires adding
`fabricApi { configureDataGeneration { client = true } }` to this mod's own `build.gradle` first —
not yet done, and a real change to how this mod's datagen boots (a full client instance instead of a
server instance) for every future run, not just this feature, so it is called out rather than made
silently alongside this doc update.

**Done when.** Per the testing section above: a plain client with no mod, connected to a server
running this pack, sees a Shadow-enchanted `artifacts:talaria`/`artifacts:wax_wings` (or any other
in-pack tool this mod ships later) go blank in hand, while the same stack's inventory icon is
unaffected and an un-enchanted or differently-enchanted stack renders normally.

## Open decisions

**Whether shipping a visibly weaker fallback is worth it at all.** A vanilla client would see
"blank in hand, glinting normally would have looked shadowed" — a real but different effect from
what a modded client sees, not a strictly worse version of the same one. Whether that divergence
reads as a feature or a bug to a player switching between the two client types is a product call,
not something this research resolves — flagged rather than assumed.

**Whether this belongs in `rkcore`'s `common/enchant` instead of staying artifacts-only — decided,
folded in.** Moved to `rkcore` as `enchant-framework.md` S8: `HiddenItemModelFallback` +
`HiddenItemModelProvider`, wired via `EnchantDatagen.addHiddenItemModelProvider`. Build and lint pass
in `rkcore` and it has been published to `mavenLocal`; `artifacts` does not consume it yet (see S1
above for the `build.gradle` prerequisite that's still outstanding).
