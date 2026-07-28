package io.github.PhantomDaze.flexibook.client.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.PhantomDaze.flexibook.layout.LayoutParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * Immutable visual theme for {@link io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen}.
 * Register instances via {@link BookThemeRegistry}; reference by id on
 * {@link io.github.PhantomDaze.flexibook.content.AdaptiveBookContent#themeId()}.
 * <p>
 * The built-in example is {@link BookThemes#DEFAULT_ID} ({@code flexibook:default}).
 */
public record BookTheme(
        ResourceLocation bookTexture,
        ResourceLocation widgetsTexture,
        int bookTexWidth,
        int bookTexHeight,
        int textureSheetSize,
        int contentLeft,
        int contentTop,
        int titleOffsetY,
        int contentOffsetY,
        int pageLabelInsetY,
        int pageContentWidth,
        int pageContentHeight,
        int lineHeight,
        int paragraphGap,
        int headingGap,
        int gutter,
        int bulletIndent,
        int dividerHeight,
        int pageTextColor,
        int linkColor,
        int highlightColor,
        int dividerColor,
        ImageFit imageFit,
        int revision
) {
    public static final Codec<ImageFit> IMAGE_FIT_CODEC = StringRepresentable.fromEnum(ImageFit::values);

    /**
     * Flat JSON object codec. Metrics/colors are intermediate MapCodecs so DFU can stay
     * under the 16-field {@code group} limit while keys remain top-level in the JSON.
     */
    public static final Codec<BookTheme> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("book_texture").forGetter(BookTheme::bookTexture),
            ResourceLocation.CODEC.fieldOf("widgets_texture").forGetter(BookTheme::widgetsTexture),
            Metrics.MAP_CODEC.forGetter(Metrics::from),
            Colors.MAP_CODEC.forGetter(Colors::from),
            IMAGE_FIT_CODEC.optionalFieldOf("image_fit", ImageFit.STRETCH).forGetter(BookTheme::imageFit),
            Codec.INT.optionalFieldOf("revision", 1).forGetter(BookTheme::revision)
    ).apply(i, (bookTexture, widgetsTexture, metrics, colors, imageFit, revision) -> new BookTheme(
            bookTexture,
            widgetsTexture,
            metrics.bookTexWidth,
            metrics.bookTexHeight,
            metrics.textureSheetSize,
            metrics.contentLeft,
            metrics.contentTop,
            metrics.titleOffsetY,
            metrics.contentOffsetY,
            metrics.pageLabelInsetY,
            metrics.pageContentWidth,
            metrics.pageContentHeight,
            metrics.lineHeight,
            metrics.paragraphGap,
            metrics.headingGap,
            metrics.gutter,
            metrics.bulletIndent,
            metrics.dividerHeight,
            colors.pageTextColor,
            colors.linkColor,
            colors.highlightColor,
            colors.dividerColor,
            imageFit,
            revision
    )));

    /** Layout / chrome integers (flattened into parent JSON). */
    private record Metrics(
            int bookTexWidth,
            int bookTexHeight,
            int textureSheetSize,
            int contentLeft,
            int contentTop,
            int titleOffsetY,
            int contentOffsetY,
            int pageLabelInsetY,
            int pageContentWidth,
            int pageContentHeight,
            int lineHeight,
            int paragraphGap,
            int headingGap,
            int gutter,
            int bulletIndent,
            int dividerHeight
    ) {
        static Metrics from(BookTheme t) {
            return new Metrics(
                    t.bookTexWidth, t.bookTexHeight, t.textureSheetSize,
                    t.contentLeft, t.contentTop, t.titleOffsetY, t.contentOffsetY, t.pageLabelInsetY,
                    t.pageContentWidth, t.pageContentHeight, t.lineHeight, t.paragraphGap, t.headingGap,
                    t.gutter, t.bulletIndent, t.dividerHeight
            );
        }

        static final com.mojang.serialization.MapCodec<Metrics> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("book_tex_width", 192).forGetter(Metrics::bookTexWidth),
                Codec.INT.optionalFieldOf("book_tex_height", 216).forGetter(Metrics::bookTexHeight),
                Codec.INT.optionalFieldOf("texture_sheet_size", 256).forGetter(Metrics::textureSheetSize),
                Codec.INT.optionalFieldOf("content_left", 16).forGetter(Metrics::contentLeft),
                Codec.INT.optionalFieldOf("content_top", 10).forGetter(Metrics::contentTop),
                Codec.INT.optionalFieldOf("title_offset_y", 5).forGetter(Metrics::titleOffsetY),
                Codec.INT.optionalFieldOf("content_offset_y", 4).forGetter(Metrics::contentOffsetY),
                Codec.INT.optionalFieldOf("page_label_inset_y", 18).forGetter(Metrics::pageLabelInsetY),
                Codec.INT.optionalFieldOf("page_content_width", 160).forGetter(Metrics::pageContentWidth),
                Codec.INT.optionalFieldOf("page_content_height", 185).forGetter(Metrics::pageContentHeight),
                Codec.INT.optionalFieldOf("line_height", 9).forGetter(Metrics::lineHeight),
                Codec.INT.optionalFieldOf("paragraph_gap", 3).forGetter(Metrics::paragraphGap),
                Codec.INT.optionalFieldOf("heading_gap", 5).forGetter(Metrics::headingGap),
                Codec.INT.optionalFieldOf("gutter", 10).forGetter(Metrics::gutter),
                Codec.INT.optionalFieldOf("bullet_indent", 10).forGetter(Metrics::bulletIndent),
                Codec.INT.optionalFieldOf("divider_height", 6).forGetter(Metrics::dividerHeight)
        ).apply(i, Metrics::new));
    }

    private record Colors(int pageTextColor, int linkColor, int highlightColor, int dividerColor) {
        static Colors from(BookTheme t) {
            return new Colors(t.pageTextColor, t.linkColor, t.highlightColor, t.dividerColor);
        }

        static final com.mojang.serialization.MapCodec<Colors> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("page_text_color", 0x3F3F3F).forGetter(Colors::pageTextColor),
                Codec.INT.optionalFieldOf("link_color", 0x0000EE).forGetter(Colors::linkColor),
                Codec.INT.optionalFieldOf("highlight_color", 0xFFD54F).forGetter(Colors::highlightColor),
                Codec.INT.optionalFieldOf("divider_color", 0x8B7355).forGetter(Colors::dividerColor)
        ).apply(i, Colors::new));
    }

    /** Layout engine inputs derived from this theme (fresh mutable copy each call). */
    public LayoutParams baseParams() {
        LayoutParams p = new LayoutParams();
        p.pageContentWidth = pageContentWidth;
        p.pageContentHeight = pageContentHeight;
        p.lineHeight = lineHeight;
        p.paragraphGap = paragraphGap;
        p.headingGap = headingGap;
        p.gutter = gutter;
        p.bulletIndent = bulletIndent;
        p.dividerHeight = dividerHeight;
        return p;
    }

    public BookTheme withImageFit(ImageFit fit) {
        return new BookTheme(
                bookTexture, widgetsTexture, bookTexWidth, bookTexHeight, textureSheetSize,
                contentLeft, contentTop, titleOffsetY, contentOffsetY, pageLabelInsetY,
                pageContentWidth, pageContentHeight, lineHeight, paragraphGap, headingGap,
                gutter, bulletIndent, dividerHeight,
                pageTextColor, linkColor, highlightColor, dividerColor,
                fit == null ? ImageFit.STRETCH : fit,
                revision + 1
        );
    }

    public BookTheme withRevision(int newRevision) {
        return new BookTheme(
                bookTexture, widgetsTexture, bookTexWidth, bookTexHeight, textureSheetSize,
                contentLeft, contentTop, titleOffsetY, contentOffsetY, pageLabelInsetY,
                pageContentWidth, pageContentHeight, lineHeight, paragraphGap, headingGap,
                gutter, bulletIndent, dividerHeight,
                pageTextColor, linkColor, highlightColor, dividerColor,
                imageFit, newRevision
        );
    }

    /** Fluent builder starting from the built-in example values. */
    public static Builder builder() {
        return Builder.from(BookThemes.DEFAULT);
    }

    public static final class Builder {
        private ResourceLocation bookTexture;
        private ResourceLocation widgetsTexture;
        private int bookTexWidth;
        private int bookTexHeight;
        private int textureSheetSize;
        private int contentLeft;
        private int contentTop;
        private int titleOffsetY;
        private int contentOffsetY;
        private int pageLabelInsetY;
        private int pageContentWidth;
        private int pageContentHeight;
        private int lineHeight;
        private int paragraphGap;
        private int headingGap;
        private int gutter;
        private int bulletIndent;
        private int dividerHeight;
        private int pageTextColor;
        private int linkColor;
        private int highlightColor;
        private int dividerColor;
        private ImageFit imageFit;
        private int revision;

        public static Builder from(BookTheme base) {
            Builder b = new Builder();
            b.bookTexture = base.bookTexture;
            b.widgetsTexture = base.widgetsTexture;
            b.bookTexWidth = base.bookTexWidth;
            b.bookTexHeight = base.bookTexHeight;
            b.textureSheetSize = base.textureSheetSize;
            b.contentLeft = base.contentLeft;
            b.contentTop = base.contentTop;
            b.titleOffsetY = base.titleOffsetY;
            b.contentOffsetY = base.contentOffsetY;
            b.pageLabelInsetY = base.pageLabelInsetY;
            b.pageContentWidth = base.pageContentWidth;
            b.pageContentHeight = base.pageContentHeight;
            b.lineHeight = base.lineHeight;
            b.paragraphGap = base.paragraphGap;
            b.headingGap = base.headingGap;
            b.gutter = base.gutter;
            b.bulletIndent = base.bulletIndent;
            b.dividerHeight = base.dividerHeight;
            b.pageTextColor = base.pageTextColor;
            b.linkColor = base.linkColor;
            b.highlightColor = base.highlightColor;
            b.dividerColor = base.dividerColor;
            b.imageFit = base.imageFit;
            b.revision = base.revision;
            return b;
        }

        public Builder bookTexture(ResourceLocation v) { this.bookTexture = v; return this; }
        public Builder widgetsTexture(ResourceLocation v) { this.widgetsTexture = v; return this; }
        public Builder bookTexWidth(int v) { this.bookTexWidth = v; return this; }
        public Builder bookTexHeight(int v) { this.bookTexHeight = v; return this; }
        public Builder textureSheetSize(int v) { this.textureSheetSize = v; return this; }
        public Builder contentLeft(int v) { this.contentLeft = v; return this; }
        public Builder contentTop(int v) { this.contentTop = v; return this; }
        public Builder titleOffsetY(int v) { this.titleOffsetY = v; return this; }
        public Builder contentOffsetY(int v) { this.contentOffsetY = v; return this; }
        public Builder pageLabelInsetY(int v) { this.pageLabelInsetY = v; return this; }
        public Builder pageContentWidth(int v) { this.pageContentWidth = v; return this; }
        public Builder pageContentHeight(int v) { this.pageContentHeight = v; return this; }
        public Builder lineHeight(int v) { this.lineHeight = v; return this; }
        public Builder paragraphGap(int v) { this.paragraphGap = v; return this; }
        public Builder headingGap(int v) { this.headingGap = v; return this; }
        public Builder gutter(int v) { this.gutter = v; return this; }
        public Builder bulletIndent(int v) { this.bulletIndent = v; return this; }
        public Builder dividerHeight(int v) { this.dividerHeight = v; return this; }
        public Builder pageTextColor(int v) { this.pageTextColor = v; return this; }
        public Builder linkColor(int v) { this.linkColor = v; return this; }
        public Builder highlightColor(int v) { this.highlightColor = v; return this; }
        public Builder dividerColor(int v) { this.dividerColor = v; return this; }
        public Builder imageFit(ImageFit v) { this.imageFit = v; return this; }
        public Builder revision(int v) { this.revision = v; return this; }

        public BookTheme build() {
            return new BookTheme(
                    bookTexture, widgetsTexture, bookTexWidth, bookTexHeight, textureSheetSize,
                    contentLeft, contentTop, titleOffsetY, contentOffsetY, pageLabelInsetY,
                    pageContentWidth, pageContentHeight, lineHeight, paragraphGap, headingGap,
                    gutter, bulletIndent, dividerHeight,
                    pageTextColor, linkColor, highlightColor, dividerColor,
                    imageFit == null ? ImageFit.STRETCH : imageFit,
                    revision
            );
        }
    }
}
