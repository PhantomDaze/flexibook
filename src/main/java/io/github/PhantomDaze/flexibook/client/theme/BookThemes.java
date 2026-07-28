package io.github.PhantomDaze.flexibook.client.theme;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import net.minecraft.resources.ResourceLocation;

/**
 * Built-in example themes shipped with FlexiBook.
 * Treat these as samples other mods can copy via {@link BookTheme.Builder#from(BookTheme)}.
 */
public final class BookThemes {
    private BookThemes() {
    }

    public static final ResourceLocation DEFAULT_ID =
            ResourceLocation.fromNamespaceAndPath(FlexiBookMod.MOD_ID, "default");

    /** Same layout/colors as {@link #DEFAULT}, but {@link ImageFit#CONTAIN} for images. */
    public static final ResourceLocation CONTAIN_ID =
            ResourceLocation.fromNamespaceAndPath(FlexiBookMod.MOD_ID, "contain");

    private static final ResourceLocation BOOK_TEX =
            ResourceLocation.fromNamespaceAndPath(FlexiBookMod.MOD_ID, "textures/gui/book.png");
    private static final ResourceLocation WIDGETS_TEX =
            ResourceLocation.fromNamespaceAndPath(FlexiBookMod.MOD_ID, "textures/gui/book_widgets.png");

    /**
     * Example parchment theme used when a book has no {@code theme} id (or an unknown id).
     * Values match the previous hard-coded {@code BookTheme.DEFAULT}.
     */
    public static final BookTheme DEFAULT = new BookTheme(
            BOOK_TEX,
            WIDGETS_TEX,
            192,
            216,
            256,
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
