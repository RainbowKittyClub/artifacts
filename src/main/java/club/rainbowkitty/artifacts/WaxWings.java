package club.rainbowkitty.artifacts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import club.rainbowkitty.rkcore.common.death.DeathMessageOverrides;
import club.rainbowkitty.rkcore.common.effect.PlayerEffect;
import club.rainbowkitty.rkcore.common.effect.PlayerEffectDispatcher;
import club.rainbowkitty.rkcore.common.effect.PlayerEffectHooks;
import club.rainbowkitty.rkcore.common.effect.PlayerEffectSource;
import club.rainbowkitty.rkcore.common.effect.PlayerFlight;
import club.rainbowkitty.rkcore.common.item.ItemRegistration;
import club.rainbowkitty.rkcore.common.item.PolymerModelItem;

/**
 * A chestplate granting creative-like flight while worn, through rkcore's {@code common/effect}
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
 * {@code death.attack.artifacts.icarus} message via rkcore's {@code common/death} registry, since
 * the actual redirect mixin can't be shared code — see {@code mixin/DeathMessageMixin}.
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

    private WaxWings() {
    }

    /** Registers the effect and death-message override; call once from {@link Main}. */
    public static void init() {
        PlayerEffectDispatcher.register(EFFECT);
        ServerTickEvents.END_SERVER_TICK.register(WaxWings::clearLandedPlayers);
        ServerPlayerEvents.LEAVE.register(player -> FALLING.remove(player.getUUID()));
        DeathMessageOverrides.register(WaxWings::icarianDeathMessage);
    }

    // "too" is the one italic run: ItemLore would otherwise italicise the whole line as a
    // fallback, which would leave the emphasis with nothing to contrast against.
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

    // Clears the entry on any death, not just a fall: the story ends at death regardless of cause.
    private static @Nullable Component icarianDeathMessage(ServerPlayer player,
            DamageSource source) {
        boolean wasFalling = FALLING.remove(player.getUUID()) != null;
        if (!wasFalling || !source.is(DamageTypeTags.IS_FALL)) {
            return null;
        }
        return Component.translatable("death.attack.artifacts.icarus", player.getDisplayName());
    }

    private static final class Hooks implements PlayerEffectHooks {
        @Override
        public void onActivate(ServerPlayer player) {
            PlayerFlight.grant(player);
            FALLING.remove(player.getUUID());
        }

        // A gamemode switch back to survival/adventure unconditionally clears mayfly
        // (GameType.updatePlayerAbilities), with no notion that the wings granted it — so the
        // grant has to be defended every tick, not just asserted once on activation.
        @Override
        public void onTick(ServerPlayer player) {
            if (!player.getAbilities().mayfly) {
                PlayerFlight.grant(player);
            }
        }

        @Override
        public void onDeactivate(ServerPlayer player) {
            PlayerFlight.revoke(player);
            if (!player.onGround()) {
                FALLING.put(player.getUUID(), 0);
            }
        }
    }
}
