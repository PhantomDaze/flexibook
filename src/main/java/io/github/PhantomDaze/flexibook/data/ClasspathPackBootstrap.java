package io.github.PhantomDaze.flexibook.data;

import io.github.PhantomDaze.flexibook.util.Compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.client.theme.BookContentRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeRegistry;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookDefinition;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads pack-shaped JSON bundled inside the mod jar (classpath) into the runtime registries.
 * Used so {@code /flexibook give} works on the integrated/dedicated server where
 * client resource-pack reload listeners do not run.
 * <p>
 * Client resource reload still overrides the same ids from active resource packs.
 */
public final class ClasspathPackBootstrap {
    private ClasspathPackBootstrap() {}

    public static void loadFieldNotesSample() {
        loadTheme("fieldnotes", "parchment");
        loadContent("fieldnotes", "journal");
        loadBook("fieldnotes", "journal");
    }

    public static void loadTheme(String ns, String path) {
        String cp = "/assets/" + ns + "/flexibook/themes/" + path + ".json";
        JsonElement el = read(cp);
        if (el == null) return;
        var parsed = BookTheme.CODEC.parse(JsonOps.INSTANCE, el);
        if (Compat.isError(parsed)) {
            FlexiBookMod.LOGGER.error("Classpath theme {}: {}", cp, parsed.error());
            return;
        }
        ResourceLocation id = FlexiBookIds.of(ns, path);
        BookThemeRegistry.register(id, Compat.getOrThrow(parsed));
        FlexiBookMod.LOGGER.info("Classpath theme registered {}", id);
    }

    public static void loadContent(String ns, String path) {
        String cp = "/assets/" + ns + "/flexibook/contents/" + path + ".json";
        JsonElement el = read(cp);
        if (el == null) return;
        var parsed = AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, el);
        if (Compat.isError(parsed)) {
            FlexiBookMod.LOGGER.error("Classpath content {}: {}", cp, parsed.error());
            return;
        }
        ResourceLocation id = FlexiBookIds.of(ns, path);
        BookContentRegistry.register(id, Compat.getOrThrow(parsed));
        FlexiBookMod.LOGGER.info("Classpath content registered {}", id);
    }

    public static void loadBook(String ns, String path) {
        String cp = "/assets/" + ns + "/flexibook/books/" + path + ".json";
        JsonElement el = read(cp);
        if (el == null) return;
        var parsed = BookDefinition.CODEC.parse(JsonOps.INSTANCE, el);
        if (Compat.isError(parsed)) {
            FlexiBookMod.LOGGER.error("Classpath book index {}: {}", cp, parsed.error());
            return;
        }
        ResourceLocation id = FlexiBookIds.of(ns, path);
        BookDefinitionRegistry.register(id, Compat.getOrThrow(parsed));
        FlexiBookMod.LOGGER.info("Classpath book registered {}", id);
    }

    private static JsonElement read(String classpath) {
        try (InputStream in = ClasspathPackBootstrap.class.getResourceAsStream(classpath)) {
            if (in == null) {
                FlexiBookMod.LOGGER.warn("Missing classpath resource {}", classpath);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            FlexiBookMod.LOGGER.error("Failed reading {}", classpath, e);
            return null;
        }
    }
}
