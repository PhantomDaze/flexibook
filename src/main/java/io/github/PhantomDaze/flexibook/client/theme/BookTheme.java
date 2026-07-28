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
            1
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

    public int bookTexWidth() {
        return 192;
    }

    public int bookTexHeight() {
        return 192;
    }

    /** Left padding of the content area inside the book texture. */
    public int contentLeft() {
        return 16;
    }

    public int contentTop() {
        return 14;
    }

    public LayoutParams baseParams() {
        LayoutParams p = new LayoutParams();
        p.pageContentWidth = 160;
        p.pageContentHeight = 154;
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
