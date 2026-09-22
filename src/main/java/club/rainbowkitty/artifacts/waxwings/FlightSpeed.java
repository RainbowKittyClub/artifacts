package club.rainbowkitty.artifacts.waxwings;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

// Slows a flying wearer's speed and cuts it further in the rain, restoring vanilla's own value
// once the wings come off.
final class FlightSpeed {
    // Read off a fresh Abilities rather than hardcoding vanilla's 0.05F, so a future vanilla
    // change to the default can't silently drift out of sync with a duplicated literal here.
    private static final float VANILLA_FLYING_SPEED = new Abilities().getFlyingSpeed();

    private static final float FLYING_SPEED_MULTIPLIER = 0.85F;
    private static final float RAIN_FLYING_SPEED_MULTIPLIER = 0.5F;

    private FlightSpeed() {
    }

    // Recomputed every tick, since rain comes and goes while still airborne. Only touches the
    // ability when the value actually changes, same care as ObstructionGuard.check. Skips
    // creative and spectator for the same reason PlayerFlight does: that flight is the gamemode's
    // own, not the wings', and isn't this effect's to slow down. Skips a grounded wearer too: the
    // value only matters once flying, so there's nothing to recompute until then.
    static void update(ServerPlayer player) {
        if (!WaxWings.isManaged(player) || !player.getAbilities().flying) {
            return;
        }
        float speed = VANILLA_FLYING_SPEED * FLYING_SPEED_MULTIPLIER
                * (isRainingOn(player) ? RAIN_FLYING_SPEED_MULTIPLIER : 1F);
        if (player.getAbilities().getFlyingSpeed() != speed) {
            player.getAbilities().setFlyingSpeed(speed);
            player.onUpdateAbilities();
        }
    }

    // Vanilla never resets this on its own once something has changed it, so leaving the wings
    // off without this would leave a later, unrelated flight source (creative mode, another mod)
    // slow.
    static void reset(ServerPlayer player) {
        if (player.getAbilities().getFlyingSpeed() != VANILLA_FLYING_SPEED) {
            player.getAbilities().setFlyingSpeed(VANILLA_FLYING_SPEED);
            player.onUpdateAbilities();
        }
    }

    // Mirrors Entity's own private isInRain(): rained on at either the feet or the head, so
    // standing under a roof during a storm doesn't count.
    private static boolean isRainingOn(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        return level.isRainingAt(pos)
                || level.isRainingAt(BlockPos.containing(pos.getX(), player.getBoundingBox().maxY,
                        pos.getZ()));
    }
}
