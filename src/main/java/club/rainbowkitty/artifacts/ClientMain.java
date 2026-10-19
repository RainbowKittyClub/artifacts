package club.rainbowkitty.artifacts;

import net.fabricmc.api.ClientModInitializer;

//? if <26.3
import net.minecraft.client.renderer.rendertype.ArtifactsRenderTypeFactory;

//? if <26.3
import club.rainbowkitty.kalic0re.common.enchant.client.HiddenRendering;

/** Client entrypoint; builds the render types the hide-model mixins draw with. */
public class ClientMain implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 26.3 has no port of kalic0re's hidden-model renderers, so Shadow hides nothing there.
        //? if <26.3
        HiddenRendering.init(Main.MOD_ID, ArtifactsRenderTypeFactory::create);
    }
}
