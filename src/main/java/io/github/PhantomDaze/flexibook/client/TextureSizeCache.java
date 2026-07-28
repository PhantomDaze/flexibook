package io.github.PhantomDaze.flexibook.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches native pixel size of standalone GUI textures (not atlas sprites).
 * Cleared when the client reloads resources.
 */
public final class TextureSizeCache {
    private static final Map<ResourceLocation, int[]> CACHE = new ConcurrentHashMap<>();

    private TextureSizeCache() {
    }

    public static void clear() {
        CACHE.clear();
    }

    /**
     * @return {@code [width, height]} or empty if missing / unreadable
     */
    public static Optional<int[]> getSize(ResourceLocation location) {
        if (location == null) {
            return Optional.empty();
        }
        int[] cached = CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return Optional.empty();
        }
        ResourceManager rm = mc.getResourceManager();
        Optional<Resource> res = rm.getResource(location);
        if (res.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = res.get().open(); NativeImage image = NativeImage.read(in)) {
            int[] size = new int[]{image.getWidth(), image.getHeight()};
            CACHE.put(location, size);
            return Optional.of(size);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}