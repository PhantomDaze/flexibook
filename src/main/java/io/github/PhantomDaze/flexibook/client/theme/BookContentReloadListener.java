package io.github.PhantomDaze.flexibook.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookDefinition;
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
 * Loads the split pack layout:
 * <ul>
 *   <li>{@code assets/&lt;ns&gt;/flexibook/contents/&lt;path&gt;.json} → {@link BookContentRegistry}</li>
 *   <li>{@code assets/&lt;ns&gt;/flexibook/books/&lt;path&gt;.json} → {@link BookDefinitionRegistry} (index only)</li>
 * </ul>
 * Themes remain on {@link BookThemeReloadListener}. Lang / fonts / textures use vanilla paths.
 */
public final class BookContentReloadListener implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONTENTS_PREFIX = "flexibook/contents/";
    private static final String BOOKS_PREFIX = "flexibook/books/";
    private static final String SUFFIX = ".json";

    private final Set<ResourceLocation> lastContentIds = new HashSet<>();
    private final Set<ResourceLocation> lastBookIds = new HashSet<>();

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        for (ResourceLocation id : lastContentIds) {
            BookContentRegistry.unregister(id);
        }
        for (ResourceLocation id : lastBookIds) {
            BookDefinitionRegistry.unregister(id);
        }
        lastContentIds.clear();
        lastBookIds.clear();

        BookContentRegistry.bootstrap();
        BookDefinitionRegistry.bootstrap();

        int contentsLoaded = loadContents(resourceManager);
        int booksLoaded = loadBooks(resourceManager);

        BookLayoutEngine.clearCache();
        LOGGER.info(
                "Loaded {} content body(ies) + {} book definition(s) (contents={}, defs={})",
                contentsLoaded,
                booksLoaded,
                BookContentRegistry.ids().size(),
                BookDefinitionRegistry.ids().size()
        );
        FlexiBookMod.LOGGER.debug("Contents: {}", BookContentRegistry.ids());
        FlexiBookMod.LOGGER.debug("Books: {}", BookDefinitionRegistry.ids());
    }

    private int loadContents(ResourceManager resourceManager) {
        Map<ResourceLocation, Resource> found = resourceManager.listResources(
                "flexibook/contents",
                rl -> rl.getPath().endsWith(SUFFIX)
        );
        int loaded = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            if (!path.startsWith(CONTENTS_PREFIX) || !path.endsWith(SUFFIX)) {
                continue;
            }
            String bodyPath = path.substring(CONTENTS_PREFIX.length(), path.length() - SUFFIX.length());
            if (bodyPath.isEmpty() || bodyPath.contains("..")) {
                continue;
            }
            ResourceLocation contentId = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), bodyPath);
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                var parsed = AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, json);
                if (parsed.isError()) {
                    LOGGER.error("Failed to parse book content body {}: {}", contentId, parsed.error());
                    continue;
                }
                AdaptiveBookContent content = parsed.getOrThrow();
                BookContentRegistry.register(contentId, content);
                lastContentIds.add(contentId);
                loaded++;
            } catch (Exception e) {
                LOGGER.error("Error loading book content body {}", contentId, e);
            }
        }
        return loaded;
    }

    private int loadBooks(ResourceManager resourceManager) {
        Map<ResourceLocation, Resource> found = resourceManager.listResources(
                "flexibook/books",
                rl -> rl.getPath().endsWith(SUFFIX)
        );
        int loaded = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            if (!path.startsWith(BOOKS_PREFIX) || !path.endsWith(SUFFIX)) {
                continue;
            }
            String bookPath = path.substring(BOOKS_PREFIX.length(), path.length() - SUFFIX.length());
            if (bookPath.isEmpty() || bookPath.contains("..")) {
                continue;
            }
            ResourceLocation bookId = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), bookPath);
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                var parsed = BookDefinition.CODEC.parse(JsonOps.INSTANCE, json);
                if (parsed.isError()) {
                    LOGGER.error(
                            "Failed to parse book index {} (expected {{\"content\",\"theme\"?,\"font\"?}}): {}",
                            bookId,
                            parsed.error()
                    );
                    continue;
                }
                BookDefinition def = parsed.getOrThrow();
                BookDefinitionRegistry.register(bookId, def);
                lastBookIds.add(bookId);
                loaded++;
            } catch (Exception e) {
                LOGGER.error("Error loading book index {}", bookId, e);
            }
        }
        return loaded;
    }
}
