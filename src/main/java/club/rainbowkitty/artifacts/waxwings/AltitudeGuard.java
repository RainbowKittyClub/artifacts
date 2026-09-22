package club.rainbowkitty.artifacts.waxwings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// Gentle altitude cutoff: cuts just `flying` (leaving mayfly granted for a quick catch) after
// sustained overheight flight, throttled so it costs no per-tick scan.
final class AltitudeGuard {
    private static final int FLIGHT_CHECK_INTERVAL_TICKS = 20;
    private static final int OVERHEIGHT_TRIGGER_CHECKS = 10;
    private static final int MAX_SAFE_HEIGHT = 60;

    // UUID -> consecutive altitude checks in a row a flying player has read as overheight; reset
    // the moment a check finds them back at or under MAX_SAFE_HEIGHT, or cut off for any other
    // reason.
    private static final Map<UUID, Integer> OVERHEIGHT_STREAK = new HashMap<>();

    private AltitudeGuard() {
    }

    // Clears a tracked player's streak, e.g. on logout, re-equip, or an obstruction cutoff.
    static void clear(UUID uuid) {
        OVERHEIGHT_STREAK.remove(uuid);
    }

    // Throttled to FLIGHT_CHECK_INTERVAL_TICKS (1s) off the server's own tick count rather than a
    // per-player counter, since a fixed cadence needs no per-player state to land on it. A flying
    // player has to read as overheight on OVERHEIGHT_TRIGGER_CHECKS checks in a row (10s worth)
    // before this cutoff fires, and dropping back under MAX_SAFE_HEIGHT for even one check resets
    // that streak to zero. Only ever fires while actually flying -- height above the ground is
    // meaningless otherwise, and an obstructed player already has flying == false from
    // ObstructionGuard, so this is naturally a no-op then.
    static void check(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.level().getServer().getTickCount() % FLIGHT_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        UUID uuid = player.getUUID();

        if (FallTracker.isFalling(uuid) || !player.getAbilities().flying) {
            OVERHEIGHT_STREAK.remove(uuid);
            return;
        }

        int height = heightAboveGround(player);
        if (height <= MAX_SAFE_HEIGHT) {
            OVERHEIGHT_STREAK.remove(uuid);
            return;
        }

        int streak = OVERHEIGHT_STREAK.merge(uuid, 1, Integer::sum);
        if (streak < OVERHEIGHT_TRIGGER_CHECKS) {
            return;
        }
        OVERHEIGHT_STREAK.remove(uuid);

        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        // Icarus-eligible only for the altitude reason; "flew too close to the sun" wouldn't fit an
        // obstruction-triggered cutoff.
        FallTracker.markFalling(uuid);
    }

    // Scans straight down from the player's feet for the first block that counts as ground, capped
    // at one past the safe height since anything beyond that already fails the check either way.
    private static int heightAboveGround(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        for (int dy = 1; dy <= MAX_SAFE_HEIGHT + 1; dy++) {
            if (isGround(level, feet.below(dy))) {
                return dy - 1;
            }
        }
        return MAX_SAFE_HEIGHT + 1;
    }

    // Leaves and scaffolding don't count as ground: flying above a canopy or a scaffold tower
    // shouldn't read as safe. Fluids fall through too, since their collision shape is empty.
    private static boolean isGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(Blocks.SCAFFOLDING)) {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }
}
