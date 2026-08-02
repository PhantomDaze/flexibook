package io.github.PhantomDaze.flexibook.client.link;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.LinkActionRegistry;
//? if >=1.21.11 {
/*import net.minecraft.util.Util;
*///?} else {
import net.minecraft.Util;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Client-side link dispatch. Arbitrary commands are never run —
 * only registered action ids, and http(s) URLs after confirmation.
 */
public final class LinkHandler {
    private LinkHandler() {}

    public static void handle(LinkAction action, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        // instanceof chains (not pattern-switch) — final on Java 17; sealed switch is preview.
        if (action instanceof LinkAction.None) {
            return;
        }
        if (action instanceof LinkAction.CommandId cmd) {
            Consumer<LinkActionRegistry.ActionContext> handler = LinkActionRegistry.get(cmd.id());
            if (handler == null) {
                if (mc.player != null) {
                    //? if >=26.1.2 {
                    /*mc.player.sendOverlayMessage(Component.translatable("flexibook.link.unknown_action", cmd.id()));
                    *///?} else {
                    mc.player.displayClientMessage(Component.translatable("flexibook.link.unknown_action", cmd.id()), true);
                    //?}
                }
                FlexiBookMod.LOGGER.warn("Blocked unregistered FlexiBook command action: {}", cmd.id());
                return;
            }
            handler.accept((key, args) -> {
                if (mc.player != null) {
                    //? if >=26.1.2 {
                    /*mc.player.sendSystemMessage(Component.translatable(key, args));
                    *///?} else {
                    mc.player.displayClientMessage(Component.translatable(key, args), false);
                    //?}
                }
            });
            return;
        }
        if (action instanceof LinkAction.Url url) {
            openUrl(mc, parent, url.url());
        }
    }

    private static void openUrl(Minecraft mc, Screen parent, String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            if (mc.player != null) {
                //? if >=26.1.2 {
                /*mc.player.sendOverlayMessage(Component.translatable("flexibook.link.bad_url"));
                *///?} else {
                mc.player.displayClientMessage(Component.translatable("flexibook.link.bad_url"), true);
                //?}
            }
            return;
        }
        //? if >=26.2 {
        /*mc.setScreenAndShow(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                Util.getPlatform().openUri(url);
            }
            mc.setScreenAndShow(parent);
        }, url, true));
        *///?} else {
        mc.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                Util.getPlatform().openUri(url);
            }
            mc.setScreen(parent);
        }, url, true));
        //?}
    }
}
