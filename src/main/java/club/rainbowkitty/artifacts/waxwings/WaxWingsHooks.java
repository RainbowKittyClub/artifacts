package club.rainbowkitty.artifacts.waxwings;

import net.minecraft.server.level.ServerPlayer;

import club.rainbowkitty.kalic0re.common.effect.PlayerEffectHooks;
import club.rainbowkitty.kalic0re.common.effect.PlayerFlight;

// PlayerEffectHooks glue between WaxWings.EFFECT and its three tracking classes, constructed once
// from WaxWings.
final class WaxWingsHooks implements PlayerEffectHooks {
    @Override
    public void onActivate(ServerPlayer player) {
        PlayerFlight.grant(player);
        FallTracker.clear(player.getUUID());
        ObstructionGuard.clear(player.getUUID());
    }

    // A gamemode switch back to survival/adventure unconditionally clears mayfly
    // (GameType.updatePlayerAbilities), with no notion that the wings granted it — so the
    // grant has to be defended every tick, not just asserted once on activation. Skipped while
    // withheld for an obstruction, or this would undo ObstructionGuard.check's own cutoff one
    // tick later.
    @Override
    public void onTick(ServerPlayer player) {
        if (!player.getAbilities().mayfly && !ObstructionGuard.isWithheld(player.getUUID())) {
            PlayerFlight.grant(player);
        }
        ObstructionGuard.check(player);
        AltitudeGuard.check(player);
        FlightSpeed.update(player);
        FallTracker.resolveCatch(player);
    }

    @Override
    public void onDeactivate(ServerPlayer player) {
        PlayerFlight.revoke(player);
        ObstructionGuard.clear(player.getUUID());
        FlightSpeed.reset(player);
        // Creative/spectator guarded to match ObstructionGuard.check/AltitudeGuard.check/
        // FlightSpeed.update (Finding 2): without it, unequipping mid-air in one of those
        // gamemodes then switching to survival while still airborne wrongly earns the Icarus
        // message on a later fall.
        if (!player.isCreative() && !player.isSpectator() && !player.onGround()) {
            FallTracker.markFalling(player.getUUID());
        }
    }
}
