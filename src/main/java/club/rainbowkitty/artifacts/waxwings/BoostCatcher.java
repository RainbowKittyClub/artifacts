package club.rainbowkitty.artifacts.waxwings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

// An invisible Interaction entity, shown only to its owner, kept around their eye while they glide
// empty-handed. A vanilla client sends nothing for an empty-hand right-click into air, but it picks
// an entity whose box holds the eye ahead of anything else, so the click arrives here as an
// interact instead. See rkc/docs/artifacts/plan/orb-and-glider-wings.md, Finding 1.
final class BoostCatcher {
    private static final float SIZE = 4.0F;
    private static final double MILLIS_PER_TICK = 50.0;

    // UUID -> that player's catcher, created on their first glide and kept for the session.
    private static final Map<UUID, BoostCatcher> CATCHERS = new HashMap<>();

    private final ElementHolder holder = new ElementHolder();
    private Vec3 point = Vec3.ZERO;

    // The level the catcher is attached in, or null while it is hidden.
    private @Nullable ServerLevel level;

    private BoostCatcher() {
        InteractionElement element = new InteractionElement(new ClickHandler());
        element.setSize(SIZE, SIZE);
        element.setOffset(new Vec3(0.0, -SIZE / 2.0, 0.0));
        this.holder.addElement(element);
    }

    // Shows, moves or hides a player's catcher to match whether a click should boost right now.
    static void update(ServerPlayer player) {
        if (!shouldCatch(player)) {
            hide(player);
            return;
        }
        CATCHERS.computeIfAbsent(player.getUUID(), uuid -> new BoostCatcher()).show(player);
    }

    // Removes a player's catcher from their client, keeping it for the next glide.
    static void hide(ServerPlayer player) {
        BoostCatcher catcher = CATCHERS.get(player.getUUID());
        if (catcher != null) {
            catcher.detach();
        }
    }

    // Drops a disconnecting player's catcher entirely.
    static void forget(UUID uuid) {
        BoostCatcher catcher = CATCHERS.remove(uuid);
        if (catcher != null) {
            catcher.detach();
        }
    }

    // Only while a click could boost: the catcher swallows every use and attack aimed past it, so
    // an item in the main hand, or an off-hand rocket or bucket during the cooldown, keeps its
    // vanilla behaviour by keeping the catcher away.
    private static boolean shouldCatch(ServerPlayer player) {
        return player.isFallFlying() && player.getMainHandItem().isEmpty()
                && !player.isSpectator()
                && !player.getCooldowns().isOnCooldown(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    // Re-attaches in the player's current level when it changed, then moves to a fresh point. The
    // holder ticks before the first spawn packet so the entity appears where it belongs.
    private void show(ServerPlayer player) {
        ServerLevel current = player.level();
        this.point = catchPoint(player);
        if (this.level == current) {
            this.holder.tick();
            return;
        }
        detach();
        this.level = current;
        new ManualAttachment(this.holder, current, () -> this.point);
        this.holder.tick();
        this.holder.startWatching(player);
    }

    // Despawns the entity client-side; ElementHolder.destroy also drops the attachment.
    private void detach() {
        if (this.level == null) {
            return;
        }
        this.holder.destroy();
        this.level = null;
    }

    // The server's view of the eye trails the client's by the round trip, so the point leads it by
    // the client's last reported per-tick movement for that many ticks, plus the one being sent.
    private static Vec3 catchPoint(ServerPlayer player) {
        double leadTicks = 1.0 + player.connection.latency() / MILLIS_PER_TICK;
        return player.getEyePosition().add(player.getKnownMovement().scale(leadTicks));
    }

    // Routes a main-hand right-click on the catcher to the boost; attacks and picks are dropped.
    private static final class ClickHandler implements VirtualElement.InteractionHandler {
        @Override
        public void interact(ServerPlayer player, InteractionHand hand, Vec3 pos,
                boolean usingSecondaryAction) {
            if (hand == InteractionHand.MAIN_HAND) {
                WingBoost.tryBoost(player);
            }
        }
    }
}
