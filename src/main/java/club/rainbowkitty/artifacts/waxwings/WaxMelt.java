package club.rainbowkitty.artifacts.waxwings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

// Altitude-scaled wear on top of vanilla's own glide wear, and the durability floor every wear
// source in this package respects.
final class WaxMelt {
    private static final double MELT_START_Y = 64.0;
    private static final double MELT_BLOCKS_PER_STEP = 64.0;

    // Vanilla's own glide-wear cadence: one point per 20 ticks (LivingEntity.updateFallFlying).
    private static final int WEAR_INTERVAL_TICKS = 20;

    private WaxMelt() {
    }

    // Wear multiplier at the player's height: 1 at or below MELT_START_Y, one more for every
    // MELT_BLOCKS_PER_STEP above it.
    static double multiplier(ServerPlayer player) {
        return 1.0 + Math.max(0.0, player.getY() - MELT_START_Y) / MELT_BLOCKS_PER_STEP;
    }

    // Adds the altitude share of glide wear once per WEAR_INTERVAL_TICKS of gliding; vanilla
    // already takes the base point on the same cadence.
    static void tick(ServerPlayer player, ItemStack wings) {
        if (!player.isFallFlying() || player.getFallFlyingTicks() % WEAR_INTERVAL_TICKS != 0) {
            return;
        }
        wear(player, wings, multiplier(player) - 1.0);
    }

    // Takes `amount` durability, rolling its fractional part so the average holds. Never takes the
    // last point: vanilla's own wear stops there (LivingEntity.canGlideUsing), and hurtAndBreak
    // past it would destroy the wings instead of grounding their wearer.
    static void wear(ServerPlayer player, ItemStack wings, double amount) {
        int points = (int) amount;
        if (player.getRandom().nextDouble() < amount - points) {
            points++;
        }
        int spare = wings.getMaxDamage() - wings.getDamageValue() - 1;
        int taken = Math.min(points, spare);
        if (taken > 0) {
            wings.hurtAndBreak(taken, player, EquipmentSlot.CHEST);
        }
    }
}
