// Serves only kalic0re's hidden-model renderers, which have no 26.3 port.
//? if <26.3 {
package net.minecraft.client.renderer.rendertype;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Reaches {@link RenderType#create}, which is package-private, on behalf of kalic0re's shared
 * {@code HiddenEquipmentRendering}.
 *
 * <p>Squatting in a vanilla package is the only way to call that method, and a squatting class is
 * the one piece of this that cannot be shared: kalic0re's code is relocated per consumer, but a
 * {@code net.minecraft.*} class would be copied verbatim into every consumer jar under the same
 * name. So each mod keeps its own mod-named copy of this shim, and nothing else.
 */
@Environment(EnvType.CLIENT)
public final class ArtifactsRenderTypeFactory {
    private ArtifactsRenderTypeFactory() {
    }

    /** Calls the package-private {@code RenderType.create}. */
    public static RenderType create(String name, RenderSetup setup) {
        return RenderType.create(name, setup);
    }
}
//?}
