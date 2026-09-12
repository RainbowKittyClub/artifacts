package club.rainbowkitty.artifacts.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Exposes the layer array {@code ItemStackRenderState} keeps private, so
 * {@code ItemModelResolverMixin} can walk the layers a just-resolved item ended up with. Vanilla
 * only ever hands out {@code newLayer()} while building, and never a way to revisit what was built.
 */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    /** The backing array, which runs longer than the layers actually in use. */
    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] artifacts$layers();

    /** How much of {@link #artifacts$layers} the last resolve actually filled. */
    @Accessor("activeLayerCount")
    int artifacts$activeLayerCount();
}
