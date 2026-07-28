package io.github.PhantomDaze.flexibook.client.theme;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.layout.LayoutParams;
import net.minecraft.resources.ResourceLocation;

/**
 * Visual theme for the book screen. Resource packs can replace the textures;
 * spacing defaults live here so layout stays consistent.
 */
public final class BookTheme {
    public static final BookTheme DEFAULT = new BookTheme(
            ResourceLocation.fromNamespaceAndPath(FlexiBookMod.MOD_ID, "textures/gui/book.png"),
            ResourceLocation.fromNamespaceAndPath(FlexiBookMod.MOD_ID, "textures/gui/book_widgets.png"),
            5
    );

    private final ResourceLocation bookTexture;
    private final ResourceLocation widgetsTexture;
    private final int revision;

    public BookTheme(ResourceLocation bookTexture, ResourceLocation widgetsTexture, int revision) {
        this.bookTexture = bookTexture;
        this.widgetsTexture = widgetsTexture;
        this.revision = revision;
    }

    public ResourceLocation bookTexture() {
        return bookTexture;
    }

    public ResourceLocation widgetsTexture() {
        return widgetsTexture;
    }

    public int revision() {
        return revision;
    }

    /** On-screen draw width of the book panel (u-span in the texture sheet). */
    public int bookTexWidth() {
        return 192;
    }

    /**
     * On-screen draw height of the book panel. Taller than the original 192 so a footer
     * band can hold the page label without eating into {@link #baseParams()} content height.
     */
    public int bookTexHeight() {
        return 216;
    }

    /**
     * Full GUI texture sheet size (power-of-two). Content sits in the top-left;
     * blit must pass these as textureWidth/textureHeight or the panel scales wrong.
     */
    public int textureSheetSize() {
        return 256;
    }

    /** Left padding of the content area inside the book texture. */
    public int contentLeft() {
        return 16;
    }

    public int contentTop() {
        return 10;
    }

    public LayoutParams baseParams() {
        LayoutParams p = new LayoutParams();
        // Paper inner ~168×…; keep a few px clear of the border and page-label footer.
        p.pageContentWidth = 160;
        p.pageContentHeight = 185;
        p.lineHeight = 9;
        p.paragraphGap = 3;
        p.headingGap = 5;
        p.gutter = 10;
        p.bulletIndent = 10;
        p.dividerHeight = 6;
        return p;
    }

    public int pageTextColor() {
        return 0x3F3F3F;
    }

    public int linkColor() {
        return 0x0000EE;
    }

    public int highlightColor() {
        return 0xFFD54F;
    }

    public int dividerColor() {
        return 0x8B7355;
    }
}
