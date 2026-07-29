package io.github.PhantomDaze.flexibook.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.layout.BookLayoutEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@link AdaptiveBookContent} definitions from {@code assets/<namespace>/flexibook/books/<path>.json}.
 * Id becomes {@code namespace:path}.
 * <p>
 * Resource definitions override same-id code registrations until the next reload.
 */
public final class BookContentReloadListener implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "flexibook/books/";
    private static final String SUFFIX = ".json";

    /** Book ids last applied from resource packs (for cleanup on reload). */
    private final Set<ResourceLocation> lastResourceIds = new HashSet<>();

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // Drop previous resource-only books
        for (ResourceLocation id : lastResourceIds) {
            BookContentRegistry.unregister(id);
        }
        lastResourceIds.clear();

        // Re-apply any code-defined books first
        BookContentRegistry.bootstrap();

        Map<ResourceLocation, Resource> found = resourceManager.listResources(
                "flexibook/books",
                rl -> rl.getPath().endsWith(SUFFIX)
        );

        int loaded = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            if (!path.startsWith(PREFIX) || !path.endsWith(SUFFIX)) {
                continue;
            }
            String bookPath = path.substring(PREFIX.length(), path.length() - SUFFIX.length());
            if (bookPath.isEmpty() || bookPath.contains("..")) {
                continue;
            }
            ResourceLocation bookId = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), bookPath);
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                var parsed = AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, json);
                if (parsed.isError()) {
                    LOGGER.error("Failed to parse book content {}: {}", bookId, parsed.error());
                    continue;
                }
                AdaptiveBookContent content = parsed.getOrThrow();
                BookContentRegistry.register(bookId, content);
                lastResourceIds.add(bookId);
                loaded++;
            } catch (Exception e) {
                LOGGER.error("Error loading book content {}", bookId, e);
            }
        }

        BookLayoutEngine.clearCache();
        LOGGER.info("Loaded {} book content(s) from resources (registry size {})",
                loaded, BookContentRegistry.ids().size());
        FlexiBookMod.LOGGER.debug("Book contents: {}", BookContentRegistry.ids());
    }
}
