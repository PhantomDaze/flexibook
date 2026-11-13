package io.github.PhantomDaze.flexibook.client;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.layout.BookLayoutEngine;

//? if forge {
/*import net.minecraftforge.api.distmarker.Dist;
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

    // Forge 的 AutoSmoke 需要一个游戏总线的 tick 钩子（NeoForge 在 ClientModEvents 里）。
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AutoSmokeClient.onClientTick();
        }
    }
}
*///?} else {
/** Forge-only game-bus client events; empty on NeoForge (handled in {@link ClientModEvents}). */
public final class ClientForgeGameEvents {
    private ClientForgeGameEvents() {}
}
//?}
