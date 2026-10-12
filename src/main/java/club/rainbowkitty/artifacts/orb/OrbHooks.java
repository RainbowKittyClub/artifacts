package club.rainbowkitty.artifacts.orb;

import net.minecraft.server.level.ServerPlayer;

import club.rainbowkitty.kalic0re.common.effect.PlayerEffectHooks;
import club.rainbowkitty.kalic0re.common.effect.PlayerFlight;

// PlayerEffectHooks glue between OrbOfLevitation.EFFECT and its tracking classes, constructed once
// from OrbOfLevitation.
final class OrbHooks implements PlayerEffectHooks {
    // Taking the orb up in water or lava, or before the wait after one is out, gives no flight:
    // LiquidGuard withholds it, and starts that wait over.
    @Override
    public void onActivate(ServerPlayer player) {
        if (!LiquidGuard.isWithheld(player.getUUID())) {
            PlayerFlight.grant(player);
        }
        LiquidGuard.restart(player.getUUID());
        FallTracker.clear(player.getUUID());
        AltitudeGuard.clear(player.getUUID());
    }

    // A gamemode switch back to survival/adventure unconditionally clears mayfly
    // (GameType.updatePlayerAbilities), with no notion that the orb granted it — so the
    // grant has to be defended every tick, not just asserted once on activation. Skipped while
    // withheld for a liquid, or this would undo LiquidGuard.check's own cutoff one tick later.
    @Override
    public void onTick(ServerPlayer player) {
        if (!player.getAbilities().mayfly && !LiquidGuard.isWithheld(player.getUUID())) {
            PlayerFlight.grant(player);
        }
        LiquidGuard.check(player);
        AltitudeGuard.check(player);
        Sputter.check(player);
        // After the guards and Sputter: within reach of water, flight is put back whatever ended
        // it.
        WaterLift.keepAloft(player);
        // Last of the flight changes, since the fence follows flight.
        AltitudeFence.update(player);
        FlightSpeed.update(player);
        FallTracker.follow(player);
    }

    @Override
    public void onDeactivate(ServerPlayer player) {
        // Read before revoke clears it: only a player the orb was holding up falls because of it,
        // not one scrolling past it mid-jump or mid-glide on Wax Wings.
        boolean wasFlying = player.getAbilities().flying;
        PlayerFlight.revoke(player);
        AltitudeFence.clear(player);
        FlightSpeed.reset(player);
        // Creative/spectator guarded to match the guards and FlightSpeed.update: without it,
        // letting go of the orb mid-air in one of those gamemodes then switching to survival while
        // still airborne wrongly earns the death message on a later fall.
        if (wasFlying && OrbOfLevitation.isManaged(player) && !player.onGround()) {
            FallTracker.markFalling(player.getUUID());
        }
    }
}
