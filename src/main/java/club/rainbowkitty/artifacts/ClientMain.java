package club.rainbowkitty.artifacts;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.renderer.rendertype.ArtifactsRenderTypeFactory;

import club.rainbowkitty.kalic0re.common.enchant.client.HiddenRendering;

/** Client entrypoint; builds the render types the hide-model mixins draw with. */
public class ClientMain implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HiddenRendering.init(Main.MOD_ID, ArtifactsRenderTypeFactory::create);
    }
}
