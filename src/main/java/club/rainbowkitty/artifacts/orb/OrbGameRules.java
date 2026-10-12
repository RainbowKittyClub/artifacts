package club.rainbowkitty.artifacts.orb;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;

import club.rainbowkitty.artifacts.Main;

// The orb's game rules, registered straight into vanilla's registry (BuiltInRegistries.GAME_RULE)
// at class init. One rule under the mod's own namespace, so kalic0re's per-component
// ComponentGameRules would only add a level. Read live, never cached: a /gamerule change applies
// on the next tick.
final class OrbGameRules {
    // Blocks of air allowed between a flying holder's feet and the ground, the orb's flight
    // ceiling: /gamerule artifacts:levitation_orb_flight_ceiling. Bounded by a world's full
    // height, past which the ground walk would only search empty air.
    static final GameRule<Integer> FLIGHT_CEILING =
            registerInteger("levitation_orb_flight_ceiling", 16, 0, 384);

    private OrbGameRules() {
    }

    // Forces class init, and so the registration, before the game rule registry freezes; call
    // once from OrbOfLevitation.init.
    static void init() {
    }

    // The flight ceiling in this level's game rules.
    static int flightCeiling(ServerLevel level) {
        return level.getGameRules().get(FLIGHT_CEILING);
    }

    // Registers an integer rule under the mod's namespace, listed with the player rules.
    private static GameRule<Integer> registerInteger(String path, int defaultValue, int min,
            int max) {
        GameRule<Integer> rule = new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.INT,
                IntegerArgumentType.integer(min, max),
                (visitor, registered) -> visitor.visitInteger(registered), Codec.INT,
                value -> value, defaultValue, FeatureFlags.VANILLA_SET);
        return Registry.register(BuiltInRegistries.GAME_RULE,
                Identifier.fromNamespaceAndPath(Main.MOD_ID, path), rule);
    }
}
