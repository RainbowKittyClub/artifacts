package club.rainbowkitty.artifacts.waxwings;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import club.rainbowkitty.artifacts.ArtifactNames;
import club.rainbowkitty.artifacts.Main;
import club.rainbowkitty.kalic0re.common.death.DeathMessageOverrides;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffect;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectDispatcher;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectSource;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;

/**
 * Icarus' wings: an elytra in every respect but three — half the durability, repaired with
 * honeycomb instead of phantom membrane, and worn faster the higher they fly. A right-click with
 * an empty main hand while gliding boosts like a firework rocket, on a cooldown and for durability.
 *
 * <p>Clients without the server's resource pack see {@link Items#ELYTRA}, and glide on it off the
 * components Polymer copies across; art is still placeholder. See
 * {@code docs/plan/orb-and-glider-wings.md} for how the boost reaches a vanilla client.
 */
public final class WaxWings {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Main.MOD_ID, "wax_wings");
    private static final ResourceKey<EquipmentAsset> ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, ID);

    // Half the elytra's 432. Not read off Items.ELYTRA: an item's components only bind when the
    // registry freezes (Holder.Reference.components), after this item has to be registered.
    private static final int DURABILITY = 432 / 2;

    public static final Item ITEM = ItemRegistration.register(Main.MOD_ID, "wax_wings",
            key -> ArtifactNames.named(key, "Wax Wings",
                    new PolymerModelItem(Items.ELYTRA, ID, new Item.Properties()
                            .setId(key)
                            .durability(DURABILITY)
                            .repairable(Items.HONEYCOMB)
                            .component(DataComponents.GLIDER, Unit.INSTANCE)
                            .component(DataComponents.EQUIPPABLE,
                                    Equippable.builder(EquipmentSlot.CHEST)
                                            .setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
                                            .setAsset(ASSET)
                                            .setDamageOnHurt(false)
                                            .build())
                            .component(DataComponents.LORE, lore()))));

    public static final PlayerEffect EFFECT = new PlayerEffect(Main.MOD_ID, "wax_wings_glide",
            PlayerEffectSource.equipped(EquipmentSlotGroup.CHEST, ITEM), new WaxWingsHooks());

    private WaxWings() {
    }

    /**
     * Registers the effect and the Icarus death message; call once from {@link Main}, after
     * {@code OrbOfLevitation.init()} so the orb's stateful death message clears its entry first.
     */
    public static void init() {
        PlayerEffectDispatcher.register(EFFECT);
        ServerPlayerEvents.LEAVE.register(player -> BoostCatcher.forget(player.getUUID()));
        DeathMessageOverrides.register(WaxWings::icarianDeathMessage);
    }

    // A fall death in wings worn to their last point. Stateless: ServerPlayer.die builds the
    // message before it drops the inventory, so the chest slot still says why they fell.
    private static @Nullable Component icarianDeathMessage(ServerPlayer player,
            DamageSource source) {
        ItemStack wings = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!source.is(DamageTypeTags.IS_FALL) || !wings.is(ITEM) || !wings.nextDamageWillBreak()) {
            return null;
        }
        return Component.translatable("death.attack.artifacts.icarus", player.getDisplayName());
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
