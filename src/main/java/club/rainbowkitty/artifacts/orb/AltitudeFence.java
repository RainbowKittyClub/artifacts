package club.rainbowkitty.artifacts.orb;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Fake barrier blocks, sent only to a flying holder, that keep them within the flight ceiling
// (OrbGameRules) of the ground: a ceiling over their head. A ledge that would put them higher is
// left open, and AltitudeNudge flies them down it; only the void's edge, where there is nothing
// to come down to, is walled. The server's own world is never touched; see
// rkc/docs/artifacts/plan/orb-and-glider-wings.md, S3, S9, S12 and S13.
final class AltitudeFence {
    private static final int LOOKAHEAD_TICKS = 4;
    private static final double MAX_LOOKAHEAD_BLOCKS = 12.0;
    static final double MILLIS_PER_TICK = 50.0;
    private static final int REFRESH_INTERVAL_TICKS = 20;

    // The placement footprint's margins around the player's path. The forward one absorbs a
    // latency spike the averaged ping behind the lookahead has not caught up with yet.
    private static final double SIDE_BUFFER = 1.0;
    private static final double REAR_BUFFER = 1.0;
    private static final double FORWARD_BUFFER = 2.0;

    // Movement under this, in blocks, counts as standing still.
    static final double STILL_EPSILON = 0.01;

    private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

    // UUID -> the barrier cells a player's client holds, where they were last scanned from, and
    // the level they are in. Kept while flying even when empty, so stillness can be detected.
    private static final Map<UUID, Fence> SHOWN = new HashMap<>();

    private AltitudeFence() {
    }

    // Recomputes the fence around a holder, sends the client only what changed, and lets
    // AltitudeNudge keep them inside the lines. Skips the scan while the holder hangs still and is
    // not being nudged, except on their refresh tick, which rescans and resends the whole fence:
    // a real block update at a fenced cell overwrites it, and terrain can change.
    static void update(ServerPlayer player) {
        if (!player.isAlive() || !OrbOfLevitation.isManaged(player)
                || !player.getAbilities().flying) {
            clear(player);
            return;
        }
        ServerLevel level = player.level();
        Fence previous = SHOWN.get(player.getUUID());
        boolean sameLevel = previous != null && previous.level() == level;
        int phase = Math.floorMod(player.getUUID().hashCode(), REFRESH_INTERVAL_TICKS);
        boolean refresh = !sameLevel
                || (level.getServer().getTickCount() + phase) % REFRESH_INTERVAL_TICKS == 0;
        if (!refresh && isStill(player, previous.scannedFrom())
                && !AltitudeNudge.isActive(player.getUUID())) {
            return;
        }

        Scan scan = scan(player, level);
        Set<BlockPos> wanted = scan.cells();
        Set<BlockPos> shown = sameLevel ? previous.cells() : Set.of();
        for (BlockPos pos : shown) {
            if (!wanted.contains(pos)) {
                restore(player, level, pos);
            }
        }
        for (BlockPos pos : wanted) {
            if (refresh || !shown.contains(pos)) {
                player.connection.send(new ClientboundBlockUpdatePacket(pos, BARRIER));
            }
        }
        SHOWN.put(player.getUUID(), new Fence(level, wanted, player.getBoundingBox()));
        AltitudeNudge.update(player, scan.grid(), player.getBoundingBox(), scan.ahead());
    }

    // Takes a player's whole fence down, sending each cell's real state back. A fence left in
    // another level needs nothing sent: the client dropped that level on the way out.
    static void clear(ServerPlayer player) {
        AltitudeNudge.forget(player.getUUID());
        Fence fence = SHOWN.remove(player.getUUID());
        if (fence == null || fence.level() != player.level()) {
            return;
        }
        for (BlockPos pos : fence.cells()) {
            restore(player, fence.level(), pos);
        }
    }

    // Drops a disconnected player's fence without sending anything.
    static void forget(UUID uuid) {
        SHOWN.remove(uuid);
        AltitudeNudge.forget(uuid);
    }

    // Still when the client reported no movement this tick and the box sits where the last scan
    // saw it, so that scan's fence is still the right one.
    private static boolean isStill(ServerPlayer player, AABB scannedFrom) {
        AABB box = player.getBoundingBox();
        return player.getKnownMovement().lengthSqr() < STILL_EPSILON * STILL_EPSILON
                && Math.abs(box.minX - scannedFrom.minX) < STILL_EPSILON
                && Math.abs(box.minY - scannedFrom.minY) < STILL_EPSILON
                && Math.abs(box.minZ - scannedFrom.minZ) < STILL_EPSILON;
    }

    // Every forbidden cell (HeightGrid.isForbidden) over the head under the footprint: the
    // ceiling. A column the box hangs into, other than the one under its centre, takes none, so
    // one who barely overhangs an edge can still rise. Over a void column the cells beside and
    // below the head are fenced too: a wall, with nothing under it to nudge the holder down to.
    // A wall cell cutting into the box is kept: the client pushes itself out of suffocating blocks
    // (LocalPlayer.moveTowardsClosestSpace).
    private static Scan scan(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox();
        double lookaheadTicks = LOOKAHEAD_TICKS + player.connection.latency() / MILLIS_PER_TICK;
        Vec3 ahead = player.getKnownMovement().scale(lookaheadTicks);
        if (ahead.length() > MAX_LOOKAHEAD_BLOCKS) {
            ahead = ahead.normalize().scale(MAX_LOOKAHEAD_BLOCKS);
        }
        Footprint footprint = Footprint.of(box, ahead);
        int bottom = Mth.floor(Math.min(box.minY, box.minY + ahead.y));
        int top = Mth.floor(Math.max(box.maxY, box.maxY + ahead.y)) + 1;
        int aboveBox = Mth.ceil(box.maxY - HeightGrid.BOX_EPSILON);
        int besideBox = Mth.floor(box.minY + HeightGrid.BOX_EPSILON);
        BlockPos centre = player.blockPosition();

        int minX = Mth.floor(footprint.centreX() - footprint.reachX());
        int maxX = Mth.floor(footprint.centreX() + footprint.reachX());
        int minZ = Mth.floor(footprint.centreZ() - footprint.reachZ());
        int maxZ = Mth.floor(footprint.centreZ() + footprint.reachZ());
        int reach = HeightGrid.REACH;
        HeightGrid grid = new HeightGrid(level, minX - reach, minZ - reach, maxX + reach,
                maxZ + reach, bottom, top);

        Set<BlockPos> cells = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!footprint.touches(x, z)) {
                    continue;
                }
                boolean isCentre = x == centre.getX() && z == centre.getZ();
                boolean isUnderBox = !isCentre && box.minX < x + 1 && box.maxX > x
                        && box.minZ < z + 1 && box.maxZ > z;
                int from = Math.max(bottom, aboveBox);
                int to = isUnderBox ? aboveBox - 1 : top;
                if (!isCentre && grid.isVoid(x, z)) {
                    from = isUnderBox ? Math.max(bottom, besideBox) : bottom;
                }
                for (int y = from; y <= to; y++) {
                    if (level.isInsideBuildHeight(y) && grid.isForbidden(x, y, z)) {
                        cells.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return new Scan(cells, grid, ahead);
    }

    // Sends a cell's real state back over a barrier the client is holding there. Skipped for an
    // unloaded chunk, which the read would otherwise load.
    private static void restore(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.hasChunkAt(pos.getX(), pos.getZ())) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        }
    }

    // The barrier cells shown to one player, the level they were computed in, and the box the
    // scan that produced them started from.
    private record Fence(ServerLevel level, Set<BlockPos> cells, AABB scannedFrom) {
    }

    // One scan's barrier cells, the grid they were read from, and the lookahead it covered.
    private record Scan(Set<BlockPos> cells, HeightGrid grid, Vec3 ahead) {
    }

    // Where barriers may go: a rectangle aligned with the direction of travel rather than the
    // world axes, so its area grows linearly with lookahead in any heading. It runs from
    // REAR_BUFFER behind the box to FORWARD_BUFFER past where the lookahead puts it, SIDE_BUFFER
    // wider on each side; a hovering player gets a square SIDE_BUFFER wider all round.
    private record Footprint(double centreX, double centreZ, double alongX, double alongZ,
            double halfLength, double halfWidth) {

        // The footprint for a box moving by `ahead` over the lookahead.
        static Footprint of(AABB box, Vec3 ahead) {
            double half = box.getXsize() / 2.0;
            double centreX = (box.minX + box.maxX) / 2.0;
            double centreZ = (box.minZ + box.maxZ) / 2.0;
            double travel = Math.hypot(ahead.x, ahead.z);
            if (travel < STILL_EPSILON) {
                double side = half + SIDE_BUFFER;
                return new Footprint(centreX, centreZ, 1.0, 0.0, side, side);
            }
            double alongX = ahead.x / travel;
            double alongZ = ahead.z / travel;
            double back = half + REAR_BUFFER;
            double front = half + travel + FORWARD_BUFFER;
            double shift = (front - back) / 2.0;
            return new Footprint(centreX + alongX * shift, centreZ + alongZ * shift, alongX,
                    alongZ, (front + back) / 2.0, half + SIDE_BUFFER);
        }

        // Half the footprint's extent along the world x axis.
        double reachX() {
            return this.halfLength * Math.abs(this.alongX) + this.halfWidth * Math.abs(this.alongZ);
        }

        // Half the footprint's extent along the world z axis.
        double reachZ() {
            return this.halfLength * Math.abs(this.alongZ) + this.halfWidth * Math.abs(this.alongX);
        }

        // Whether the column's unit square overlaps the footprint, by the separating axis test on
        // the footprint's two axes and the world's two.
        boolean touches(int x, int z) {
            double dx = x + 0.5 - this.centreX;
            double dz = z + 0.5 - this.centreZ;
            double squareSpread = 0.5 * (Math.abs(this.alongX) + Math.abs(this.alongZ));
            double along = dx * this.alongX + dz * this.alongZ;
            double across = dz * this.alongX - dx * this.alongZ;
            return Math.abs(along) <= this.halfLength + squareSpread
                    && Math.abs(across) <= this.halfWidth + squareSpread
                    && Math.abs(dx) <= 0.5 + reachX()
                    && Math.abs(dz) <= 0.5 + reachZ();
        }
    }
}
