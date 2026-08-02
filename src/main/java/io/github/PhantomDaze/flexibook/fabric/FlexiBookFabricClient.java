package io.github.PhantomDaze.flexibook.fabric;

//? if fabric {
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.client.TextureSizeCache;
import io.github.PhantomDaze.flexibook.client.smoke.BookClientSmokeTest;
import io.github.PhantomDaze.flexibook.client.theme.BookContentReloadListener;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeReloadListener;
import io.github.PhantomDaze.flexibook.layout.BookLayoutEngine;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

// Fabric client entrypoint — reload listeners + disconnect cache clear.
public final class FlexiBookFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        register("texture_size_cache", manager -> TextureSizeCache.clear());
        register("book_themes", new BookThemeReloadListener());
        register("book_contents", new BookContentReloadListener());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BookLayoutEngine.clearCache();
            TextureSizeCache.clear();
        });

        BookClientSmokeTest.bootstrap();
        ClientTickEvents.END_CLIENT_TICK.register(BookClientSmokeTest::onClientTick);
    }

    private static void register(String path, net.minecraft.server.packs.resources.ResourceManagerReloadListener listener) {
        ResourceLocation id = FlexiBookIds.of(FlexiBookMod.MOD_ID, path);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return id;
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        listener.onResourceManagerReload(manager);
                    }
                });
    }
}
//?} else {
/*// Fabric-only client entrypoint stub for non-Fabric builds.
public final class FlexiBookFabricClient {
    private FlexiBookFabricClient() {}
}
*///?}
