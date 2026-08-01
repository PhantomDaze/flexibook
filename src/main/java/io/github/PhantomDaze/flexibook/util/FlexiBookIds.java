package io.github.PhantomDaze.flexibook.util;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Identifier factory that works on both 1.20.1 ({@code new Identifier})
 * and 1.21.1 ({@code fromNamespaceAndPath}).
 */
public final class FlexiBookIds {
    private FlexiBookIds() {}

    public static Identifier of(String namespace, String path) {
        //? if >=1.21 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} else
        /*return new Identifier(namespace, path);*/
    }

    public static @Nullable Identifier tryParse(String id) {
        return Identifier.tryParse(id);
    }
}
