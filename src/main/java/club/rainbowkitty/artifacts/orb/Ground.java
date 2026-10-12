package club.rainbowkitty.artifacts.orb;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

// What counts as footing under a flying orb holder, read by HeightGrid for AltitudeFence's
// barriers, AltitudeNudge's lines and AltitudeGuard's cutoff.
final class Ground {
    private Ground() {
    }

    // Any fluid, or any block with collision; anything a player walks through is not ground.
    static boolean isGround(BlockGetter level, BlockPos pos, BlockState state) {
        return !state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty();
    }
}
