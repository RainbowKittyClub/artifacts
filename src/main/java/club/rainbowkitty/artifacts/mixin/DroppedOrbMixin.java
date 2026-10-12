package club.rainbowkitty.artifacts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.item.ItemEntity;

import club.rainbowkitty.artifacts.orb.OrbOfLevitation;

/**
 * Hands every item on the ground to {@link OrbOfLevitation#tickDropped} each tick, since nothing
 * ticks the stack itself there the way an inventory does, and a wet orb lying in the rain must
 * not dry. At the head, before {@code tick} can discard the entity or return early for an empty
 * stack.
 */
@Mixin(ItemEntity.class)
public abstract class DroppedOrbMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void artifacts$tickDroppedOrb(CallbackInfo ci) {
        OrbOfLevitation.tickDropped((ItemEntity) (Object) this);
    }
}
