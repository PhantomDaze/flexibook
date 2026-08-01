package io.github.PhantomDaze.flexibook.api;

import io.github.PhantomDaze.flexibook.client.theme.BookContentRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookThemes;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookDefinition;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.LinkActionRegistry;
import io.github.PhantomDaze.flexibook.registry.ModItems;
import io.github.PhantomDaze.flexibook.content.BookContentAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Public facade for other mods.
 */
public final class FlexiBookAPI {
    private FlexiBookAPI() {}

    public static AdaptiveBookBuilder builder(String guideId) {
        return new AdaptiveBookBuilder(guideId);
    }

    public static ItemStack createBook(AdaptiveBookContent content) {
        ItemStack stack = new ItemStack(ModItems.book());
        BookContentAccess.set(stack, content);
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

    // ── Themes ──────────────────────────────────────────────────────────────

    /** Built-in parchment sample id: {@code flexibook:default}. */
    public static ResourceLocation defaultThemeId() {
        return BookThemes.DEFAULT_ID;
    }

    /** Built-in keep-aspect image sample id: {@code flexibook:contain}. */
    public static ResourceLocation containThemeId() {
        return BookThemes.CONTAIN_ID;
    }

    /**
     * Registers or replaces a book theme. Safe from common setup (pure data).
     * Resource-pack JSON under {@code assets/<ns>/flexibook/themes/<path>.json}
     * can override the same id on client reload.
     */
    public static void registerTheme(ResourceLocation id, BookTheme theme) {
        BookThemeRegistry.register(id, theme);
    }

    public static void registerTheme(String id, BookTheme theme) {
        BookThemeRegistry.register(id, theme);
    }

    public static Optional<BookTheme> getTheme(ResourceLocation id) {
        return BookThemeRegistry.getOptional(id);
    }

    /** Resolves id or falls back to the built-in default sample. */
    public static BookTheme resolveTheme(ResourceLocation id) {
        return BookThemeRegistry.resolve(id);
    }

    public static BookTheme resolveTheme(Optional<ResourceLocation> id) {
        return BookThemeRegistry.resolve(id);
    }

    public static Collection<ResourceLocation> themeIds() {
        return BookThemeRegistry.ids();
    }

    // ── Book contents (bodies under flexibook/contents/) ───────────────────────

    /**
     * Registers or replaces a content <em>body</em>.
     * Resource-pack JSON: {@code assets/<ns>/flexibook/contents/<path>.json}.
     */
    public static void registerBookContent(ResourceLocation id, AdaptiveBookContent content) {
        BookContentRegistry.register(id, content);
    }

    public static void registerBookContent(String id, AdaptiveBookContent content) {
        BookContentRegistry.register(id, content);
    }

    public static Optional<AdaptiveBookContent> getBookContent(ResourceLocation id) {
        return BookContentRegistry.getOptional(id);
    }

    /** Resolves a content body id or returns {@link AdaptiveBookContent#EMPTY}. */
    public static AdaptiveBookContent resolveBookContent(ResourceLocation id) {
        return BookContentRegistry.resolve(id);
    }

    public static AdaptiveBookContent resolveBookContent(Optional<ResourceLocation> id) {
        return BookContentRegistry.resolve(id);
    }

    public static Collection<ResourceLocation> bookContentIds() {
        return BookContentRegistry.ids();
    }

    // ── Book definitions (indices under flexibook/books/) ───────────────────

    /**
     * Registers a book <em>index</em> (content id + theme id).
     * Resource-pack JSON: {@code assets/<ns>/flexibook/books/<path>.json}.
     */
    public static void registerBookDefinition(ResourceLocation id, BookDefinition def) {
        BookDefinitionRegistry.register(id, def);
    }

    public static void registerBookDefinition(String id, BookDefinition def) {
        BookDefinitionRegistry.register(id, def);
    }

    public static Optional<BookDefinition> getBookDefinition(ResourceLocation id) {
        return BookDefinitionRegistry.getOptional(id);
    }

    public static Collection<ResourceLocation> bookDefinitionIds() {
        return BookDefinitionRegistry.ids();
    }

    /**
     * Resolves a book id to full content for item use:
     * {@code flexibook/books/} index → content body + theme/font merge.
     */
    public static AdaptiveBookContent resolveBook(ResourceLocation bookId) {
        return BookDefinitionRegistry.resolveContent(bookId);
    }

    /**
     * Creates a FlexiBook ItemStack from a registered book index id
     * ({@code assets/<ns>/flexibook/books/<path>.json}).
     */
    public static ItemStack createBookFromDefinition(ResourceLocation bookId) {
        AdaptiveBookContent content = BookDefinitionRegistry.resolveContent(bookId);
        return createBook(content);
    }

    /**
     * Creates a FlexiBook ItemStack from a registered book, then applies an override.
     * {@link AdaptiveBookContent} is immutable — the function must return the tweaked instance
     * (e.g. {@code c -> c.withThemeId(myTheme)}). {@code null} override or return keeps the base.
     */
    public static ItemStack createBookFromDefinition(
            ResourceLocation bookId,
            Function<AdaptiveBookContent, AdaptiveBookContent> override
    ) {
        AdaptiveBookContent base = BookDefinitionRegistry.resolveContent(bookId);
        AdaptiveBookContent modified = base;
        if (override != null) {
            AdaptiveBookContent next = override.apply(base);
            if (next != null) {
                modified = next;
            }
        }
        return createBook(modified);
    }

    /**
     * Lectern compatibility hook — not implemented in v1.
     * Future versions may place FlexiBooks on lecterns and open the same screen.
     */
    public interface LecternCompat {
        // TODO v2: allow lecterns to host AdaptiveBookContent
    }
}
