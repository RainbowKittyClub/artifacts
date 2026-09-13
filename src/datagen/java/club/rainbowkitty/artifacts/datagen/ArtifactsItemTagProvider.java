package club.rainbowkitty.artifacts.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import club.rainbowkitty.artifacts.Shadow;
import club.rainbowkitty.artifacts.Talaria;
import club.rainbowkitty.artifacts.WaxWings;

/**
 * Puts every wearable artifact into the per-slot and general {@code enchantable/*} item tags, so
 * vanilla armor enchantments can actually target them at the enchanting table, and populates
 * {@link Shadow#SUPPORTED_ITEMS}.
 */
public final class ArtifactsItemTagProvider extends FabricTagsProvider.ItemTagsProvider {
    public ArtifactsItemTagProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        ResourceKey<Item> talaria = key(Talaria.ITEM);
        ResourceKey<Item> waxWings = key(WaxWings.ITEM);

        tag(ItemTags.ARMOR_ENCHANTABLE).add(talaria, waxWings);
        tag(ItemTags.FOOT_ARMOR_ENCHANTABLE).add(talaria);
        tag(ItemTags.CHEST_ARMOR_ENCHANTABLE).add(waxWings);

        // addOptionalTag, not addTag: this run's own registries snapshot predates the base game's
        // vanilla-shipped item tags the same way EnchantTagProvider's does for enchantments (see
        // its comment) -- required=false only weakens datagen-time validation, not the real tag at
        // runtime, where every referenced vanilla tag genuinely exists.
        tag(Shadow.SUPPORTED_ITEMS)
                .addOptionalTag(ItemTags.HEAD_ARMOR)
                .addOptionalTag(ItemTags.CHEST_ARMOR)
                .addOptionalTag(ItemTags.LEG_ARMOR)
                .addOptionalTag(ItemTags.FOOT_ARMOR)
                .addOptionalTag(ItemTags.SWORDS)
                .addOptionalTag(ItemTags.AXES)
                .addOptionalTag(ItemTags.PICKAXES)
                .addOptionalTag(ItemTags.SHOVELS)
                .addOptionalTag(ItemTags.HOES)
                .add(talaria, waxWings, key(Items.SHEARS));
    }

    private static ResourceKey<Item> key(Item item) {
        return BuiltInRegistries.ITEM.getResourceKey(item)
                .orElseThrow(() -> new IllegalStateException("Unregistered item: " + item));
    }
}
