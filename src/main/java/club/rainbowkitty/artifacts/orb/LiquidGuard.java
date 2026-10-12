package club.rainbowkitty.artifacts.orb;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import club.rainbowkitty.kalic0re.common.effect.PlayerFlight;

// Liquid cutoffs, the orb's only ones besides AltitudeGuard's fallback and Sputter's failures. In
// water or lava, mayfly itself (not just active flight) is withheld while the holder is in it, so
// they can't launch from inside it either, and for a while after, the wetter the orb the longer.
// In water, an orb in either hand also soaks, and is waterlogged once soaked through (Wetness);
// WaterLift keeps a flyer up out of the water so it doesn't come to that.
final class LiquidGuard {
    // The longest wait out of every liquid before flight comes back, for an orb a tick from
    // waterlogged. A drier one waits its share of this: how soaked it is, so an orb that only
    // touched the water barely waits, and a dry one, back from lava, not at all.
    private static final int DRIP_TICKS = 60;

    // UUID -> ticks the player has been seen out of every liquid, holding the orb, since their
    // mayfly was withheld; present while it is. Kept across letting go of the orb, so swapping it
    // out and back can't restore flight. OrbHooks' "defend the grant" and onActivate's own grant
    // skip these players, or they would undo the cutoff -- only check, once the wait is out,
    // restores it.
    private static final Map<UUID, Integer> WITHHELD = new HashMap<>();

    private LiquidGuard() {
    }

    // Whether a player's mayfly is currently being withheld for a liquid.
    static boolean isWithheld(UUID uuid) {
        return WITHHELD.containsKey(uuid);
    }

    // Starts a withheld player's wait out of the liquid over, as they take the orb up again: it
    // counts only ticks seen with the orb held, and they may have been back in water without it.
    static void restart(UUID uuid) {
        WITHHELD.replace(uuid, 0);
    }

    // Stops tracking a player, on logout.
    static void clear(UUID uuid) {
        WITHHELD.remove(uuid);
    }

    // Runs every tick, not throttled, so a fast-moving flying player can't skim through a liquid
    // between samples, and so each tick in water soaks the orb once. Applies regardless of
    // whether the player is currently flying. Abilities change only on the tick they enter a
    // liquid and the tick the wait after it ends, instead of re-syncing them every tick between.
    static void check(ServerPlayer player) {
        if (!OrbOfLevitation.isManaged(player)) {
            return;
        }
        boolean inWater = player.isInWater();
        if (inWater) {
            long now = player.level().getGameTime();
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.is(OrbOfLevitation.ITEM)) {
                    ItemStack soaked = Wetness.soak(stack, now);
                    if (soaked != stack) {
                        player.setItemInHand(hand, soaked);
                    }
                }
            }
        }
        UUID uuid = player.getUUID();
        if (inWater || player.isInLava()) {
            if (WITHHELD.put(uuid, 0) == null) {
                PlayerFlight.revoke(player);
                AltitudeGuard.clear(uuid);
            }
            return;
        }
        Integer out = WITHHELD.get(uuid);
        if (out == null) {
            return;
        }
        if (out + 1 < Mth.ceil(DRIP_TICKS * Wetness.driest(player))) {
            WITHHELD.put(uuid, out + 1);
            return;
        }
        WITHHELD.remove(uuid);
        PlayerFlight.grant(player);
    }
}
