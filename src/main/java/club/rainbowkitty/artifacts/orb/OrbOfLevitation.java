package club.rainbowkitty.artifacts.orb;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import club.rainbowkitty.artifacts.ArtifactNames;
import club.rainbowkitty.artifacts.Main;
import club.rainbowkitty.kalic0re.common.death.DeathMessageOverrides;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffect;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectDispatcher;
import club.rainbowkitty.kalic0re.common.effect.PlayerEffectSource;
import club.rainbowkitty.kalic0re.common.effect.PlayerFlight;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;

/**
 * A held orb granting creative-like flight, through kalic0re's {@code common/effect} framework and
 * its {@link PlayerFlight} helper — the grant holds only while {@link PlayerEffectDispatcher} sees
 * the orb in either hand, and is released the tick it leaves both. Clients without the server's
 * resource pack see {@link Items#HEART_OF_THE_SEA}; art is still placeholder.
 *
 * <p>Flight is bounded by the sibling classes in this package, each a single concern — see
 * {@code docs/plan/waxwings-package-split.md}'s "Reference: Flight tracking mechanics" for how they
 * interact, and {@code docs/plan/orb-and-glider-wings.md} for how they came to hold an orb.
 */
public final class OrbOfLevitation {
    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(Main.MOD_ID, "orb_of_levitation");
    private static final Identifier WATERLOGGED_ID =
            Identifier.fromNamespaceAndPath(Main.MOD_ID, "waterlogged_orb_of_levitation");

    public static final Item ITEM = ItemRegistration.register(Main.MOD_ID, "orb_of_levitation",
            key -> ArtifactNames.named(key, "Orb of Levitation",
                    new OrbItem(Items.HEART_OF_THE_SEA, ID, new Item.Properties()
                            .setId(key)
                            .durability(Wetness.POINTS)
                            .component(DataComponents.LORE, lore()))));

    /**
     * The orb once soaked through in its holder's hand ({@link Wetness}): it gives no flight, and
     * dries back into {@link #ITEM} in a furnace or on a campfire. Same fallback as the orb, its
     * own model.
     */
    public static final Item WATERLOGGED_ITEM = ItemRegistration.register(Main.MOD_ID,
            "waterlogged_orb_of_levitation",
            key -> ArtifactNames.named(key, "Waterlogged Orb of Levitation",
                    new PolymerModelItem(Items.HEART_OF_THE_SEA, WATERLOGGED_ID,
                            new Item.Properties()
                                    .setId(key)
                                    .stacksTo(1)
                                    .component(DataComponents.LORE, waterloggedLore()))));

    public static final PlayerEffect EFFECT = new PlayerEffect(Main.MOD_ID,
            "orb_of_levitation_flight", PlayerEffectSource.held(ITEM), new OrbHooks());

    private OrbOfLevitation() {
    }

    /** Registers the effect and death-message override; call once from {@link Main}. */
    public static void init() {
        OrbGameRules.init();
        PlayerEffectDispatcher.register(EFFECT);
        ServerTickEvents.END_SERVER_TICK.register(FallTracker::tick);
        ServerPlayerEvents.LEAVE.register(player -> {
            FallTracker.clear(player.getUUID());
            AltitudeGuard.clear(player.getUUID());
            LiquidGuard.clear(player.getUUID());
            AltitudeFence.forget(player.getUUID());
        });
        DeathMessageOverrides.register(FallTracker::deathMessage);
    }

    /**
     * Whether {@code player}'s mayfly-driven fall immunity should NOT apply right now: holding the
     * orb only protects a fall while they're actually using the flight it grants, not merely while
     * held. Creative/spectator guarded like every other check in this package -- their own mayfly
     * isn't the orb's to withhold.
     */
    public static boolean isUnprotectedFall(ServerPlayer player) {
        if (!isManaged(player)) {
            return false;
        }
        return EFFECT.source().isActive(player) && !player.getAbilities().flying;
    }

    /**
     * Ticks a dropped orb's wetness: lying in water it soaks as fast as in a holder's hand, turning
     * waterlogged once soaked through, lying in the rain it keeps as wet as it is, and anywhere
     * else it dries. Called every tick for every item on the ground, from {@code DroppedOrbMixin}.
     */
    public static void tickDropped(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (!stack.is(ITEM) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (entity.isInWater()) {
            ItemStack soaked = Wetness.soak(stack, now);
            if (soaked != stack) {
                entity.setItem(soaked);
            }
            return;
        }
        if (!entity.isInWaterOrRain()) {
            Wetness.dry(stack, now);
        }
    }

    // Whether every sibling class's flight-management logic even applies to this player:
    // creative/spectator already fly for free, on their own terms, so none of this package's
    // guards, cutoffs, or speed changes are the orb's business while a player is in one.
    static boolean isManaged(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator();
    }

    // One component per line, since lore does not break on a newline. Italic is off explicitly:
    // ItemLore merges dark-purple-italic in as a fallback.
    private static ItemLore lore() {
        return new ItemLore(List.of(
                loreLine("It is said that you can ponder this to great heights..."),
                loreLine("It also doesn't like water.")));
    }

    // The waterlogged orb's lore, saying it needs drying.
    private static ItemLore waterloggedLore() {
        return new ItemLore(List.of(
                loreLine("It needs to be dried before it will work again.")));
    }

    // One gray, upright line of lore.
    private static Component loreLine(String text) {
        return Component.literal(text)
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
    }
}
