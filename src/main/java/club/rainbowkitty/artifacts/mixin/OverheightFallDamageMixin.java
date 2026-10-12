package club.rainbowkitty.artifacts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import club.rainbowkitty.artifacts.orb.OrbOfLevitation;

/**
 * {@code Player#causeFallDamage} returns early whenever {@code abilities.mayfly} is true, at all,
 * regardless of {@code abilities.flying} — which is why the Orb of Levitation would grant total
 * fall immunity merely by being held, flying or not, rather than only while its flight is actually
 * in use. This reports {@code mayfly} as false for that one check whenever
 * {@link OrbOfLevitation#isUnprotectedFall} says so for an orb holder; every other player's mayfly
 * (creative mode included) is untouched.
 *
 * <p>{@link ModifyExpressionValue}, matching {@code DeathMessageMixin}: composing over the value
 * the field read produces, rather than claiming the whole method, is what lets this coexist with
 * any other mod's own hook on the same method.
 */
@Mixin(Player.class)
public class OverheightFallDamageMixin {
    private static final String CAUSE_FALL_DAMAGE =
            "causeFallDamage(DFLnet/minecraft/world/damagesource/DamageSource;)Z";

    private static final String MAYFLY_FIELD =
            "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z";

    @ModifyExpressionValue(method = CAUSE_FALL_DAMAGE,
            at = @At(value = "FIELD", target = MAYFLY_FIELD))
    private boolean artifacts$forceFallDamage(boolean mayfly) {
        if (!((Player) (Object) this instanceof ServerPlayer player)) {
            return mayfly;
        }
        return mayfly && !OrbOfLevitation.isUnprotectedFall(player);
    }
}
