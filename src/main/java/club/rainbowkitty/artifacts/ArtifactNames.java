package club.rainbowkitty.artifacts;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Gives an artifact its gold, italic name as an {@code ITEM_NAME} component, so clients
 * without the server's resource pack read it too.
 *
 * <p>The name is set by a component initializer registered after the item's own, not through
 * {@code Item.Properties.component}: {@code Item.Properties.finalizeInitializer} sets
 * {@code ITEM_NAME} to the translatable name after every component the properties hold, and an
 * item's initializers run in the order they were added ({@code DataComponentInitializers}), so
 * only a later one sticks.
 */
public final class ArtifactNames {
    private ArtifactNames() {
    }

    /**
     * Names {@code item}, registered under {@code key}, and returns it; call with the item just
     * constructed, so this initializer runs after the one its constructor added.
     */
    public static <T extends Item> T named(ResourceKey<Item> key, String name, T item) {
        Component styled = Component.literal(name)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.add(key,
                (components, context, id) -> components.set(DataComponents.ITEM_NAME, styled));
        return item;
    }
}
