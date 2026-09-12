package club.rainbowkitty.artifacts.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Datagen entrypoint: item enchantability tags and lang.
 *
 * <p>{@link club.rainbowkitty.artifacts.Shadow}'s enchantment json and supported-items tag are not
 * generated while the enchantment is unwired — shipping them would put an enchantment in the
 * table's pool that does nothing on a vanilla client.
 */
public final class ArtifactsDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(ArtifactsItemTagProvider::new);
        pack.addProvider(ArtifactsLangProvider::new);
    }
}
