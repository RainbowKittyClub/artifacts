package club.rainbowkitty.artifacts;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import club.rainbowkitty.kalic0re.common.effect.PlayerEffect;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectAttributes;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectDispatcher;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectHooks;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectSource;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;

/**
 * Hermes' winged sandals: +35% movement speed while worn, through kalic0re's {@code common/effect}
 * framework rather than a plain {@code ItemAttributeModifiers} component — the boost applies only
 * while {@link PlayerEffectDispatcher} sees them equipped, and is removed the tick they come off.
 *
 * <p>Clients without the server's resource pack see {@link Items#COPPER_BOOTS}; the name and lore
 * are {@code ITEM_NAME}/{@code LORE} components rather than lang keys precisely so those clients
 * still read the right thing. Art is a placeholder.
 */
public final class Talaria {
    private static final double SPEED_BONUS = 0.35;

    private static final Identifier ID = Identifier.fromNamespaceAndPath(Main.MOD_ID, "talaria");
    private static final ResourceKey<EquipmentAsset> ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, ID);

    public static final Item ITEM = ItemRegistration.register(Main.MOD_ID, "talaria",
            key -> new PolymerModelItem(Items.COPPER_BOOTS, ID, new Item.Properties()
                    .setId(key)
                    .component(DataComponents.EQUIPPABLE,
                            Equippable.builder(EquipmentSlot.FEET).setAsset(ASSET).build())
                    .component(DataComponents.ITEM_NAME, Component.literal("Talaria"))
                    .component(DataComponents.LORE, lore())));

    public static final PlayerEffect EFFECT = new PlayerEffect(Main.MOD_ID, "talaria_speed",
            PlayerEffectSource.equipped(EquipmentSlotGroup.FEET, ITEM), new Hooks());

    private Talaria() {
    }

    /** Registers the effect; call once from {@link Main#onInitialize}. */
    public static void init() {
        PlayerEffectDispatcher.register(EFFECT);
    }

    // Italic is off explicitly: ItemLore merges dark-purple-italic in as a fallback, so a line
    // that sets neither would come out italic and leave no way to emphasise a word within it.
    private static ItemLore lore() {
        return new ItemLore(List.of(
                Component.literal("They say the man who wore these stole from the gods...")
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                                .withItalic(false))));
    }

    private static final class Hooks implements PlayerEffectHooks {
        @Override
        public void onActivate(ServerPlayer player) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed == null) {
                return;
            }
            Identifier id = PlayerEffectAttributes.modifierId(EFFECT, Attributes.MOVEMENT_SPEED);
            AttributeModifier.Operation op = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            speed.addOrUpdateTransientModifier(new AttributeModifier(id, SPEED_BONUS, op));
        }

        @Override
        public void onDeactivate(ServerPlayer player) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed == null) {
                return;
            }
            Identifier id = PlayerEffectAttributes.modifierId(EFFECT, Attributes.MOVEMENT_SPEED);
            speed.removeModifier(id);
        }
    }
}
