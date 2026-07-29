package io.github.PhantomDaze.flexibook.content;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import net.minecraft.resources.ResourceLocation;

public final class FlexiBookFonts {
    public static final ResourceLocation DEFAULT =
            ResourceLocation.fromNamespaceAndPath(FlexiBookMod.MOD_ID, "default");

    private FlexiBookFonts() {}

    /** Explicit book font if present, otherwise flexibook:default. */
    public static ResourceLocation resolve(java.util.Optional<ResourceLocation> explicit) {
        return explicit != null && explicit.isPresent() ? explicit.get() : DEFAULT;
    }
}
