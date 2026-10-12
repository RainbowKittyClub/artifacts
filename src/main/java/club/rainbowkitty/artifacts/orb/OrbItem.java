package club.rainbowkitty.artifacts.orb;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;

// The orb's item: a PolymerModelItem whose durability is how wet it is (Wetness), drying while it
// sits in an inventory. Like the water, the rain reaches it only in a hand: one carried elsewhere
// dries on. On the ground, OrbOfLevitation.tickDropped does the same.
final class OrbItem extends PolymerModelItem {
    OrbItem(Item fallback, Identifier model, Properties properties) {
        super(fallback, model, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner,
            @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        boolean held = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        // A held orb is out in whatever its holder is in: LiquidGuard soaks it in water, and the
        // rain keeps it as wet as it is.
        if (!held || !owner.isInWaterOrRain()) {
            Wetness.dry(stack, level.getGameTime());
        }
    }
}
