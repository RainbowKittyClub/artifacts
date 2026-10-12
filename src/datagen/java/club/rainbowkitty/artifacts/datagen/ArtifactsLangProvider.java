package club.rainbowkitty.artifacts.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import net.minecraft.core.HolderLookup;

import club.rainbowkitty.artifacts.Talaria;
import club.rainbowkitty.artifacts.orb.OrbOfLevitation;
import club.rainbowkitty.artifacts.waxwings.WaxWings;

/** English names for every artifact item. */
public final class ArtifactsLangProvider extends FabricLanguageProvider {
    public ArtifactsLangProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider registries, TranslationBuilder builder) {
        builder.add(Talaria.ITEM, "Talaria");
        builder.add(WaxWings.ITEM, "Wax Wings");
        builder.add(OrbOfLevitation.ITEM, "Orb of Levitation");
        builder.add(OrbOfLevitation.WATERLOGGED_ITEM, "Waterlogged Orb of Levitation");
        builder.add("enchantment.artifacts.shadow", "Shadow");
        builder.add("death.attack.artifacts.icarus", "%1$s flew too close to the sun");
        builder.add("death.attack.artifacts.orb_of_levitation", "%1$s stopped pondering the orb");
        builder.add("gamerule.artifacts.levitation_orb_flight_ceiling",
                "Levitation Orb Flight Ceiling");
        builder.add("gamerule.artifacts.levitation_orb_flight_ceiling.description",
                "Blocks of air an Orb of Levitation holder may fly above the ground");
    }
}
