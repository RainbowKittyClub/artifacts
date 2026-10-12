package club.rainbowkitty.artifacts.orb;

import net.minecraft.server.level.ServerPlayer;

// A wet orb's flight fails at random: `flying` cut, as AltitudeGuard cuts it, with mayfly left
// granted, so the holder falls until they take off again. A dry orb never fails. A wet one fails
// with the same chance every tick of flight, so the flight between failures averages a length set
// by the durability left (Wetness). See the plan's S17 and S18.
final class Sputter {
    // The average flight between failures for an orb a tick from waterlogged, and for one barely
    // damaged. Between the two it runs in a straight line with the durability left.
    private static final double SHORTEST_MEAN_TICKS = 20.0;
    private static final double LONGEST_MEAN_TICKS = 1200.0;

    private Sputter() {
    }

    // Runs every tick of flight, before WaterLift, which puts a holder within reach of water back
    // into flight whatever ended it.
    static void check(ServerPlayer player) {
        if (!OrbOfLevitation.isManaged(player) || !player.getAbilities().flying) {
            return;
        }
        double soaked = Wetness.driest(player);
        if (soaked <= 0.0) {
            return;
        }
        double mean = SHORTEST_MEAN_TICKS
                + (LONGEST_MEAN_TICKS - SHORTEST_MEAN_TICKS) * (1.0 - soaked);
        if (player.getRandom().nextDouble() * mean >= 1.0) {
            return;
        }
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        // The orb giving out, so a fall from here earns its death message.
        FallTracker.markFalling(player.getUUID());
    }
}
