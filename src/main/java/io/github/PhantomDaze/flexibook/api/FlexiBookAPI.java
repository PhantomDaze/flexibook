package io.github.PhantomDaze.flexibook.api;

import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.LinkActionRegistry;
import io.github.PhantomDaze.flexibook.registry.ModDataComponents;
import io.github.PhantomDaze.flexibook.registry.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Public facade for other mods.
 */
public final class FlexiBookAPI {
    private FlexiBookAPI() {}

    public static AdaptiveBookBuilder builder(String guideId) {
        return new AdaptiveBookBuilder(guideId);
    }

    public static ItemStack createBook(AdaptiveBookContent content) {
        ItemStack stack = new ItemStack(ModItems.FLEXI_BOOK.get());
        stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), content);
        return stack;
    }

    public static void registerCommandAction(String id, Consumer<LinkActionRegistry.ActionContext> action) {
        LinkActionRegistry.register(id, action);
    }

    public static LinkAction commandAction(String id) {
        return LinkAction.commandId(id);
    }

    public static LinkAction urlAction(String url) {
        return LinkAction.url(url);
    }

    /** Built-in demo actions used by the example book. */
    public static void registerDefaultActions() {
        LinkActionRegistry.registerDefaults();
    }

    /**
     * Lectern compatibility hook — not implemented in v1.
     * Future versions may place FlexiBooks on lecterns and open the same screen.
     */
    public interface LecternCompat {
        // TODO v2: allow lecterns to host AdaptiveBookContent
    }
}
