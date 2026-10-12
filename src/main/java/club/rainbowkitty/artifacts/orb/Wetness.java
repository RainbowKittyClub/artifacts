package club.rainbowkitty.artifacts.orb;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

// The orb's wetness, which is its damage. Held in water, or lying in it, it gains a point every
// SOAK_STEP_TICKS, and the point that would use up its durability, SOAK_TICKS in, makes it
// OrbOfLevitation.WATERLOGGED_ITEM instead. Out of the water it loses a point every
// DRY_STEP_TICKS, back to dry over DRY_TICKS, but only while something ticks it: in an inventory
// (OrbItem) or on the ground (OrbOfLevitation.tickDropped), and not held or lying in the rain.
// Anywhere else, a chest or an item frame, it keeps as wet as it is. Both steps land on fixed
// beats of the game clock rather than on a count kept per orb, so the damage is all it stores,
// and it changes, and is resent to a holder's client, only when a whole point does. A wet orb
// makes its holder wait out of the water for flight (LiquidGuard), and its flight fails at random
// (Sputter). See the plan's S16 to S18.
final class Wetness {
    // The orb's durability: the most points on which both steps come out in whole ticks, since
    // SOAK_TICKS and DRY_TICKS share no larger factor.
    static final int POINTS = 20;

    // Ticks in water from dry to waterlogged, and out of it from a point short of that to dry.
    private static final int SOAK_TICKS = 140;
    private static final int DRY_TICKS = 12000;

    // Ticks between one point and the next each way: 7 and 600.
    private static final int SOAK_STEP_TICKS = SOAK_TICKS / POINTS;
    private static final int DRY_STEP_TICKS = DRY_TICKS / POINTS;

    private Wetness() {
    }

    // One tick of an orb in water. On a soak beat it gains a point, and is returned; if that point
    // would use up its durability, the waterlogged orb its holder should put in its place is
    // returned instead. That keeps whatever else the stack carries, a rename included, but not its
    // damage, which it has no durability to show.
    static ItemStack soak(ItemStack stack, long now) {
        if (now % SOAK_STEP_TICKS != 0L) {
            return stack;
        }
        int damage = stack.getDamageValue() + 1;
        if (damage >= stack.getMaxDamage()) {
            ItemStack waterlogged = stack.transmuteCopy(OrbOfLevitation.WATERLOGGED_ITEM);
            waterlogged.remove(DataComponents.DAMAGE);
            return waterlogged;
        }
        stack.setDamageValue(damage);
        return stack;
    }

    // One tick of an orb out of the water and the rain: on a drying beat it loses a point.
    static void dry(ItemStack stack, long now) {
        if (now % DRY_STEP_TICKS == 0L && stack.isDamaged()) {
            stack.setDamageValue(stack.getDamageValue() - 1);
        }
    }

    // How soaked the driest orb in the holder's hands is: 0 for a dry one, or with none held, up to
    // a point short of 1 for one a point from waterlogged. Flight comes from whichever orb is held,
    // so the driest is the one that decides.
    static double driest(ServerPlayer player) {
        double driest = Double.POSITIVE_INFINITY;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(OrbOfLevitation.ITEM)) {
                driest = Math.min(driest,
                        (double) stack.getDamageValue() / stack.getMaxDamage());
            }
        }
        return driest == Double.POSITIVE_INFINITY ? 0.0 : driest;
    }
}
