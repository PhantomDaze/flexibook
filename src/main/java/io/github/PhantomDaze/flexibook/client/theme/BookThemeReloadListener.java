package io.github.PhantomDaze.flexibook.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.github.PhantomDaze.flexibook.FlexiBookMod;
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
 * Loads {@link BookTheme} JSON from {@code assets/<namespace>/flexibook/themes/<path>.json}.
 * Id becomes {@code namespace:path}. Resource themes override same-id code registrations
 * until the next reload rebuilds from code bootstrap + JSON.
 */
public final class BookThemeReloadListener implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "flexibook/themes/";
    private static final String SUFFIX = ".json";

    /** Theme ids last applied from resource packs (so we can drop stale ones). */
    private final Set<ResourceLocation> lastResourceIds = new HashSet<>();

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // Drop previous resource-only themes (not built-ins), then re-seed code samples.
        for (ResourceLocation id : lastResourceIds) {
            if (!BookThemes.DEFAULT_ID.equals(id) && !BookThemes.CONTAIN_ID.equals(id)) {
                BookThemeRegistry.unregister(id);
            }
        }
        lastResourceIds.clear();
        BookThemeRegistry.bootstrap();

        Map<ResourceLocation, Resource> found = resourceManager.listResources(
                "flexibook/themes",
                rl -> rl.getPath().endsWith(SUFFIX)
        );

        int loaded = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            // assets/ns/flexibook/themes/foo.json → ns:foo
            String path = fileId.getPath();
            if (!path.startsWith(PREFIX) || !path.endsWith(SUFFIX)) {
                continue;
            }
            String themePath = path.substring(PREFIX.length(), path.length() - SUFFIX.length());
            if (themePath.isEmpty() || themePath.contains("..")) {
                continue;
            }
            ResourceLocation themeId = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), themePath);
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                var parsed = BookTheme.CODEC.parse(JsonOps.INSTANCE, json);
                if (parsed.isError()) {
                    LOGGER.error("Failed to parse book theme {}: {}", themeId, parsed.error());
                    continue;
                }
                BookTheme theme = parsed.getOrThrow();
                BookThemeRegistry.register(themeId, theme);
                lastResourceIds.add(themeId);
                loaded++;
            } catch (Exception e) {
                LOGGER.error("Error loading book theme {}", themeId, e);
            }
        }

        BookLayoutEngine.clearCache();
        LOGGER.info("Loaded {} book theme(s) from resources (registry size {})",
                loaded, BookThemeRegistry.ids().size());
        FlexiBookMod.LOGGER.debug("Book themes: {}", BookThemeRegistry.ids());
    }
}
