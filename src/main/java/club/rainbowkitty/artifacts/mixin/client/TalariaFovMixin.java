package club.rainbowkitty.artifacts.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import club.rainbowkitty.artifacts.Talaria;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectAttributes;

/**
 * {@code AbstractClientPlayer#getFieldOfViewModifier} reads the raw, already-synced
 * {@code Attributes.MOVEMENT_SPEED} value to widen FOV in proportion to how much faster than base
 * walking speed the player currently is -- the same mechanism that kicks in for sprinting or a
 * Speed potion. Talaria's +35% would otherwise trigger it too, so this divides Talaria's own
 * multiplier back out of the value before that calculation runs, leaving every other speed source
 * (sprint, potions, other mods) affecting FOV exactly as vanilla intends.
 *
 * <p>Client-only, like the mod's other rendering mixins: a vanilla client sees Talaria as plain
 * {@code Items#COPPER_BOOTS} with no way to intercept its own client-side FOV math, so this only
 * takes effect for a client that has this mod installed.
 */
@Mixin(AbstractClientPlayer.class)
public class TalariaFovMixin {
    private static final String GET_FIELD_OF_VIEW_MODIFIER = "getFieldOfViewModifier(ZF)F";

    private static final String GET_ATTRIBUTE_VALUE =
            "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue"
            + "(Lnet/minecraft/core/Holder;)D";

    @ModifyExpressionValue(method = GET_FIELD_OF_VIEW_MODIFIER,
            at = @At(value = "INVOKE", target = GET_ATTRIBUTE_VALUE))
    private double artifacts$excludeTalariaSpeed(double movementSpeed) {
        LivingEntity self = (LivingEntity) (Object) this;
        AttributeInstance speed = self.getAttribute(Attributes.MOVEMENT_SPEED);
        Identifier id = PlayerEffectAttributes.modifierId(Talaria.EFFECT,
                Attributes.MOVEMENT_SPEED);
        if (speed == null || !speed.hasModifier(id)) {
            return movementSpeed;
        }
        return movementSpeed / (1.0 + speed.getModifier(id).amount());
    }
}
