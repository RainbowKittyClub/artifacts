package club.rainbowkitty.artifacts;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.rainbowkitty.kalic0re.common.effect.PlayerEffectDispatcher;

/** Mod entrypoint; registers every artifact's item and effect. */
public class Main implements ModInitializer {
    public static final String MOD_ID = "artifacts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Talaria.init();
        WaxWings.init();
        Shadow.init();
        ArtifactsLoot.init();
        PlayerEffectDispatcher.init();

        // Not markAsRequired(): every artifact declares a vanilla fallback item, so a client
        // without the pack still sees a sensible boot/elytra rather than a missing model.
        PolymerResourcePackUtils.addModAssets(MOD_ID);

        LOGGER.info("Artifacts initialized!");
    }
}
