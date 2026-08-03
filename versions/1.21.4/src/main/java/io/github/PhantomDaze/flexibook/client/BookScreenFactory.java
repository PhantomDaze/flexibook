package io.github.PhantomDaze.flexibook.client;

import io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** 1.21.4 NeoForge client boundary. Keep version API calls here. */
public final class BookScreenFactory {
    private BookScreenFactory() {}

    public static void open(Minecraft minecraft, ItemStack stack) {
        minecraft.setScreen(new AdaptiveBookScreen(stack));
    }
}
