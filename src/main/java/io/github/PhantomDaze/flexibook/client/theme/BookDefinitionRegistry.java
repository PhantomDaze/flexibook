package io.github.PhantomDaze.flexibook.client.theme;

import com.mojang.logging.LogUtils;
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookDefinition;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of {@link BookDefinition} indices ({@code flexibook/books/*.json}).
 * Body text lives in {@link BookContentRegistry} ({@code flexibook/contents/*.json}).
 */
public final class BookDefinitionRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, BookDefinition> DEFS = new ConcurrentHashMap<>();

    private BookDefinitionRegistry() {}

    public static void register(ResourceLocation id, BookDefinition def) {
        if (id == null || def == null) {
            throw new IllegalArgumentException("book id and definition must be non-null");
        }
        DEFS.put(id, def);
        LOGGER.debug("Registered book definition {}", id);
    }

    public static void register(String id, BookDefinition def) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            throw new IllegalArgumentException("Invalid book id: " + id);
        }
        register(rl, def);
    }

    public static Optional<BookDefinition> getOptional(ResourceLocation id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(DEFS.get(id));
    }

    public static boolean isRegistered(ResourceLocation id) {
        return id != null && DEFS.containsKey(id);
    }

    public static Collection<ResourceLocation> ids() {
        return Collections.unmodifiableSet(DEFS.keySet());
    }

    public static Map<ResourceLocation, BookDefinition> snapshot() {
        return Map.copyOf(DEFS);
    }

    public static boolean unregister(ResourceLocation id) {
        return DEFS.remove(id) != null;
    }

    public static void clear() {
        DEFS.clear();
    }

    public static void bootstrap() {
        FlexiBookMod.LOGGER.debug("Book definition registry bootstrapped ({} entries)", DEFS.size());
    }

    /**
     * Resolves a book id to full {@link AdaptiveBookContent}:
     * load definition → load content body by {@link BookDefinition#contentId()} → apply theme/font.
     * Unknown book id → {@link AdaptiveBookContent#EMPTY}.
     */
    public static AdaptiveBookContent resolveContent(ResourceLocation bookId) {
        if (bookId == null) {
            return AdaptiveBookContent.EMPTY;
        }
        BookDefinition def = DEFS.get(bookId);
        if (def == null) {
            LOGGER.warn("Unknown book {}, using empty", bookId);
            return AdaptiveBookContent.EMPTY;
        }
        AdaptiveBookContent body = BookContentRegistry.resolve(def.contentId());
        return def.applyTo(body);
    }

    public static AdaptiveBookContent resolveContent(Optional<ResourceLocation> bookId) {
        return bookId.map(BookDefinitionRegistry::resolveContent).orElse(AdaptiveBookContent.EMPTY);
    }
}
