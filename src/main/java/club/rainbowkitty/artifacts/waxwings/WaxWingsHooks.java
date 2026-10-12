package club.rainbowkitty.artifacts.waxwings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

import club.rainbowkitty.kalic0re.common.effect.PlayerEffectHooks;

// PlayerEffectHooks glue between WaxWings.EFFECT and its per-tick classes, constructed once from
// WaxWings.
final class WaxWingsHooks implements PlayerEffectHooks {
    @Override
    public void onTick(ServerPlayer player) {
        WaxMelt.tick(player, player.getItemBySlot(EquipmentSlot.CHEST));
        BoostCatcher.update(player);
    }

    @Override
    public void onDeactivate(ServerPlayer player) {
        BoostCatcher.hide(player);
    }
}
