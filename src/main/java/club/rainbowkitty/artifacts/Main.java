package club.rainbowkitty.artifacts;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.rainbowkitty.rkcore.common.effect.PlayerEffectDispatcher;

/**
 * Mod entrypoint; registers every artifact's item and effect.
 *
 * <p>{@link Shadow} is deliberately not wired up — it is the one feature whose payoff is purely
 * client-side rendering, so it stays out of server deploys until it has an implementation that
 * works for a vanilla client.
 */
public class Main implements ModInitializer {
    public static final String MOD_ID = "artifacts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Talaria.init();
        WaxWings.init();
        ArtifactsLoot.init();
        PlayerEffectDispatcher.init();

        // Not markAsRequired(): every artifact declares a vanilla fallback item, so a client
        // without the pack still sees a sensible boot/elytra rather than a missing model.
        PolymerResourcePackUtils.addModAssets(MOD_ID);

        LOGGER.info("Artifacts initialized!");
    }
}
