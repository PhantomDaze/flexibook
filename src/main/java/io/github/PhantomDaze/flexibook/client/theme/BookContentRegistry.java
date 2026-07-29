package io.github.PhantomDaze.flexibook.client.theme;

import com.mojang.logging.LogUtils;
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of {@link AdaptiveBookContent} definitions, keyed by {@link ResourceLocation}.
 * <p>
 * Code can call {@link #register(ResourceLocation, AdaptiveBookContent)} during setup.
 * Resource packs provide definitions under {@code assets/<ns>/flexibook/books/<path>.json}
 * (loaded client-side on resource reload via {@link BookContentReloadListener}).
 * <p>
 * Missing ids return empty content (caller should handle gracefully).
 */
public final class BookContentRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, AdaptiveBookContent> CONTENTS = new ConcurrentHashMap<>();

    private BookContentRegistry() {}

    /** Registers or replaces a book content definition. */
    public static void register(ResourceLocation id, AdaptiveBookContent content) {
        if (id == null || content == null) {
            throw new IllegalArgumentException("book id and content must be non-null");
        }
        CONTENTS.put(id, content);
        LOGGER.debug("Registered book content {}", id);
    }

    public static void register(String id, AdaptiveBookContent content) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            throw new IllegalArgumentException("Invalid book id: " + id);
        }
        register(rl, content);
    }

    public static Optional<AdaptiveBookContent> getOptional(ResourceLocation id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(CONTENTS.get(id));
    }

    /** Resolves id or returns {@link AdaptiveBookContent#EMPTY} if unknown. */
    public static AdaptiveBookContent resolve(ResourceLocation id) {
        if (id == null) return AdaptiveBookContent.EMPTY;
        AdaptiveBookContent c = CONTENTS.get(id);
        if (c != null) return c;
        LOGGER.warn("Unknown book content {}, using empty", id);
        return AdaptiveBookContent.EMPTY;
    }

    public static AdaptiveBookContent resolve(Optional<ResourceLocation> id) {
        return id.map(BookContentRegistry::resolve).orElse(AdaptiveBookContent.EMPTY);
    }

    public static boolean isRegistered(ResourceLocation id) {
        return id != null && CONTENTS.containsKey(id);
    }

    public static Collection<ResourceLocation> ids() {
        return Collections.unmodifiableSet(CONTENTS.keySet());
    }

    public static Map<ResourceLocation, AdaptiveBookContent> snapshot() {
        return Map.copyOf(CONTENTS);
    }

    /** Removes a book definition (built-ins may be re-added by bootstrap). */
    public static boolean unregister(ResourceLocation id) {
        return CONTENTS.remove(id) != null;
    }

    /**
     * Re-applies any code-registered built-in books.
     * Resource-loaded books override same ids after this call.
     */
    public static void bootstrap() {
        // Currently no hard-coded built-in books in the registry.
        // The original demo is created via ExampleBooks / AdaptiveBookBuilder in creative tab.
        // Data-driven books are expected to come from resource packs under flexibook/books/.
        // If you want a code-defined built-in, register it here and it will be cleared on reload
        // before resources are applied again.
        FlexiBookMod.LOGGER.debug("Book content registry bootstrapped ({} entries)", CONTENTS.size());
    }

    public static void clear() {
        CONTENTS.clear();
    }
}
