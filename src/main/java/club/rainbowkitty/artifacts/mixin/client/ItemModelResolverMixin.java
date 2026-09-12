package club.rainbowkitty.artifacts.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import club.rainbowkitty.rkcore.common.enchant.HiddenEquipmentStyle;
import club.rainbowkitty.rkcore.common.enchant.client.HiddenItemRendering;

/**
 * Rewrites a just-resolved item's layers onto rkcore's hidden render types when the stack carries a
 * hide-model enchantment. {@code updateForTopItem} is the one place that both holds the
 * {@link ItemStack} and has finished populating the {@link ItemStackRenderState}, which is what
 * makes it the hook: by the time {@code ItemFeatureRenderer} draws, the stack is long gone and only
 * the quads remain, so the decision has to be baked into the quads here.
 *
 * <p>Deliberately {@code updateForTopItem} and not {@code appendItemLayers}: the latter also runs
 * for nested sub-models (a bundle's contents, say), where hiding the container's own enchantment
 * should not reach through to whatever it is holding.
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void artifacts$hideItemModel(ItemStackRenderState output, ItemStack item,
            ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed,
            CallbackInfo callbackInfo) {
        HiddenEquipmentStyle style = HiddenItemRendering.styleFor(item, displayContext);
        if (style == null) {
            return;
        }
        ItemStackRenderStateAccessor layers = (ItemStackRenderStateAccessor)output;
        int count = layers.artifacts$activeLayerCount();
        for (int i = 0; i < count; i++) {
            HiddenItemRendering.hideLayer(layers.artifacts$layers()[i], style);
        }
    }
}
