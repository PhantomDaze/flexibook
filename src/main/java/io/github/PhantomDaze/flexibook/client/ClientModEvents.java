package io.github.PhantomDaze.flexibook.client;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen;
import io.github.PhantomDaze.flexibook.layout.BookLayoutEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = FlexiBookMod.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BookLayoutEngine.clearCache();
    }

    /** Client-only open helper so item code does not classload Screen on dedicated server until invoked. */
    public static void openBook(ItemStack stack) {
        Minecraft.getInstance().setScreen(new AdaptiveBookScreen(stack));
    }
}
