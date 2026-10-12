package club.rainbowkitty.artifacts.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import club.rainbowkitty.artifacts.Main;
import club.rainbowkitty.artifacts.orb.OrbOfLevitation;

/**
 * Drying a waterlogged Orb of Levitation back into a working one, in a furnace or on a campfire,
 * at vanilla's own cook times for each. The artifacts themselves have no recipes: they are loot.
 */
public final class ArtifactsRecipeProvider extends FabricRecipeProvider {
    private static final int FURNACE_TICKS = 200;
    private static final int CAMPFIRE_TICKS = 600;

    public ArtifactsRecipeProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries,
            RecipeOutput output) {
        return new RecipeProvider(registries, output) {
            @Override
            public void buildRecipes() {
                Ingredient wet = Ingredient.of(OrbOfLevitation.WATERLOGGED_ITEM);
                String unlock = getHasName(OrbOfLevitation.WATERLOGGED_ITEM);
                SimpleCookingRecipeBuilder.smelting(wet, RecipeCategory.MISC,
                                CookingBookCategory.MISC, OrbOfLevitation.ITEM, 0.0F, FURNACE_TICKS)
                        .group("orb_of_levitation_drying")
                        .unlockedBy(unlock, this.has(OrbOfLevitation.WATERLOGGED_ITEM))
                        .save(this.output, key("orb_of_levitation_from_smelting"));
                SimpleCookingRecipeBuilder.campfireCooking(wet, RecipeCategory.MISC,
                                OrbOfLevitation.ITEM, 0.0F, CAMPFIRE_TICKS)
                        .group("orb_of_levitation_drying")
                        .unlockedBy(unlock, this.has(OrbOfLevitation.WATERLOGGED_ITEM))
                        .save(this.output, key("orb_of_levitation_from_campfire_cooking"));
            }
        };
    }

    @Override
    public String getName() {
        return "Artifacts Recipes";
    }

    // A recipe id under the mod's namespace.
    private static ResourceKey<Recipe<?>> key(String path) {
        return ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(Main.MOD_ID, path));
    }
}
