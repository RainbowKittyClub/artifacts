package club.rainbowkitty.artifacts.waxwings;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import club.rainbowkitty.artifacts.Main;
import club.rainbowkitty.kalic0re.common.death.DeathMessageOverrides;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffect;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectDispatcher;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectSource;
import club.rainbowkitty.kalic0re.common.effect.PlayerFlight;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;

/**
 * A chestplate granting creative-like flight while worn, through kalic0re's {@code common/effect}
 * framework and its {@link PlayerFlight} helper — the grant holds only while
 * {@link PlayerEffectDispatcher} sees the wings equipped, and is released the tick they come off.
 * Clients without the server's resource pack see {@link Items#ELYTRA}; art is still placeholder.
 *
 * <p>Flight is bounded by a handful of sibling classes in this package, each a single concern
 * split out of what used to be one file — see {@code docs/plan/waxwings-package-split.md}'s
 * "Reference: Flight tracking mechanics" section for the full write-up of how they interact.
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
            PlayerEffectSource.equipped(EquipmentSlotGroup.CHEST, ITEM), new WaxWingsHooks());

    private WaxWings() {
    }

    /** Registers the effect and death-message override; call once from {@link Main}. */
    public static void init() {
        PlayerEffectDispatcher.register(EFFECT);
        ServerTickEvents.END_SERVER_TICK.register(FallTracker::tick);
        ServerPlayerEvents.LEAVE.register(player -> {
            FallTracker.clear(player.getUUID());
            AltitudeGuard.clear(player.getUUID());
            ObstructionGuard.clear(player.getUUID());
        });
        DeathMessageOverrides.register(FallTracker::icarianDeathMessage);
    }

    /**
     * Whether {@code player}'s mayfly-driven fall immunity should NOT apply right now: wearing the
     * wings only protects a fall while they're actually using the flight it grants, not merely
     * while worn. Creative/spectator guarded like every other check in this package -- their own
     * mayfly isn't the wings' to withhold.
     */
    public static boolean isUnprotectedFall(ServerPlayer player) {
        if (!isManaged(player)) {
            return false;
        }
        return EFFECT.source().isActive(player) && !player.getAbilities().flying;
    }

    // Whether every sibling class's flight-management logic even applies to this player:
    // creative/spectator already fly for free, on their own terms, so none of this package's
    // guards, cutoffs, or speed changes are the wings' business while a player is in one.
    static boolean isManaged(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator();
    }

    // "too" is the one italic run: ItemLore would otherwise italicise the whole line as a
    // fallback, which would leave the emphasis with nothing to contrast against.
    private static ItemLore lore() {
        return new ItemLore(List.of(Component.literal("Don't fly ")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))
                .append(Component.literal("too").withStyle(style -> style.withItalic(true)))
                .append(Component.literal(" high with these..."))));
    }
}
