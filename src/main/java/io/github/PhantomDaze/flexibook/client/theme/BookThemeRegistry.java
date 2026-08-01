package io.github.PhantomDaze.flexibook.client.theme;

import com.mojang.logging.LogUtils;
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of {@link BookTheme} instances, keyed by {@link Identifier}.
 * <p>
 * Code mods call {@link #register(Identifier, BookTheme)} during common/client setup.
 * Resource packs may add/override themes under {@code assets/<ns>/flexibook/themes/<path>.json}
 * (loaded client-side on resource reload).
 * <p>
 * Missing ids fall back to {@link BookThemes#DEFAULT}.
 */
public final class BookThemeRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Identifier, BookTheme> THEMES = new ConcurrentHashMap<>();

    private BookThemeRegistry() {
    }

    /** Registers or replaces a theme. Safe to call from any side (themes are pure data). */
    public static void register(Identifier id, BookTheme theme) {
        if (id == null || theme == null) {
            throw new IllegalArgumentException("theme id and theme must be non-null");
        }
        THEMES.put(id, theme);
        LOGGER.debug("Registered book theme {}", id);
    }

    public static void register(String id, BookTheme theme) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) {
            throw new IllegalArgumentException("Invalid theme id: " + id);
        }
        register(rl, theme);
    }

    public static Optional<BookTheme> getOptional(Identifier id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(THEMES.get(id));
    }

    /** Resolves {@code id}, or {@link BookThemes#DEFAULT} if null/unknown. */
    public static BookTheme resolve(Identifier id) {
        if (id == null) {
            return fallback();
        }
        BookTheme t = THEMES.get(id);
        if (t != null) {
            return t;
        }
        LOGGER.warn("Unknown book theme {}, falling back to {}", id, BookThemes.DEFAULT_ID);
        return fallback();
    }

    public static BookTheme resolve(Optional<Identifier> id) {
        return id.map(BookThemeRegistry::resolve).orElseGet(BookThemeRegistry::fallback);
    }

    public static BookTheme fallback() {
        BookTheme t = THEMES.get(BookThemes.DEFAULT_ID);
        return t != null ? t : BookThemes.DEFAULT;
    }

    public static boolean isRegistered(Identifier id) {
        return id != null && THEMES.containsKey(id);
    }

    public static Collection<Identifier> ids() {
        return Collections.unmodifiableSet(THEMES.keySet());
    }

    public static Map<Identifier, BookTheme> snapshot() {
        return Map.copyOf(THEMES);
    }

    /**
     * Removes a theme. Built-in {@link BookThemes#DEFAULT_ID} cannot be removed
     * (re-register to override values instead).
     */
    public static boolean unregister(Identifier id) {
        if (BookThemes.DEFAULT_ID.equals(id)) {
            return false;
        }
        return THEMES.remove(id) != null;
    }

    /**
     * Ensures built-in example themes are present with code defaults.
     * Always re-applies builtins so a prior resource override is cleared before JSON reload.
     */
    public static void bootstrap() {
        THEMES.put(BookThemes.DEFAULT_ID, BookThemes.DEFAULT);
        THEMES.put(BookThemes.CONTAIN_ID, BookThemes.CONTAIN);
        FlexiBookMod.LOGGER.debug("Book themes bootstrapped ({} entries)", THEMES.size());
    }
}
