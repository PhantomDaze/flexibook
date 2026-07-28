package io.github.PhantomDaze.flexibook.content;

import io.github.PhantomDaze.flexibook.FlexiBookMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Common-side registry for safe command-id link actions.
 * Handlers may capture client-only behavior; registration itself is side-safe.
 */
public final class LinkActionRegistry {
    public interface ActionContext {
        /** Display a status message to the local player when available. */
        void message(String translationKey, Object... args);
    }

    private static final Map<String, Consumer<ActionContext>> ACTIONS = new ConcurrentHashMap<>();

    private LinkActionRegistry() {}

    public static void register(String id, Consumer<ActionContext> action) {
        if (id == null || id.isBlank() || action == null) {
            return;
        }
        ACTIONS.put(id, action);
    }

    public static Consumer<ActionContext> get(String id) {
        return ACTIONS.get(id);
    }

    public static boolean isRegistered(String id) {
        return ACTIONS.containsKey(id);
    }

    public static void registerDefaults() {
        register("flexibook:say_hi", ctx -> ctx.message("flexibook.action.say_hi"));
        FlexiBookMod.LOGGER.debug("Registered default FlexiBook link actions");
    }
}
