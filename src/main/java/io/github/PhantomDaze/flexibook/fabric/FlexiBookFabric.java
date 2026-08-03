package io.github.PhantomDaze.flexibook.fabric;

//? if fabric {
/*import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeRegistry;
import io.github.PhantomDaze.flexibook.command.FlexiBookCommands;
import io.github.PhantomDaze.flexibook.data.ClasspathPackBootstrap;
import io.github.PhantomDaze.flexibook.registry.ModCreativeTabs;
import io.github.PhantomDaze.flexibook.registry.ModDataComponents;
import io.github.PhantomDaze.flexibook.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

// Fabric common entrypoint — registers items/components/tabs and server commands.
public final class FlexiBookFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ModDataComponents.register();
        ModItems.register();
        ModCreativeTabs.register();
        BookThemeRegistry.bootstrap();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                FlexiBookCommands.register(dispatcher));

        FlexiBookAPI.registerDefaultActions();
        ClasspathPackBootstrap.loadBundledSamples();
        FlexiBookMod.LOGGER.info("FlexiBook (Fabric) initialized");
    }
}
*///?} else {
// Fabric-only entrypoint stub for non-Fabric builds.
public final class FlexiBookFabric {
    private FlexiBookFabric() {}
}
//?}
