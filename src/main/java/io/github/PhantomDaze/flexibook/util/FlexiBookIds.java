package io.github.PhantomDaze.flexibook.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * ResourceLocation factory that works on both 1.20.1 ({@code new ResourceLocation})
 * and 1.21.1 ({@code fromNamespaceAndPath}).
 */
public final class FlexiBookIds {
    private FlexiBookIds() {}

    public static ResourceLocation of(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else
        /*return new ResourceLocation(namespace, path);*/
    }

    public static @Nullable ResourceLocation tryParse(String id) {
        return ResourceLocation.tryParse(id);
    }
}
