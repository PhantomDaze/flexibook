package io.github.PhantomDaze.flexibook.client;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen;
import io.github.PhantomDaze.flexibook.client.theme.BookContentReloadListener;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeReloadListener;
import io.github.PhantomDaze.flexibook.layout.BookLayoutEngine;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
//? if >=1.21.4 {
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
//?} else {
/*import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
*///?}
//?} else {
/*//? if forge {
/^import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
^///?}
*///?}

//? if neoforge {
@EventBusSubscriber(modid = FlexiBookMod.MOD_ID, value = Dist.CLIENT)
//?} else {
/*//? if forge {
/^@Mod.EventBusSubscriber(modid = FlexiBookMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
^///?}
*///?}
public final class ClientModEvents {
    private ClientModEvents() {}

    //? if neoforge {
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BookLayoutEngine.clearCache();
        TextureSizeCache.clear();
    }

    //? if >=1.21.4 {
    @SubscribeEvent
    public static void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(
                FlexiBookIds.of(FlexiBookMod.MOD_ID, "texture_size_cache"),
                (ResourceManagerReloadListener) (ResourceManager manager) -> TextureSizeCache.clear());
        event.addListener(FlexiBookIds.of(FlexiBookMod.MOD_ID, "book_themes"), new BookThemeReloadListener());
        event.addListener(FlexiBookIds.of(FlexiBookMod.MOD_ID, "book_contents"), new BookContentReloadListener());
    }
    //?} else {
    /*@SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) (ResourceManager manager) -> TextureSizeCache.clear());
        event.registerReloadListener(new BookThemeReloadListener());
        event.registerReloadListener(new BookContentReloadListener());
    }
    *///?}
    //?} else {
    /*//? if forge {
    /^@SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) (ResourceManager manager) -> TextureSizeCache.clear());
        event.registerReloadListener(new BookThemeReloadListener());
        event.registerReloadListener(new BookContentReloadListener());
    }
    ^///?}
    *///?}

    /** Client-only open helper so item code does not classload Screen on dedicated server until invoked. */
    public static void openBook(ItemStack stack) {
        //? if >=26.2 {
        /*Minecraft.getInstance().setScreenAndShow(new AdaptiveBookScreen(stack));
        *///?} else {
        Minecraft.getInstance().setScreen(new AdaptiveBookScreen(stack));
        //?}
    }
}
