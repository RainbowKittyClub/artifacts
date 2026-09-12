package club.rainbowkitty.artifacts.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;

import club.rainbowkitty.artifacts.Talaria;
import club.rainbowkitty.artifacts.WaxWings;

/**
 * Puts every wearable artifact into the per-slot and general {@code enchantable/*} item tags, so
 * vanilla armor enchantments can actually target them at the enchanting table.
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
    }

    private static ResourceKey<Item> key(Item item) {
        return BuiltInRegistries.ITEM.getResourceKey(item)
                .orElseThrow(() -> new IllegalStateException("Unregistered item: " + item));
    }
}
