package club.rainbowkitty.artifacts;

import java.util.Set;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import club.rainbowkitty.artifacts.orb.OrbOfLevitation;
import club.rainbowkitty.artifacts.waxwings.WaxWings;

/**
 * Seeds every artifact into vanilla structure chests as their only obtainable source — there is no
 * recipe, so a run through the listed structures is the whole acquisition path.
 *
 * <p>Each item gets its own one-roll pool gated on a {@value #CHANCE} random chance, added to a
 * short thematically-chosen list of treasure tables rather than every {@code chests/*} table: the
 * per-chest odds stay tiny, and the per-structure odds stay bounded by how many chests that
 * structure actually rolls (a desert pyramid has four, so ~4% of pyramids hold Talaria).
 */
public final class ArtifactsLoot {
    /** Per-chest drop chance, applied independently to each table below. */
    private static final float CHANCE = 0.01F;

    // Hermes' sandals: overland structures whose theme is travel, depth or old civilisation.
    private static final Set<ResourceKey<LootTable>> TALARIA_CHESTS = Set.of(
            BuiltInLootTables.DESERT_PYRAMID,
            BuiltInLootTables.JUNGLE_TEMPLE,
            BuiltInLootTables.STRONGHOLD_CORRIDOR,
            BuiltInLootTables.WOODLAND_MANSION,
            BuiltInLootTables.ANCIENT_CITY,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_UNIQUE);

    // Icarus' wings: structures you already need flight, or a long fall, to reach or survive.
    private static final Set<ResourceKey<LootTable>> WAX_WINGS_CHESTS = Set.of(
            BuiltInLootTables.END_CITY_TREASURE,
            BuiltInLootTables.BASTION_TREASURE,
            BuiltInLootTables.NETHER_BRIDGE,
            BuiltInLootTables.WOODLAND_MANSION,
            BuiltInLootTables.ANCIENT_CITY,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE);

    // An orb to ponder: libraries, spellcasters, and the shulkers levitation comes from.
    private static final Set<ResourceKey<LootTable>> ORB_CHESTS = Set.of(
            BuiltInLootTables.STRONGHOLD_LIBRARY,
            BuiltInLootTables.WOODLAND_MANSION,
            BuiltInLootTables.END_CITY_TREASURE,
            BuiltInLootTables.ANCIENT_CITY);

    private ArtifactsLoot() {
    }

    /** Registers the loot-table modifier; call once from {@link Main#onInitialize}. */
    public static void init() {
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (TALARIA_CHESTS.contains(key)) {
                table.withPool(rarePool(Talaria.ITEM));
            }
            if (WAX_WINGS_CHESTS.contains(key)) {
                table.withPool(rarePool(WaxWings.ITEM));
            }
            if (ORB_CHESTS.contains(key)) {
                table.withPool(rarePool(OrbOfLevitation.ITEM));
            }
        });
    }

    // The condition sits on the pool, not the entry, so a failed roll yields nothing at all rather
    // than falling through to some other entry in the same pool.
    private static LootPool.Builder rarePool(Item item) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(LootItemRandomChanceCondition.randomChance(CHANCE))
                .add(LootItem.lootTableItem(item));
    }
}
