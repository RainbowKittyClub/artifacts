package club.rainbowkitty.artifacts;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

import club.rainbowkitty.kalic0re.common.enchant.EnchantSpec;
import club.rainbowkitty.kalic0re.common.enchant.HiddenEquipmentStyle;
import club.rainbowkitty.kalic0re.common.enchant.HideEquipmentModelEnchantments;

/**
 * A purely cosmetic enchantment for armor, tools and weapons: the item it is on renders
 * invisible while its enchantment glint still shows, through
 * {@link HideEquipmentModelEnchantments} and a client-side mixin on {@code EquipmentLayerRenderer}
 * (see {@code mixin/client/EquipmentLayerRendererMixin}). That mixin only covers the equipped-armor
 * render path; a held tool or weapon still shows its normal model for now.
 *
 * <p>{@link HiddenEquipmentStyle#HIDDEN_GLINT} leaves the wearer's own body intact under the hidden
 * piece; {@link HiddenEquipmentStyle#PHANTOM_GLINT} instead cuts a piece-shaped hole through them.
 *
 * <p>Needs no {@link Enchantment.Builder#withEffect} of its own — every enchanted item already has
 * a foil regardless of whether it does anything mechanically, which is the whole trick.
 *
 * <p>Its payoff is entirely client-side rendering: only a client running this mod's mixins sees the
 * hidden model, and a vanilla client sees a plain enchanted item. See
 * {@code docs/plan/shadow-vanilla-fallback.md} for why a resource-pack-only substitute can't cover
 * worn armor, and where it can for a held tool or weapon.
 */
public final class Shadow {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Main.MOD_ID, "shadow"));

    /** Armor, plus melee weapons, axes/pickaxes/shovels/hoes and shears — see the datagen tag. */
    public static final TagKey<Item> SUPPORTED_ITEMS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Main.MOD_ID, "shadow_enchantable"));

    public static final EnchantSpec SPEC = EnchantSpec.of(KEY, Shadow::builder);

    private Shadow() {
    }

    /** Registers the hide-model-keep-foil flag; call once from {@link Main#onInitialize}. */
    public static void init() {
        HideEquipmentModelEnchantments.register(KEY, HiddenEquipmentStyle.HIDDEN_GLINT);
    }

    private static Enchantment.Builder builder(HolderLookup.Provider registries) {
        HolderSet<Item> supportedItems = registries.lookupOrThrow(Registries.ITEM)
                .getOrThrow(SUPPORTED_ITEMS);
        return Enchantment.enchantment(Enchantment.definition(supportedItems, 10, 1,
                Enchantment.constantCost(15), Enchantment.constantCost(65), 4,
                EquipmentSlotGroup.ARMOR, EquipmentSlotGroup.MAINHAND));
    }
}
