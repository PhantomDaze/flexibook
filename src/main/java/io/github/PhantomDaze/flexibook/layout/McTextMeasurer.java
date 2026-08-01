package io.github.PhantomDaze.flexibook.layout;

import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.util.McFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * MC-backed TextMeasurer that delegates to vanilla Font via Style.
 */
public final class McTextMeasurer implements TextMeasurer {
    private final Font font;

    public McTextMeasurer(Font font) {
        this.font = font;
    }

    @Override
    public int width(String text, StyleFlags style, Optional<ResourceLocation> fontId) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Style mcStyle = toStyle(style, fontId);
        return font.width(Component.literal(text).withStyle(mcStyle));
    }

    private static Style toStyle(StyleFlags flags, Optional<ResourceLocation> overrideFont) {
        Style s = Style.EMPTY;
        if (flags.bold()) s = s.withBold(true);
        if (flags.italic()) s = s.withItalic(true);
        if (flags.underline()) s = s.withUnderlined(true);
        ResourceLocation f = overrideFont.orElseGet(() -> flags.font().orElse(null));
        if (f != null) {
            s = McFonts.withFont(s, f);
        }
        return s;
    }
}
