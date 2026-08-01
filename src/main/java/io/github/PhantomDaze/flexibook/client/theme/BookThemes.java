package io.github.PhantomDaze.flexibook.client.theme;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.resources.Identifier;

/**
 * Built-in example themes shipped with FlexiBook.
 * Treat these as samples other mods can copy via {@link BookTheme.Builder#from(BookTheme)}.
 */
public final class BookThemes {
    private BookThemes() {
    }

    public static final Identifier DEFAULT_ID =
            FlexiBookIds.of(FlexiBookMod.MOD_ID, "default");

    /** Same layout/colors as {@link #DEFAULT}, but {@link ImageFit#CONTAIN} for images. */
    public static final Identifier CONTAIN_ID =
            FlexiBookIds.of(FlexiBookMod.MOD_ID, "contain");

    private static final Identifier BOOK_TEX =
            FlexiBookIds.of(FlexiBookMod.MOD_ID, "textures/gui/book.png");

    /**
     * Example parchment theme used when a book has no {@code theme} id (or an unknown id).
     * Values match the previous hard-coded {@code BookTheme.DEFAULT}.
     */
    public static final BookTheme DEFAULT = new BookTheme(
            BOOK_TEX,
            192,
            216,
            2048,
            16,
            10,
            5,
            4,
            18,
            160,
            185,
            9,
            3,
            5,
            10,
            10,
            6,
            0x3F3F3F,
            0x0000EE,
            0xFFD54F,
            0x8B7355,
            ImageFit.STRETCH,
            6
    );

    /** Example: default chrome with aspect-preserving images. */
    public static final BookTheme CONTAIN = DEFAULT.withImageFit(ImageFit.CONTAIN);
}
