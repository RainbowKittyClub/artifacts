package club.rainbowkitty.artifacts.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;

import club.rainbowkitty.rkcore.common.enchant.client.HiddenEquipmentRendering;

/**
 * Hands every render decision inside {@code EquipmentLayerRenderer#renderLayers} to rkcore's
 * {@link HiddenEquipmentRendering}, which knows how each {@code HiddenEquipmentStyle} wants a
 * flagged stack drawn. This class holds only the two hook points, since mixins are never shared
 * code. {@code EquipmentLayerRenderer} is the one choke point every vanilla armor-wearing entity
 * renderer (humanoid, wolf, wither) funnels through, so these two cover all of them.
 *
 * <p>Two redirects because the two questions need different information: which pipeline the plain
 * layer draws with needs the layer's texture, which only the {@code armorCutoutNoCull} call site
 * has; whether a given draw happens at all needs the resulting {@link RenderType}, which only the
 * {@code submitModel} call site has.
 */
@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
    // The 11-arg renderLayers overload that actually holds the loop; the 8-arg one just delegates
    // into it. Descriptors are hand-verified against the deobfuscated jar (javap), not remapped —
    // this workspace ships no refmap.
    private static final String RENDER_LAYERS = "renderLayers("
            + "Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;"
            + "Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "ILnet/minecraft/resources/Identifier;II)V";

    private static final String ARMOR_CUTOUT_NO_CULL =
            "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull("
            + "Lnet/minecraft/resources/Identifier;"
            + ")Lnet/minecraft/client/renderer/rendertype/RenderType;";

    private static final String SUBMIT_MODEL =
            "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
            + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/rendertype/RenderType;III"
            + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;I"
            + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";

    // Redirecting a call does not rewrite the same call made from the handler, so asking vanilla
    // for its own render type here is safe, not recursive.
    @Redirect(method = RENDER_LAYERS, at = @At(value = "INVOKE", target = ARMOR_CUTOUT_NO_CULL))
    private RenderType artifacts$armorLayerType(Identifier texture,
            EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> equipmentAssetId,
            Model model, Object state, ItemStack itemStack, PoseStack poseStack,
            SubmitNodeCollector collector, int lightCoords, Identifier playerTextureOverride,
            int outlineColor, int order) {
        return HiddenEquipmentRendering.armorLayerFor(itemStack, texture,
                RenderTypes.armorCutoutNoCull(texture));
    }

    @Redirect(method = RENDER_LAYERS, at = @At(value = "INVOKE", target = SUBMIT_MODEL))
    private void artifacts$maybeSubmitLayer(OrderedSubmitNodeCollector collector, Model model,
            Object state, PoseStack poseStack, RenderType renderType, int lightCoords,
            int overlayCoords, int tintedColor, TextureAtlasSprite sprite, int outlineColor,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> equipmentAssetId,
            Model outerModel, Object outerState, ItemStack itemStack, PoseStack outerPoseStack,
            SubmitNodeCollector submitNodeCollector, int outerLightCoords,
            Identifier playerTextureOverride, int outerOutlineColor, int order) {
        RenderType resolved = HiddenEquipmentRendering.submitTypeFor(itemStack, renderType);
        if (resolved != null) {
            collector.submitModel(model, state, poseStack, resolved, lightCoords, overlayCoords,
                    tintedColor, sprite, outlineColor, crumblingOverlay);
        }
    }
}
