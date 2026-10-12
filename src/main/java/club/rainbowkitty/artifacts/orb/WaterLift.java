package club.rainbowkitty.artifacts.orb;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

// The orb doesn't like water: it soaks while held in it, and is waterlogged once soaked through
// (Wetness). So within WATER_HEIGHT of water a holder is kept flying, whatever they do, and
// AltitudeNudge holds a flyer at least that far above its surface. Higher up they may stop flying,
// and fall until caught at that height. See the plan's S12 and S13.
final class WaterLift {
    // Blocks of air kept between a holder's feet and the water below them, whatever the flight
    // ceiling.
    static final int WATER_HEIGHT = 6;

    // floorLevel's answer for a column not over water.
    static final int NO_FLOOR = Integer.MIN_VALUE;

    // The least depth under a holder's feet that water is looked for, whatever the flight
    // ceiling: a terminal-velocity fall, about 4 blocks a tick, still carries 10 blocks once
    // flight damps it.
    private static final int CATCH_REACH = 48;

    private WaterLift() {
    }

    // Puts a holder within WATER_HEIGHT of the water under them back into flight, every tick, so
    // they can't stop flying there. A falling holder is caught early by as far as they will still
    // fall: half a round trip each way before their client hears, then the fall flight's damping
    // lets run out (AltitudeNudge.FLIGHT_DAMPING). Skipped for a passenger, and while
    // LiquidGuard withholds flight in water or lava.
    static void keepAloft(ServerPlayer player) {
        if (!OrbOfLevitation.isManaged(player) || player.isPassenger()
                || !player.getAbilities().mayfly || player.getAbilities().flying) {
            return;
        }
        double clearance = clearance(player);
        if (Double.isNaN(clearance)) {
            return;
        }
        double fall = Math.max(0.0, -player.getKnownMovement().y);
        double roundTrip = player.connection.latency() / AltitudeFence.MILLIS_PER_TICK;
        double carry = fall * (roundTrip + 1.0 / (1.0 - AltitudeNudge.FLIGHT_DAMPING));
        if (clearance > WATER_HEIGHT + carry) {
            return;
        }
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    // The lowest a holder's feet may be over this column: WATER_HEIGHT over the water's surface
    // when the ground at or below level `feet` is water, else NO_FLOOR. Reads the fence's grid
    // for the ground, then the level for the water and its surface above it.
    static int floorLevel(ServerLevel level, HeightGrid grid, int x, int feet, int z) {
        int ground = grid.groundLevel(x, feet, z);
        if (ground == HeightGrid.NO_GROUND || !level.hasChunkAt(x, z)) {
            return NO_FLOOR;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, ground, z);
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return NO_FLOOR;
        }
        return surface(level, pos) + 1 + WATER_HEIGHT;
    }

    // Blocks of air between the feet and the water under the holder's centre, if the first
    // ground at or under the feet is water; NaN if it isn't, or none is in reach. The walk goes as
    // deep as the fence's own, and never less than CATCH_REACH, so a fast fall toward water is
    // seen in time to catch.
    private static double clearance(ServerPlayer player) {
        ServerLevel level = player.level();
        int x = Mth.floor(player.getX());
        int z = Mth.floor(player.getZ());
        if (!level.hasChunkAt(x, z)) {
            return Double.NaN;
        }
        double feetY = player.getBoundingBox().minY;
        int feet = Mth.floor(feetY + HeightGrid.BOX_EPSILON);
        int reach = Math.max(CATCH_REACH, HeightGrid.walkDepth(OrbGameRules.flightCeiling(level)));
        int lowest = Math.max(level.getMinY(), feet - reach);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = feet; y >= lowest; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (Ground.isGround(level, pos, state)) {
                if (!state.getFluidState().is(FluidTags.WATER)) {
                    return Double.NaN;
                }
                return feetY - (surface(level, pos) + 1);
            }
        }
        return Double.NaN;
    }

    // The top water block of the column `pos` stands in, walking up from it.
    private static int surface(ServerLevel level, BlockPos.MutableBlockPos pos) {
        int top = pos.getY();
        while (top < level.getMaxY()
                && level.getFluidState(pos.setY(top + 1)).is(FluidTags.WATER)) {
            top++;
        }
        return top;
    }
}
