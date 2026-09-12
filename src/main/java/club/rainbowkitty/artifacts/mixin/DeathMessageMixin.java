package club.rainbowkitty.artifacts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;

import club.rainbowkitty.rkcore.common.death.DeathMessageOverrides;

/**
 * Redirects the single {@code CombatTracker.getDeathMessage()} call inside
 * {@code ServerPlayer#die} — the one spot that builds both the kill-packet text and the broadcast
 * text — through rkcore's {@code common/death} registry, so a registered override (see
 * {@code WaxWings}) can replace it before either is sent.
 */
@Mixin(ServerPlayer.class)
public class DeathMessageMixin {
    private static final String DIE = "die(Lnet/minecraft/world/damagesource/DamageSource;)V";

    private static final String GET_DEATH_MESSAGE =
            "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()"
            + "Lnet/minecraft/network/chat/Component;";

    @Redirect(method = DIE, at = @At(value = "INVOKE", target = GET_DEATH_MESSAGE))
    private Component artifacts$deathMessage(CombatTracker combatTracker, DamageSource source) {
        Component vanilla = combatTracker.getDeathMessage();
        return DeathMessageOverrides.resolve((ServerPlayer) (Object) this, source, vanilla);
    }
}
