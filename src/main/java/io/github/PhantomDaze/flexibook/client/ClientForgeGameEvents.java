package io.github.PhantomDaze.flexibook.client;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.layout.BookLayoutEngine;

//? if forge {
/*import io.github.PhantomDaze.flexibook.client.smoke.BookClientSmokeTest;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlexiBookMod.MOD_ID, value = Dist.CLIENT)
public final class ClientForgeGameEvents {
    private ClientForgeGameEvents() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BookLayoutEngine.clearCache();
        TextureSizeCache.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        BookClientSmokeTest.onClientTick(Minecraft.getInstance());
    }
}
*///?} else {
/** Forge-only game-bus client events; empty on NeoForge (handled in {@link ClientModEvents}). */
public final class ClientForgeGameEvents {
    private ClientForgeGameEvents() {}
}
//?}
