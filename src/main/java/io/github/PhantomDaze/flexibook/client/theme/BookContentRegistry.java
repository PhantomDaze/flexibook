package io.github.PhantomDaze.flexibook.client.theme;

import com.mojang.logging.LogUtils;
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of book <em>content bodies</em> ({@link AdaptiveBookContent}),
 * keyed by {@link Identifier}.
 * <p>
 * Resource packs place bodies under {@code assets/<ns>/flexibook/contents/<path>.json}
 * (loaded on client reload via {@link BookContentReloadListener}).
 * Book <em>indices</em> live in {@link BookDefinitionRegistry} ({@code flexibook/books/}).
 * <p>
 * Missing ids return empty content (caller should handle gracefully).
 */
public final class BookContentRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Identifier, AdaptiveBookContent> CONTENTS = new ConcurrentHashMap<>();

    private BookContentRegistry() {}

    /** Registers or replaces a book content definition. */
    public static void register(Identifier id, AdaptiveBookContent content) {
        if (id == null || content == null) {
            throw new IllegalArgumentException("book id and content must be non-null");
        }
        CONTENTS.put(id, content);
        LOGGER.debug("Registered book content {}", id);
    }

    public static void register(String id, AdaptiveBookContent content) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) {
            throw new IllegalArgumentException("Invalid book id: " + id);
        }
        register(rl, content);
    }

    public static Optional<AdaptiveBookContent> getOptional(Identifier id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(CONTENTS.get(id));
    }

    /** Resolves id or returns {@link AdaptiveBookContent#EMPTY} if unknown. */
    public static AdaptiveBookContent resolve(Identifier id) {
        if (id == null) return AdaptiveBookContent.EMPTY;
        AdaptiveBookContent c = CONTENTS.get(id);
        if (c != null) return c;
        LOGGER.warn("Unknown book content {}, using empty", id);
        return AdaptiveBookContent.EMPTY;
    }

    public static AdaptiveBookContent resolve(Optional<Identifier> id) {
        return id.map(BookContentRegistry::resolve).orElse(AdaptiveBookContent.EMPTY);
    }

    public static boolean isRegistered(Identifier id) {
        return id != null && CONTENTS.containsKey(id);
    }

    public static Collection<Identifier> ids() {
        return Collections.unmodifiableSet(CONTENTS.keySet());
    }

    public static Map<Identifier, AdaptiveBookContent> snapshot() {
        return Map.copyOf(CONTENTS);
    }

    /** Removes a book definition (built-ins may be re-added by bootstrap). */
    public static boolean unregister(Identifier id) {
        return CONTENTS.remove(id) != null;
    }

    /**
     * Re-applies any code-registered built-in content bodies.
     * Resource-loaded contents override same ids after this call.
     */
    public static void bootstrap() {
        // No hard-coded bodies; data-driven contents come from flexibook/contents/.
        FlexiBookMod.LOGGER.debug("Book content registry bootstrapped ({} entries)", CONTENTS.size());
    }

    public static void clear() {
        CONTENTS.clear();
    }
}
