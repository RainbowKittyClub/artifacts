package club.rainbowkitty.artifacts.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import net.minecraft.core.HolderLookup;

import club.rainbowkitty.artifacts.Talaria;
import club.rainbowkitty.artifacts.WaxWings;

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
        builder.add("death.attack.artifacts.icarus", "%1$s flew too close to the sun");
    }
}
