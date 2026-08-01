package io.github.PhantomDaze.flexibook.util;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import net.minecraft.network.chat.FontDescription;
//?}

/**
 * Cross-version helpers for chat {@link Style} font binding.
 * 1.20–1.21.4: {@code Style#withFont(Identifier)}.
 * 1.21.11+: {@code Style#withFont(FontDescription)} via {@link FontDescription.Resource}.
 */
public final class McFonts {
    private McFonts() {}

    public static Style withFont(Style style, Identifier fontId) {
        //? if >=1.21.11 {
        return style.withFont(new FontDescription.Resource(fontId));
        //?} else {
        /*return style.withFont(fontId);
        *///?}
    }
}
