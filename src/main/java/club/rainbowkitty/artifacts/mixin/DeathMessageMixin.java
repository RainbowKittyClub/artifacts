package club.rainbowkitty.artifacts.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;

import club.rainbowkitty.kalic0re.common.death.DeathMessageOverrides;

/**
 * Feeds every death message through kalic0re's {@code common/death} registry, so a registered
 * override (see {@code OrbOfLevitation} and {@code WaxWings}) can replace it. Inside
 * {@code getDeathMessage()}, so chat mods that restyle or hide the message at its callers
 * (StyledChat in {@code ServerPlayer.die}) treat the replacement as vanilla's own text.
 */
@Mixin(CombatTracker.class)
public class DeathMessageMixin {
    @Shadow
    @Final
    private List<CombatEntry> entries;

    @Shadow
    @Final
    private LivingEntity mob;

    @ModifyReturnValue(method = "getDeathMessage", at = @At("RETURN"))
    private Component artifacts$deathMessage(Component vanilla) {
        return DeathMessageOverrides.resolve(this.mob, this.entries, vanilla);
    }
}
