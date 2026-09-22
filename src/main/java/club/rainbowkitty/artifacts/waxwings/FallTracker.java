package club.rainbowkitty.artifacts.waxwings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

// Tracks players left airborne without the wings' protection, until they land, catch flight
// again, or die, so a fall in between can be given the themed Icarus death message.
final class FallTracker {
    // UUID -> consecutive ticks spent onGround since re-landing; 0 while still airborne.
    private static final Map<UUID, Integer> FALLING = new HashMap<>();
    private static final int LANDING_GRACE_TICKS = 5;

    private FallTracker() {
    }

    // Starts tracking a player as falling without protection.
    static void markFalling(UUID uuid) {
        FALLING.put(uuid, 0);
    }

    // Stops tracking a player, e.g. on logout, re-equip, or a resolved landing.
    static void clear(UUID uuid) {
        FALLING.remove(uuid);
    }

    // Whether a player is currently tracked as falling without protection.
    static boolean isFalling(UUID uuid) {
        return FALLING.containsKey(uuid);
    }

    // Clears a tracked player once grounded for LANDING_GRACE_TICKS in a row -- a single grounded
    // tick isn't enough, since that's also the tick a fatal landing itself reports onGround.
    static void tick(MinecraftServer server) {
        FALLING.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            if (!player.onGround()) {
                entry.setValue(0);
                return false;
            }
            int groundedTicks = entry.getValue() + 1;
            if (groundedTicks < LANDING_GRACE_TICKS) {
                entry.setValue(groundedTicks);
                return false;
            }
            return true;
        });
    }

    // Clears the entry on any death, and returns the Icarus message if it was a tracked fall.
    static @Nullable Component icarianDeathMessage(ServerPlayer player, DamageSource source) {
        boolean wasFalling = FALLING.remove(player.getUUID()) != null;
        if (!wasFalling || !source.is(DamageTypeTags.IS_FALL)) {
            return null;
        }
        return Component.translatable("death.attack.artifacts.icarus", player.getDisplayName());
    }

    // Runs every tick, unlike AltitudeGuard.check, since it has to catch a mid-air catch on the
    // exact tick it happens. Only Icarus-message eligibility is at stake here now -- the mixin's
    // own fall-damage decision already re-protects a catch for free, live, with nothing to clear.
    static void resolveCatch(ServerPlayer player) {
        if (player.getAbilities().flying) {
            clear(player.getUUID());
        }
    }
}
