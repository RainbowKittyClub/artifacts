package club.rainbowkitty.artifacts.waxwings;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.effect.PlayerFlight;

// Immediate obstruction cutoff: withholds mayfly itself (not just active flight) while the player
// is in a liquid, on fire, or touching a cobweb, sweet berry bush, or vine, so they can't launch
// from inside one either.
final class ObstructionGuard {
    // Matches Entity#getFluidInteractionBox's own deflate: without it, a player standing with feet
    // exactly on a solid floor reads as overlapping that floor's block too, at the shared boundary.
    private static final double OBSTRUCTION_BOX_EPSILON = 0.001;

    // Players whose mayfly is being withheld for being obstructed (in a liquid, on fire, a
    // cobweb, sweet berry bush, or vine). WaxWingsHooks#onTick's usual "defend the grant" must
    // skip these players, or it would undo check's own cutoff one tick later -- only check's own
    // next check, once it finds them clear, restores it.
    private static final Set<UUID> OBSTRUCTED = new HashSet<>();

    private ObstructionGuard() {
    }

    // Whether a player's mayfly is currently being withheld for an obstruction.
    static boolean isWithheld(UUID uuid) {
        return OBSTRUCTED.contains(uuid);
    }

    // Stops tracking a player, e.g. on logout, re-equip, or deactivation.
    static void clear(UUID uuid) {
        OBSTRUCTED.remove(uuid);
    }

    // Runs every tick, not throttled: unlike altitude there's no measurement noise to tolerate, and
    // a fast-moving flying player would otherwise skim through an obstruction between samples and
    // never get caught. Applies regardless of whether the player is currently flying -- mayfly
    // itself is withheld while obstructed, not just active flight, so they can't launch from inside
    // one either. Set#add/#remove's own return value gates the actual ability change to the exact
    // transition tick, instead of re-syncing abilities every tick spent stuck.
    static void check(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        UUID uuid = player.getUUID();
        if (isObstructed(player)) {
            if (OBSTRUCTED.add(uuid)) {
                PlayerFlight.revoke(player);
                AltitudeGuard.clear(uuid);
            }
            return;
        }
        if (OBSTRUCTED.remove(uuid)) {
            PlayerFlight.grant(player);
        }
    }

    // True while the player is in any liquid (Entity#isInLiquid: water or lava) or on fire, or any
    // part of their own volume overlaps a cobweb, sweet berry bush, or vine. The bounding-box scan
    // is deflated the same way Entity#getFluidInteractionBox is, so standing with feet exactly on a
    // solid floor -- whose top face touches the bounding box's own bottom face -- doesn't count as
    // overlapping it.
    private static boolean isObstructed(ServerPlayer player) {
        if (player.isInLiquid() || player.isOnFire()) {
            return true;
        }
        ServerLevel level = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                player.getBoundingBox().deflate(OBSTRUCTION_BOX_EPSILON))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.COBWEB) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.VINE)) {
                return true;
            }
        }
        return false;
    }
}
