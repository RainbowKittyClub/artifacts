package club.rainbowkitty.artifacts.datagen;

import java.util.List;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

import club.rainbowkitty.artifacts.Shadow;
import club.rainbowkitty.kalic0re.common.enchant.datagen.EnchantDatagen;

/** Datagen entrypoint: item enchantability tags, enchantment json/tag, and lang. */
public final class ArtifactsDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(ArtifactsItemTagProvider::new);
        pack.addProvider(ArtifactsLangProvider::new);
        EnchantDatagen.addProviders(pack, List.of(Shadow.SPEC));
    }
}
