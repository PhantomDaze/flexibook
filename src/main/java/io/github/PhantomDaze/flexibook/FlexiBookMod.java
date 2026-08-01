package io.github.PhantomDaze.flexibook;

import com.mojang.logging.LogUtils;
import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeRegistry;
import io.github.PhantomDaze.flexibook.registry.ModCreativeTabs;
import io.github.PhantomDaze.flexibook.registry.ModItems;
import org.slf4j.Logger;

//? if neoforge {
import io.github.PhantomDaze.flexibook.registry.ModDataComponents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//?} else {
/*//? if forge {
/^import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
^///?}
*///?}

//? if neoforge {
@Mod(FlexiBookMod.MOD_ID)
//?} else {
/*//? if forge {
/^@Mod(FlexiBookMod.MOD_ID)
^///?}
*///?}
public class FlexiBookMod {
    public static final String MOD_ID = "flexibook";
    public static final Logger LOGGER = LogUtils.getLogger();

    //? if neoforge {
    public FlexiBookMod(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        BookThemeRegistry.bootstrap();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FlexiBookAPI.registerDefaultActions();
            BookThemeRegistry.bootstrap();
            io.github.PhantomDaze.flexibook.data.ClasspathPackBootstrap.loadFieldNotesSample();
        });
        LOGGER.info("FlexiBook initialized");
    }
    //?} else {
    /*//? if forge {
    /^public FlexiBookMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        BookThemeRegistry.bootstrap();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FlexiBookAPI.registerDefaultActions();
            BookThemeRegistry.bootstrap();
            io.github.PhantomDaze.flexibook.data.ClasspathPackBootstrap.loadFieldNotesSample();
        });
        LOGGER.info("FlexiBook initialized");
    }
    ^///?} else {
    // Fabric: no @Mod class — see fabric/FlexiBookFabric.
    private FlexiBookMod() {}
    //?}
    *///?}
}
