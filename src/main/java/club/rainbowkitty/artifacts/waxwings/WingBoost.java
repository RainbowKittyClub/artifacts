package club.rainbowkitty.artifacts.waxwings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// The boost a right-click on BoostCatcher buys: what a firework rocket does when used mid-glide
// (FireworkRocketItem.use), paid for in cooldown and durability instead of a rocket.
final class WingBoost {
    private static final int COOLDOWN_TICKS = 40;
    private static final double WEAR = 2.0;

    private WingBoost() {
    }

    // Re-checks everything the catcher's visibility assumed, since the click arrives up to a round
    // trip after the catcher was last placed.
    static void tryBoost(ServerPlayer player) {
        ItemStack wings = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!wings.is(WaxWings.ITEM) || !player.isFallFlying()
                || !player.getMainHandItem().isEmpty()
                || player.getCooldowns().isOnCooldown(wings)) {
            return;
        }
        ServerLevel level = player.level();
        if (player.dropAllLeashConnections(null)) {
            level.playSound(null, player, SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        // A plain rocket: flight duration 1 and no explosions, so it boosts and never hurts.
        level.addFreshEntity(
                new FireworkRocketEntity(level, new ItemStack(Items.FIREWORK_ROCKET), player));
        // The client's own interaction with the catcher returns CONSUME, which does not swing.
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(wings, COOLDOWN_TICKS);
        WaxMelt.wear(player, wings, WEAR * WaxMelt.multiplier(player));
    }
}
