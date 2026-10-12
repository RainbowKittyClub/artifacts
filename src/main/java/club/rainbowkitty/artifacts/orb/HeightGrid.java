package club.rainbowkitty.artifacts.orb;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

// Heights above the ground for a box of columns and a window of levels, shared by AltitudeFence,
// AltitudeNudge and AltitudeGuard so all three agree on every cell. Smoothed within each level so a
// bump narrower than the smoothing square neither leaves a pocket in the ceiling to get stuck in
// nor vanishes outright: it gives back ROOM_FRACTION of its height as headroom over the ground
// around it. A pit narrower than the square is filled to PIT_DIP under its rim's line, so it can be
// crossed. Columns are read on first use, so querying a thin strip of a wide box reads only that
// strip.
final class HeightGrid {
    // Half-width of the square a raised feature must fill to keep its full height: 1 means 3x3.
    static final int SMOOTHING_RADIUS = 1;

    // Share of a narrower bump's height given back as headroom over the ground around it.
    static final double ROOM_FRACTION = 0.25;

    // How far under its rim's line the line over a filled narrow pit sits: crossing one at the
    // cap takes a small shove down from AltitudeNudge.
    static final int PIT_DIP = 1;

    // How far past a queried column smoothing reads: the opening's 2r, then pitLevel's closing of
    // it, another 2r, which also covers the room's spread of r.
    static final int REACH = 4 * SMOOTHING_RADIUS;

    // Matches Entity#getFluidInteractionBox's deflate: a box resting on a cell boundary is not
    // inside the cell.
    static final double BOX_EPSILON = 0.001;

    // ceilingLevel's answer for a column with no ground in reach, and groundLevel's.
    static final int NO_LINE = Integer.MIN_VALUE;
    static final int NO_GROUND = Integer.MIN_VALUE;

    private final ServerLevel level;
    private final int forbiddenHeight;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int sizeZ;
    private final int bottom;
    private final int sizeY;
    private final int noGround;
    private final boolean[] columnRead;
    private final boolean[] solid;
    private final int[] ground;
    private final int[] eroded;
    private final boolean[] erodedSet;
    private final int[] opened;
    private final boolean[] openedSet;
    private final int[] dilated;
    private final boolean[] dilatedSet;

    // Covers [minX, maxX] x [minZ, maxZ] and levels [bottom, top]; callers query only columns at
    // least REACH inside that box, since smoothing reads that far around each one. The flight
    // ceiling is the level's game rule, read once here.
    HeightGrid(ServerLevel level, int minX, int minZ, int maxX, int maxZ, int bottom, int top) {
        int ceiling = OrbGameRules.flightCeiling(level);
        this.level = level;
        this.forbiddenHeight = forbiddenHeight(ceiling);
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.sizeZ = maxZ - minZ + 1;
        this.bottom = bottom;
        this.sizeY = top - bottom + 1;
        this.noGround = bottom - walkDepth(ceiling) - 1;
        int columns = (maxX - minX + 1) * this.sizeZ;
        int cells = columns * this.sizeY;
        this.columnRead = new boolean[columns];
        this.solid = new boolean[cells];
        this.ground = new int[cells];
        this.eroded = new int[cells];
        this.erodedSet = new boolean[cells];
        this.opened = new int[cells];
        this.openedSet = new boolean[cells];
        this.dilated = new int[cells];
        this.dilatedSet = new boolean[cells];
    }

    // Whether this cell can be queried: inside the window, and in a column at least REACH inside
    // the box.
    boolean covers(int x, int y, int z) {
        return x >= this.minX + REACH && x <= this.maxX - REACH
                && z >= this.minZ + REACH && z <= this.maxZ - REACH
                && y >= this.bottom && y < this.bottom + this.sizeY;
    }

    // The highest ground at or below this cell, itself when solid; NO_GROUND with none in reach.
    int groundLevel(int x, int y, int z) {
        int ground = this.ground[cell(x, y, z)];
        return ground == this.noGround ? NO_GROUND : ground;
    }

    // The highest a column's line can be when its ground lies past the walk: a floor to aim a
    // descent at until the ground comes within reach.
    int deepestLine() {
        return this.noGround + 1 + this.forbiddenHeight;
    }

    // Whether the column holds nothing at all down to the bottom of the world, as off the edge of
    // an End island: no ground in reach, and a world-surface heightmap at the world's floor.
    // Heightmaps count down from the top, so one over an empty column below an overhang reads
    // the overhang; that column reads as not void.
    boolean isVoid(int x, int z) {
        return this.ground[cell(x, this.bottom, z)] == this.noGround
                && this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
                        <= this.level.getMinY();
    }

    // The height of the first forbidden cell under a flight ceiling: a 1.8-tall player with feet in
    // the ceiling's own cell reaches 0.8 into the cell two above it, so the first forbidden cell
    // is one further; see the plan's "Reference: Fence geometry".
    static int forbiddenHeight(int ceiling) {
        return ceiling + 2;
    }

    // How far below its window a grid looks for a column's ground under a flight ceiling: deep
    // enough to find the ground under a drop as deep as the ceiling itself, flying at it. Deeper
    // ground is only known to be past the walk (deepestLine). That is past the deepest ground any
    // cell can stand over and still be allowed, (forbiddenHeight + 1) / (1 - ROOM_FRACTION), since
    // a bump's headroom lifts a cell's smoothed ground by at most ROOM_FRACTION of its own depth.
    static int walkDepth(int ceiling) {
        return forbiddenHeight(ceiling) + 1 + ceiling;
    }

    // Whether a flyer could pass through this cell, so a barrier could stand in it.
    boolean isOpen(int x, int y, int z) {
        return !this.solid[cell(x, y, z)];
    }

    // Whether this cell is open and too high: forbiddenHeight or more above its smoothed ground,
    // or with no ground in reach at all, which no bump's headroom can make safe; either way unless
    // it lies inside a filled narrow pit.
    boolean isForbidden(int x, int y, int z) {
        int index = cell(x, y, z);
        if (this.solid[index]) {
            return false;
        }
        if (this.ground[index] == this.noGround) {
            return !isInFilledPit(x, y, z);
        }
        // The smoothed ground never sits below the lowest ground in the centred square, that square
        // being one of those the opening takes the highest minimum of. A cell under the line even
        // by that lower bound, as nearly every cell is, needs neither the opening nor a bump's
        // headroom, nor the columns beyond its own neighbours that those read.
        if (y - eroded(x, y, z) - 1 < this.forbiddenHeight) {
            return false;
        }
        return height(x, y, z) >= this.forbiddenHeight && !isInFilledPit(x, y, z);
    }

    // The column's line: its lowest forbidden level, reading the smoothed ground at level `y`,
    // and no lower than a filled narrow pit's (pitLevel). Exact from `y` down to the line while
    // no ground within reach lies between them, as over open air, where the smoothed ground is
    // the same at every level. NO_LINE with no ground.
    // When the lowest ground in the centred square (isForbidden's lower bound) already puts the
    // line at `atLeast` or higher, returns that bound instead and skips the smoothing: a caller
    // asking only whether the line is under `atLeast` gets the same answer.
    int ceilingLevel(int x, int y, int z, int atLeast) {
        int floor = eroded(x, y, z);
        if (floor != this.noGround && floor + 1 + this.forbiddenHeight >= atLeast) {
            return floor + 1 + this.forbiddenHeight;
        }
        int base = opened(x, y, z);
        int line = base == this.noGround ? NO_LINE
                : (int) Math.ceil(base + ROOM_FRACTION * bump(x, y, z) + 1 + this.forbiddenHeight);
        if (!mayBePit(x, y, z, line)) {
            return line;
        }
        return Math.max(line, pitLevel(x, y, z));
    }

    // Whether the cell lies under the line of a filled narrow pit (pitLevel).
    private boolean isInFilledPit(int x, int y, int z) {
        if (!mayBePit(x, y, z, y)) {
            return false;
        }
        int pit = pitLevel(x, y, z);
        return pit != NO_LINE && y < pit;
    }

    // Whether a filled pit's line could lie over `level`. The closing never rises past the
    // highest opened ground in the centred square, which bump has already read, so away from
    // pits this spares pitLevel's reads two columns further out.
    private boolean mayBePit(int x, int y, int z, int level) {
        int highest = dilated(x, y, z);
        return highest != this.noGround && highest + 1 + this.forbiddenHeight - PIT_DIP > level;
    }

    // The line a narrow pit is filled to: PIT_DIP under the line of the opened ground after a 3x3
    // closing, the lowest of the highest opened grounds in the squares holding the cell. A pit
    // narrower than the square rises to its rim and a wider drop stays as it is, under its own
    // line. Closing the opened ground rather than the raw ground matters: the raw ground's narrow
    // spikes, which the opening shaved, would otherwise fill the gaps between them, as over a
    // basalt delta. NO_LINE when even the closing finds no ground.
    private int pitLevel(int x, int y, int z) {
        int value = Integer.MAX_VALUE;
        for (int cx = x - SMOOTHING_RADIUS; cx <= x + SMOOTHING_RADIUS; cx++) {
            for (int cz = z - SMOOTHING_RADIUS; cz <= z + SMOOTHING_RADIUS; cz++) {
                value = Math.min(value, dilated(cx, y, cz));
            }
        }
        return value == this.noGround ? NO_LINE : value + 1 + this.forbiddenHeight - PIT_DIP;
    }

    // The highest opened ground in the square centred on the cell, at one level.
    private int dilated(int x, int y, int z) {
        int index = cell(x, y, z);
        if (!this.dilatedSet[index]) {
            int value = Integer.MIN_VALUE;
            for (int sx = x - SMOOTHING_RADIUS; sx <= x + SMOOTHING_RADIUS; sx++) {
                for (int sz = z - SMOOTHING_RADIUS; sz <= z + SMOOTHING_RADIUS; sz++) {
                    value = Math.max(value, opened(sx, y, sz));
                }
            }
            this.dilated[index] = value;
            this.dilatedSet[index] = true;
        }
        return this.dilated[index];
    }

    // Height above the smoothed ground: the opened ground, with narrow bumps shaved off, raised by
    // ROOM_FRACTION of the tallest bump shaved within SMOOTHING_RADIUS.
    private double height(int x, int y, int z) {
        return y - (opened(x, y, z) + ROOM_FRACTION * bump(x, y, z)) - 1;
    }

    // The tallest bump the opening shaved within SMOOTHING_RADIUS. A bump only counts when it
    // stands on measured ground; one beside unmeasured depth would inflate by that depth.
    private int bump(int x, int y, int z) {
        int bump = 0;
        for (int bx = x - SMOOTHING_RADIUS; bx <= x + SMOOTHING_RADIUS; bx++) {
            for (int bz = z - SMOOTHING_RADIUS; bz <= z + SMOOTHING_RADIUS; bz++) {
                int base = opened(bx, y, bz);
                if (base != this.noGround) {
                    bump = Math.max(bump, this.ground[cell(bx, y, bz)] - base);
                }
            }
        }
        return bump;
    }

    // Morphological opening at one level: the highest of the minima of the squares holding the
    // cell. Removes every raised feature narrower than the square and leaves wider ones intact.
    private int opened(int x, int y, int z) {
        int index = cell(x, y, z);
        if (!this.openedSet[index]) {
            int value = Integer.MIN_VALUE;
            for (int cx = x - SMOOTHING_RADIUS; cx <= x + SMOOTHING_RADIUS; cx++) {
                for (int cz = z - SMOOTHING_RADIUS; cz <= z + SMOOTHING_RADIUS; cz++) {
                    value = Math.max(value, eroded(cx, y, cz));
                }
            }
            this.opened[index] = value;
            this.openedSet[index] = true;
        }
        return this.opened[index];
    }

    // The lowest ground in the square centred on the cell, at one level.
    private int eroded(int x, int y, int z) {
        int index = cell(x, y, z);
        if (!this.erodedSet[index]) {
            int value = Integer.MAX_VALUE;
            for (int sx = x - SMOOTHING_RADIUS; sx <= x + SMOOTHING_RADIUS; sx++) {
                for (int sz = z - SMOOTHING_RADIUS; sz <= z + SMOOTHING_RADIUS; sz++) {
                    value = Math.min(value, this.ground[cell(sx, y, sz)]);
                }
            }
            this.eroded[index] = value;
            this.erodedSet[index] = true;
        }
        return this.eroded[index];
    }

    // Flattens a world position inside this grid to its array slot, reading its column first.
    private int cell(int x, int y, int z) {
        int column = (x - this.minX) * this.sizeZ + (z - this.minZ);
        if (!this.columnRead[column]) {
            readColumn(column, x, z);
        }
        return column * this.sizeY + (y - this.bottom);
    }

    // Reads the window cells, then walks down to the first ground within WALK_DEPTH of the window.
    // Each cell records the highest ground at or below it, itself when solid, so an overhang is
    // never mistaken for the floor. An unloaded column reads as solid: never fenced, and never
    // the reason a neighbour is.
    private void readColumn(int column, int x, int z) {
        this.columnRead[column] = true;
        int base = column * this.sizeY;
        if (!this.level.hasChunkAt(x, z)) {
            for (int i = 0; i < this.sizeY; i++) {
                this.solid[base + i] = true;
                this.ground[base + i] = this.bottom + i;
            }
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < this.sizeY; i++) {
            pos.set(x, this.bottom + i, z);
            this.solid[base + i] = Ground.isGround(this.level, pos, this.level.getBlockState(pos));
        }
        int below = this.noGround;
        for (int y = this.bottom - 1; y > this.noGround; y--) {
            pos.set(x, y, z);
            if (Ground.isGround(this.level, pos, this.level.getBlockState(pos))) {
                below = y;
                break;
            }
        }
        for (int i = 0; i < this.sizeY; i++) {
            if (this.solid[base + i]) {
                below = this.bottom + i;
            }
            this.ground[base + i] = below;
        }
    }
}
