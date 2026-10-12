package club.rainbowkitty.artifacts.orb;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

// Tracks players left airborne without the orb's protection, until they land, catch flight
// again, or die, so a fall in between can be given the orb's themed death message.
final class FallTracker {
    // UUID -> consecutive ticks spent onGround since re-landing; 0 while still airborne.
    private static final Map<UUID, Integer> FALLING = new HashMap<>();
    private static final int LANDING_GRACE_TICKS = 5;

    // Holders flying at the end of their last tick with the orb, so flight switched off mid-air
    // can be caught on the tick it happens.
    private static final Set<UUID> FLYING = new HashSet<>();

    private FallTracker() {
    }

    // Starts tracking a player as falling without protection.
    static void markFalling(UUID uuid) {
        FALLING.put(uuid, 0);
    }

    // Stops tracking a player, e.g. on logout or re-equip.
    static void clear(UUID uuid) {
        FALLING.remove(uuid);
        FLYING.remove(uuid);
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

    // Clears the entry on any death, and returns the orb's message if it was a tracked fall.
    static @Nullable Component deathMessage(ServerPlayer player, DamageSource source) {
        boolean wasFalling = FALLING.remove(player.getUUID()) != null;
        if (!wasFalling || !source.is(DamageTypeTags.IS_FALL)) {
            return null;
        }
        return Component.translatable("death.attack.artifacts.orb_of_levitation",
                player.getDisplayName());
    }

    // Runs every tick, last of the orb's flight changes, since it has to catch the exact tick
    // flight starts or stops. Flying again is a mid-air catch, and ends a tracked fall. Stopping
    // mid-air starts one, whatever stopped it: the holder switching flight off themselves as much
    // as a cutoff, which marks its own. Not in a liquid, whose cutoff is the world's doing and
    // breaks the fall anyway, nor in creative or spectator, as in OrbHooks.onDeactivate. Only
    // death-message eligibility is at stake here -- the mixin's own fall-damage decision already
    // follows flight live, with nothing to mark or clear.
    static void follow(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (player.getAbilities().flying) {
            FALLING.remove(uuid);
            FLYING.add(uuid);
            return;
        }
        if (FLYING.remove(uuid) && !player.onGround() && !player.isInLiquid()
                && OrbOfLevitation.isManaged(player)) {
            markFalling(uuid);
        }
    }
}
