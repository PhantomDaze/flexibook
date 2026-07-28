package io.github.PhantomDaze.flexibook;

import com.mojang.logging.LogUtils;
import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeRegistry;
import io.github.PhantomDaze.flexibook.registry.ModCreativeTabs;
import io.github.PhantomDaze.flexibook.registry.ModDataComponents;
import io.github.PhantomDaze.flexibook.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(FlexiBookMod.MOD_ID)
public class FlexiBookMod {
    public static final String MOD_ID = "flexibook";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FlexiBookMod(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        // Built-in sample themes available before client resource reload.
        BookThemeRegistry.bootstrap();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FlexiBookAPI.registerDefaultActions();
            BookThemeRegistry.bootstrap();
        });
        LOGGER.info("FlexiBook initialized");
    }
}
