package club.rainbowkitty.artifacts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.death.DeathMessageOverrides;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffect;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectDispatcher;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectHooks;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectSource;
import club.rainbowkitty.kalic0re.common.effect.PlayerFlight;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;

/**
 * A chestplate granting creative-like flight while worn, through kalic0re's {@code common/effect}
 * framework and its {@link PlayerFlight} helper — the grant holds only while
 * {@link PlayerEffectDispatcher} sees the wings equipped, and is released the tick they come off.
 *
 * <p>Clients without the server's resource pack see {@link Items#ELYTRA}. Art is a placeholder.
 *
 * <p>A player who loses the wings while still airborne is tracked in {@link #FALLING} until they
 * either land safely (cleared by the {@link #clearLandedPlayers} tick scan, once {@code onGround}
 * has held for {@link #LANDING_GRACE_TICKS} in a row — a single grounded tick isn't enough, since
 * that is also the tick a fatal landing itself reports {@code onGround}) or regain flight (cleared
 * in {@link Hooks#onActivate}); dying to fall damage in between gets the themed
 * {@code death.attack.artifacts.icarus} message via kalic0re's {@code common/death} registry, since
 * the actual redirect mixin can't be shared code — see {@code mixin/DeathMessageMixin}.
 *
 * <p>{@link #checkObstruction} runs every tick, regardless of whether the player is currently
 * flying: whenever they're in any liquid, on fire, or any part of their own volume overlaps a
 * cobweb or sweet berry bush foliage, both {@code flying} and {@code mayfly} are withheld
 * immediately (no streak — there's no measurement noise to tolerate the way altitude has), tracked
 * in {@link #OBSTRUCTED} so {@code mayfly} comes back only once a later tick finds them clear of
 * it, not the moment they physically leave it. Withholding {@code mayfly} itself, not just active
 * flight, is deliberate: it's what stops a launch from inside the obstruction, not only an
 * in-progress one. {@link #checkAltitude} is the separate, gentler cutoff: throttled to once every
 * {@link #FLIGHT_CHECK_INTERVAL_TICKS} and only evaluated while actually {@code flying}, it cuts
 * just {@code flying} (leaving {@code mayfly} granted for a quick catch) after
 * {@link #OVERHEIGHT_TRIGGER_CHECKS} consecutive checks reading them more than
 * {@link #MAX_SAFE_HEIGHT} blocks above solid ground (leaves and scaffolding don't count as ground;
 * dropping back under it for even one check resets that streak). That leaves the vanilla
 * no-fall-damage short-circuit ({@code Player.causeFallDamage} returns early whenever
 * {@code mayfly} is true, at all, regardless of {@code flying}) still in the way — worse, that
 * short-circuit is why the wings give total fall immunity merely by being worn, {@code flying} or
 * not, which was never the intent. {@code mixin/OverheightFallDamageMixin} corrects the rule itself
 * instead of special-casing either cutoff: for a wings wearer specifically (everyone else's mayfly
 * is untouched), it reports {@code mayfly} as false whenever {@link #isUnprotectedFall} says they
 * aren't currently {@code flying}, live at the moment vanilla asks — no tracking needed, since
 * {@code causeFallDamage} only ever fires at the instant of landing, so "not flying right now" and
 * "not flying at the moment of landing" are the same check. Catching themselves before landing
 * (re-engaging {@code flying}) is protected again for free the same way, with nothing to clear.
 * {@link #checkAltitude} additionally seeds {@link #FALLING} on its own cutoff specifically (not an
 * obstruction one — "flew too close to the sun" wouldn't fit), purely so the death message reads as
 * Icarus, same as an unequip mid-air — that bookkeeping has nothing to do with whether the fall
 * itself hurts.
 *
 * <p>{@link #updateFlyingSpeed} sets {@link Abilities#setFlyingSpeed} to
 * {@link #FLYING_SPEED_MULTIPLIER} of vanilla's own default, further cut to
 * {@link #RAIN_FLYING_SPEED_MULTIPLIER} of that while {@link #isRainingOn} the player, every tick
 * so a storm rolling in or clearing mid-flight takes effect immediately. {@link #resetFlyingSpeed}
 * restores vanilla's value on unequip, since nothing else ever would.
 */
public final class WaxWings {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Main.MOD_ID, "wax_wings");
    private static final ResourceKey<EquipmentAsset> ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, ID);

    public static final Item ITEM = ItemRegistration.register(Main.MOD_ID, "wax_wings",
            key -> new PolymerModelItem(Items.ELYTRA, ID, new Item.Properties()
                    .setId(key)
                    .component(DataComponents.EQUIPPABLE,
                            Equippable.builder(EquipmentSlot.CHEST).setAsset(ASSET).build())
                    .component(DataComponents.ITEM_NAME, Component.literal("Wax Wings"))
                    .component(DataComponents.LORE, lore())));

    public static final PlayerEffect EFFECT = new PlayerEffect(Main.MOD_ID, "wax_wings_flight",
            PlayerEffectSource.equipped(EquipmentSlotGroup.CHEST, ITEM), new Hooks());

    // UUID -> consecutive ticks spent onGround since re-landing; 0 while still airborne.
    private static final Map<UUID, Integer> FALLING = new HashMap<>();
    private static final int LANDING_GRACE_TICKS = 5;

    private static final int FLIGHT_CHECK_INTERVAL_TICKS = 20;
    private static final int OVERHEIGHT_TRIGGER_CHECKS = 10;
    private static final int MAX_SAFE_HEIGHT = 60;

    // Matches Entity#getFluidInteractionBox's own deflate: without it, a player standing with feet
    // exactly on a solid floor reads as overlapping that floor's block too, at the shared boundary.
    private static final double OBSTRUCTION_BOX_EPSILON = 0.001;

    // UUID -> consecutive altitude checks in a row a flying player has read as overheight; reset
    // the moment a check finds them back at or under MAX_SAFE_HEIGHT, or cut off for any other
    // reason.
    private static final Map<UUID, Integer> OVERHEIGHT_STREAK = new HashMap<>();

    // Players whose mayfly is being withheld for being obstructed (in a liquid, on fire, a
                // cobweb, or berry bush foliage). Hooks#onTick's usual "defend the grant" must skip
    // these players, or it would undo checkObstruction's own cutoff one tick later -- only
    // checkObstruction's own next check, once it finds them clear, restores it.
    private static final Set<UUID> OBSTRUCTED = new HashSet<>();

    // Read off a fresh Abilities rather than hardcoding vanilla's 0.05F, so a future vanilla change
    // to the default can't silently drift out of sync with a duplicated literal here.
    private static final float VANILLA_FLYING_SPEED = new Abilities().getFlyingSpeed();

    private static final float FLYING_SPEED_MULTIPLIER = 0.85F;
    private static final float RAIN_FLYING_SPEED_MULTIPLIER = 0.5F;

    private WaxWings() {
    }

    /** Registers the effect and death-message override; call once from {@link Main}. */
    public static void init() {
        PlayerEffectDispatcher.register(EFFECT);
        ServerTickEvents.END_SERVER_TICK.register(WaxWings::clearLandedPlayers);
        ServerPlayerEvents.LEAVE.register(player -> {
            FALLING.remove(player.getUUID());
            OVERHEIGHT_STREAK.remove(player.getUUID());
            OBSTRUCTED.remove(player.getUUID());
        });
        DeathMessageOverrides.register(WaxWings::icarianDeathMessage);
    }

    /**
     * Whether {@code player}'s mayfly-driven fall immunity should NOT apply right now: wearing the
     * wings only protects a fall while they're actually using the flight it grants, not merely
     * while worn.
     */
    public static boolean isUnprotectedFall(ServerPlayer player) {
        return EFFECT.source().isActive(player) && !player.getAbilities().flying;
    }

    // Lore is default italic so we build all the formatting manually
    private static ItemLore lore() {
        return new ItemLore(List.of(Component.literal("Don't fly ")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))
                .append(Component.literal("too").withStyle(style -> style.withItalic(true)))
                .append(Component.literal(" high with these..."))));
    }

    private static void clearLandedPlayers(MinecraftServer server) {
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

    // Clears the entry on any death
    private static @Nullable Component icarianDeathMessage(ServerPlayer player,
            DamageSource source) {
        boolean wasFalling = FALLING.remove(player.getUUID()) != null;
        if (!wasFalling || !source.is(DamageTypeTags.IS_FALL)) {
            return null;
        }
        return Component.translatable("death.attack.artifacts.icarus", player.getDisplayName());
    }

    // Runs every tick, not throttled: unlike altitude there's no measurement noise to tolerate, and
    // a fast-moving flying player would otherwise skim through an obstruction between samples and
    // never get caught. Applies regardless of whether the player is currently flying -- mayfly
    // itself is withheld while obstructed, not just active flight, so they can't launch from inside
    // one either. Set#add/#remove's own return value gates the actual ability change to the exact
    // transition tick, instead of re-syncing abilities every tick spent stuck.
    private static void checkObstruction(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        UUID uuid = player.getUUID();
        if (isObstructed(player)) {
            if (OBSTRUCTED.add(uuid)) {
                PlayerFlight.revoke(player);
                OVERHEIGHT_STREAK.remove(uuid);
            }
            return;
        }
        if (OBSTRUCTED.remove(uuid)) {
            PlayerFlight.grant(player);
        }
    }

    // True while the player is in any liquid (Entity#isInLiquid: water or lava) or on fire, or any
    // part of their own volume overlaps a cobweb or sweet berry bush. The bounding-box scan is
    // deflated the same way Entity#getFluidInteractionBox is, so standing with feet exactly on a
    // solid floor -- whose top face touches the bounding box's own bottom face -- doesn't count as
    // overlapping it.
    private static boolean isObstructed(ServerPlayer player) {
        if (player.isInLiquid() || player.isOnFire()) {
            return true;
        }
        ServerLevel level = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                player.getBoundingBox().deflate(OBSTRUCTION_BOX_EPSILON))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.COBWEB) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.VINE)) {
                return true;
            }
        }
        return false;
    }

    // Throttled to FLIGHT_CHECK_INTERVAL_TICKS (1s) off the server's own tick count rather than a
    // per-player counter, since a fixed cadence needs no per-player state to land on it. A flying
    // player has to read as overheight on OVERHEIGHT_TRIGGER_CHECKS checks in a row (10s worth)
    // before this cutoff fires, and dropping back under MAX_SAFE_HEIGHT for even one check resets
    // that streak to zero. Only ever fires while actually flying -- height above the ground is
    // meaningless otherwise, and an obstructed player already has flying == false from
    // checkObstruction, so this is naturally a no-op then.
    private static void checkAltitude(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.level().getServer().getTickCount() % FLIGHT_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        UUID uuid = player.getUUID();

        if (FALLING.containsKey(uuid) || !player.getAbilities().flying) {
            OVERHEIGHT_STREAK.remove(uuid);
            return;
        }

        int height = heightAboveGround(player);
        if (height <= MAX_SAFE_HEIGHT) {
            OVERHEIGHT_STREAK.remove(uuid);
            return;
        }

        int streak = OVERHEIGHT_STREAK.merge(uuid, 1, Integer::sum);
        if (streak < OVERHEIGHT_TRIGGER_CHECKS) {
            return;
        }
        OVERHEIGHT_STREAK.remove(uuid);

        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        // Icarus-eligible only for the altitude reason; "flew too close to the sun" wouldn't fit an
        // obstruction-triggered cutoff.
        FALLING.put(uuid, 0);
    }

    // Recomputed every tick, since rain comes and goes while still airborne. Only touches the
    // ability when the value actually changes, same care as checkObstruction. Skips creative and
    // spectator for the same reason PlayerFlight does: that flight is the gamemode's own, not the
    // wings', and isn't this effect's to slow down. Skips a grounded wearer too: the value only
    // matters once flying, so there's nothing to recompute until then.
    private static void updateFlyingSpeed(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || !player.getAbilities().flying) {
            return;
        }
        float speed = VANILLA_FLYING_SPEED * FLYING_SPEED_MULTIPLIER
                * (isRainingOn(player) ? RAIN_FLYING_SPEED_MULTIPLIER : 1F);
        if (player.getAbilities().getFlyingSpeed() != speed) {
            player.getAbilities().setFlyingSpeed(speed);
            player.onUpdateAbilities();
        }
    }

    // Mirrors Entity's own private isInRain(): rained on at either the feet or the head, so
    // standing under a roof during a storm doesn't count.
    private static boolean isRainingOn(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        return level.isRainingAt(pos)
                || level.isRainingAt(BlockPos.containing(pos.getX(), player.getBoundingBox().maxY,
                        pos.getZ()));
    }

    // Vanilla never resets this on its own once something has changed it, so leaving the wings off
    // without this would leave a later, unrelated flight source (creative mode, another mod) slow.
    private static void resetFlyingSpeed(ServerPlayer player) {
        if (player.getAbilities().getFlyingSpeed() != VANILLA_FLYING_SPEED) {
            player.getAbilities().setFlyingSpeed(VANILLA_FLYING_SPEED);
            player.onUpdateAbilities();
        }
    }

    // Runs every tick, unlike checkAltitude, since it has to catch a mid-air catch on the exact
    // tick it happens. Only Icarus-message eligibility is at stake here now -- the mixin's own
    // fall-damage decision already re-protects a catch for free, live, with nothing to clear.
    private static void resolveCatch(ServerPlayer player) {
        if (player.getAbilities().flying) {
            // Caught themselves: clears Icarus-eligibility too, so a later unrelated death doesn't
            // read as one. clearLandedPlayers only resolves a real landing, and a catch may not
            // touch ground again for a long time, so this has to clear FALLING itself instead of
            // waiting on that scan.
            FALLING.remove(player.getUUID());
        }
    }

    // Scans straight down from the player's feet for the first block that counts as ground, capped
    // at one past the safe height since anything beyond that already fails the check either way.
    private static int heightAboveGround(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        for (int dy = 1; dy <= MAX_SAFE_HEIGHT + 1; dy++) {
            if (isGround(level, feet.below(dy))) {
                return dy - 1;
            }
        }
        return MAX_SAFE_HEIGHT + 1;
    }

    // Leaves and scaffolding don't count as ground: flying above a canopy or a scaffold tower
    // shouldn't read as safe. Fluids fall through too, since their collision shape is empty.
    private static boolean isGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(Blocks.SCAFFOLDING)) {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static final class Hooks implements PlayerEffectHooks {
        @Override
        public void onActivate(ServerPlayer player) {
            PlayerFlight.grant(player);
            FALLING.remove(player.getUUID());
            OBSTRUCTED.remove(player.getUUID());
        }

        // A gamemode switch back to survival/adventure unconditionally clears mayfly
        // (GameType.updatePlayerAbilities), with no notion that the wings granted it — so the
        // grant has to be defended every tick, not just asserted once on activation. Skipped while
        // OBSTRUCTED, or this would undo checkObstruction's own cutoff one tick later.
        @Override
        public void onTick(ServerPlayer player) {
            if (!player.getAbilities().mayfly && !OBSTRUCTED.contains(player.getUUID())) {
                PlayerFlight.grant(player);
            }
            checkObstruction(player);
            checkAltitude(player);
            updateFlyingSpeed(player);
            resolveCatch(player);
        }

        @Override
        public void onDeactivate(ServerPlayer player) {
            PlayerFlight.revoke(player);
            OBSTRUCTED.remove(player.getUUID());
            resetFlyingSpeed(player);
            if (!player.onGround()) {
                FALLING.put(player.getUUID(), 0);
            }
        }
    }
}
