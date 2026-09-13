package club.rainbowkitty.artifacts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import club.rainbowkitty.kalic0re.common.death.DeathMessageOverrides;

/**
 * Routes the single {@code CombatTracker.getDeathMessage()} call inside {@code ServerPlayer#die} —
 * the one spot that builds both the kill-packet text and the broadcast text — through kalic0re's
 * {@code common/death} registry, so a registered override (see {@code WaxWings}) can replace it
 * before either is sent.
 *
 * <p>{@link ModifyExpressionValue}, not {@code @Redirect}, and that is not a style preference. Only
 * one {@code @Redirect} may claim a given call site: a second mod redirecting the same call gets
 * silently skipped, and its own {@code defaultRequire} then turns that into a fatal
 * {@code InjectionError} that takes the whole server down at launch. StyledChat redirects exactly
 * this call to style death messages, so a redirect here is not a conflict this mod can win — it is
 * a crash. MixinExtras wrappers compose instead: {@code original} is whatever the expression
 * produced after every other mod had its turn, and returning it unchanged leaves their work intact.
 */
@Mixin(ServerPlayer.class)
public class DeathMessageMixin {
    private static final String DIE = "die(Lnet/minecraft/world/damagesource/DamageSource;)V";

    private static final String GET_DEATH_MESSAGE =
            "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()"
            + "Lnet/minecraft/network/chat/Component;";

    // The trailing DamageSource is die()'s own argument, captured by appending it to the
    // injector's parameters.
    @ModifyExpressionValue(method = DIE, at = @At(value = "INVOKE", target = GET_DEATH_MESSAGE))
    private Component artifacts$deathMessage(Component original, DamageSource source) {
        return DeathMessageOverrides.resolve((ServerPlayer) (Object) this, source, original);
    }
}
