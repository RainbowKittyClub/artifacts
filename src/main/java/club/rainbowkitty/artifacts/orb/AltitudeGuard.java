package club.rainbowkitty.artifacts.orb;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

// Altitude cutoff, the fallback for a client that ignores the fence's ceiling and AltitudeNudge:
// cuts just `flying` (leaving mayfly granted for a quick catch) after sustained overheight flight,
// throttled so it costs no per-tick scan. The ceiling and the nudges keep an honest client under
// the flight ceiling (OrbGameRules) to begin with.
final class AltitudeGuard {
    private static final int FLIGHT_CHECK_INTERVAL_TICKS = 20;
    private static final int OVERHEIGHT_TRIGGER_CHECKS = 3;

    // A check that finds the box top at least this much lower than the check before does not
    // extend the streak: the holder is coming down, as AltitudeNudge flies them off a ledge.
    private static final double DESCENT_PROOF = 0.5;

    // UUID -> consecutive altitude checks in a row a flying player has read as overheight, and
    // where the last one found their box top; reset the moment a check finds them back under the
    // flight ceiling, or cut off for any other reason.
    private static final Map<UUID, Streak> OVERHEIGHT_STREAK = new HashMap<>();

    private AltitudeGuard() {
    }

    // Clears a tracked player's streak, e.g. on logout, re-equip, or a liquid cutoff.
    static void clear(UUID uuid) {
        OVERHEIGHT_STREAK.remove(uuid);
    }

    // Throttled to FLIGHT_CHECK_INTERVAL_TICKS (1s) off the server's own tick count rather than a
    // per-player counter, since a fixed cadence needs no per-player state to land on it. A flying
    // player has to read as overheight on OVERHEIGHT_TRIGGER_CHECKS checks in a row (3s worth)
    // before this cutoff fires, and dropping back under the ceiling for even one check resets
    // that streak to zero. A check that finds them DESCENT_PROOF lower than the last starts the
    // streak over instead of extending it, so a long nudged descent down a slope never trips it
    // while a client that ignores the nudges still does. Only ever fires while actually flying.
    static void check(ServerPlayer player) {
        if (!OrbOfLevitation.isManaged(player)) {
            return;
        }
        if (player.level().getServer().getTickCount() % FLIGHT_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        UUID uuid = player.getUUID();

        if (FallTracker.isFalling(uuid) || !player.getAbilities().flying
                || !isOverheight(player)) {
            OVERHEIGHT_STREAK.remove(uuid);
            return;
        }

        double top = player.getBoundingBox().maxY;
        Streak last = OVERHEIGHT_STREAK.get(uuid);
        int streak = last != null && last.top() - top < DESCENT_PROOF ? last.checks() + 1 : 1;
        OVERHEIGHT_STREAK.put(uuid, new Streak(streak, top));
        if (streak < OVERHEIGHT_TRIGGER_CHECKS) {
            return;
        }
        OVERHEIGHT_STREAK.remove(uuid);

        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        // Death-message-eligible only for the altitude reason; a liquid cutoff is the world's
        // doing, not the orb giving out.
        FallTracker.markFalling(uuid);
    }

    // Whether the body, in the column under its centre, overlaps a cell the fence forbids: the
    // rule AltitudeFence places its ceiling by, smoothing, a bump's headroom and the pit fill
    // included, so a holder resting against the ceiling is never read as overheight.
    private static boolean isOverheight(ServerPlayer player) {
        AABB box = player.getBoundingBox();
        BlockPos centre = player.blockPosition();
        int feet = Mth.floor(box.minY + HeightGrid.BOX_EPSILON);
        int head = Mth.ceil(box.maxY - HeightGrid.BOX_EPSILON) - 1;
        int reach = HeightGrid.REACH;
        HeightGrid grid = new HeightGrid(player.level(), centre.getX() - reach,
                centre.getZ() - reach, centre.getX() + reach, centre.getZ() + reach, feet, head);
        for (int y = feet; y <= head; y++) {
            if (grid.isForbidden(centre.getX(), y, centre.getZ())) {
                return true;
            }
        }
        return false;
    }

    // An overheight streak: how many checks in a row, and the box top at the latest.
    private record Streak(int checks, double top) {
    }
}
