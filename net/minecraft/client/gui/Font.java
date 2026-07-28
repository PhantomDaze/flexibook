package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringDecomposer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class Font implements net.neoforged.neoforge.client.extensions.IFontExtension {
    private static final float EFFECT_DEPTH = 0.01F;
    private static final Vector3f SHADOW_OFFSET = new Vector3f(0.0F, 0.0F, 0.03F);
    public static final int ALPHA_CUTOFF = 8;
    public final int lineHeight = 9;
    public final RandomSource random = RandomSource.create();
    private final Function<ResourceLocation, FontSet> fonts;
    final boolean filterFishyGlyphs;
    private final StringSplitter splitter;

    public Font(Function<ResourceLocation, FontSet> fonts, boolean filterFishyGlyphs) {
        this.fonts = fonts;
        this.filterFishyGlyphs = filterFishyGlyphs;
        this.splitter = new StringSplitter(
            (p_92722_, p_92723_) -> this.getFontSet(p_92723_.getFont()).getGlyphInfo(p_92722_, this.filterFishyGlyphs).getAdvance(p_92723_.isBold())
        );
    }

    FontSet getFontSet(ResourceLocation fontLocation) {
        return this.fonts.apply(fontLocation);
    }

    /**
     * Apply Unicode Bidirectional Algorithm to string and return a new possibly reordered string for visual rendering.
     */
    public String bidirectionalShaping(String text) {
        try {
            Bidi bidi = new Bidi(new ArabicShaping(8).shape(text), 127);
            bidi.setReorderingMode(0);
            return bidi.writeReordered(2);
        } catch (ArabicShapingException arabicshapingexception) {
            return text;
        }
    }

    public int drawInBatch(
        String text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f matrix,
        MultiBufferSource buffer,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLightCoords
    ) {
        return this.drawInBatch(
            text, x, y, color, dropShadow, matrix, buffer, displayMode, backgroundColor, packedLightCoords, this.isBidirectional()
        );
    }

    public int drawInBatch(
        String text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f matrix,
        MultiBufferSource buffer,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLightCoords,
        boolean bidirectional
    ) {
        return this.drawInternal(text, x, y, color, dropShadow, matrix, buffer, displayMode, backgroundColor, packedLightCoords, bidirectional);
    }

    public int drawInBatch(
        Component text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f matrix,
        MultiBufferSource buffer,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLightCoords
    ) {
        return this.drawInBatch(
            text.getVisualOrderText(), x, y, color, dropShadow, matrix, buffer, displayMode, backgroundColor, packedLightCoords
        );
    }

    public int drawInBatch(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f matrix,
        MultiBufferSource buffer,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLightCoords
    ) {
        return this.drawInternal(text, x, y, color, dropShadow, matrix, buffer, displayMode, backgroundColor, packedLightCoords);
    }

    public void drawInBatch8xOutline(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        int backgroundColor,
        Matrix4f matrix,
        MultiBufferSource bufferSource,
        int packedLightCoords
    ) {
        int i = adjustColor(backgroundColor);
        Font.StringRenderOutput font$stringrenderoutput = new Font.StringRenderOutput(
            bufferSource, 0.0F, 0.0F, i, false, matrix, Font.DisplayMode.NORMAL, packedLightCoords
        );

        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                if (j != 0 || k != 0) {
                    float[] afloat = new float[]{x};
                    int l = j;
                    int i1 = k;
                    text.accept((p_168661_, p_168662_, p_168663_) -> {
                        boolean flag = p_168662_.isBold();
                        FontSet fontset = this.getFontSet(p_168662_.getFont());
                        GlyphInfo glyphinfo = fontset.getGlyphInfo(p_168663_, this.filterFishyGlyphs);
                        font$stringrenderoutput.x = afloat[0] + (float)l * glyphinfo.getShadowOffset();
                        font$stringrenderoutput.y = y + (float)i1 * glyphinfo.getShadowOffset();
                        afloat[0] += glyphinfo.getAdvance(flag);
                        return font$stringrenderoutput.accept(p_168661_, p_168662_.withColor(i), p_168663_);
                    });
                }
            }
        }

        Font.StringRenderOutput font$stringrenderoutput1 = new Font.StringRenderOutput(
            bufferSource, x, y, adjustColor(color), false, matrix, Font.DisplayMode.POLYGON_OFFSET, packedLightCoords
        );
        text.accept(font$stringrenderoutput1);
        font$stringrenderoutput1.finish(0, x);
    }

    private static int adjustColor(int color) {
        return (color & -67108864) == 0 ? color | 0xFF000000 : color;
    }

    private int drawInternal(
        String text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f matrix,
        MultiBufferSource buffer,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLightCoords,
        boolean bidirectional
    ) {
        if (bidirectional) {
            text = this.bidirectionalShaping(text);
        }

        color = adjustColor(color);
        Matrix4f matrix4f = new Matrix4f(matrix);
        if (dropShadow) {
            this.renderText(text, x, y, color, true, matrix, buffer, displayMode, backgroundColor, packedLightCoords);
            matrix4f.translate(SHADOW_OFFSET);
        }

        x = this.renderText(text, x, y, color, false, matrix4f, buffer, displayMode, backgroundColor, packedLightCoords);
        return (int)x + (dropShadow ? 1 : 0);
    }

    private int drawInternal(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f matrix,
        MultiBufferSource buffer,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLightCoords
    ) {
        color = adjustColor(color);
        Matrix4f matrix4f = new Matrix4f(matrix);
        if (dropShadow) {
            this.renderText(text, x, y, color, true, matrix, buffer, displayMode, backgroundColor, packedLightCoords);
            matrix4f.translate(SHADOW_OFFSET);
        }

        x = this.renderText(text, x, y, color, false, matrix4f, buffer, displayMode, backgroundColor, packedLightCoords);
        return (int)x + (dropShadow ? 1 : 0);
    }

    private float renderText(
        String text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f matrix,
        MultiBufferSource buffer,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLightCoords
    ) {
        Font.StringRenderOutput font$stringrenderoutput = new Font.StringRenderOutput(
            buffer, x, y, color, dropShadow, matrix, displayMode, packedLightCoords
        );
        StringDecomposer.iterateFormatted(text, Style.EMPTY, font$stringrenderoutput);
        return font$stringrenderoutput.finish(backgroundColor, x);
    }

    private float renderText(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f matrix,
        MultiBufferSource buffer,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLightCoords
    ) {
        Font.StringRenderOutput font$stringrenderoutput = new Font.StringRenderOutput(
            buffer, x, y, color, dropShadow, matrix, displayMode, packedLightCoords
        );
        text.accept(font$stringrenderoutput);
        return font$stringrenderoutput.finish(backgroundColor, x);
    }

    void renderChar(
        BakedGlyph glyph,
        boolean bold,
        boolean italic,
        float boldOffset,
        float x,
        float y,
        Matrix4f matrix,
        VertexConsumer buffer,
        float red,
        float green,
        float blue,
        float alpha,
        int packedLight
    ) {
        glyph.render(italic, x, y, matrix, buffer, red, green, blue, alpha, packedLight);
        if (bold) {
            glyph.render(italic, x + boldOffset, y, matrix, buffer, red, green, blue, alpha, packedLight);
        }
    }

    /**
     * Returns the width of this string. Equivalent of FontMetrics.stringWidth(String s).
     */
    public int width(String text) {
        return Mth.ceil(this.splitter.stringWidth(text));
    }

    public int width(FormattedText text) {
        return Mth.ceil(this.splitter.stringWidth(text));
    }

    public int width(FormattedCharSequence text) {
        return Mth.ceil(this.splitter.stringWidth(text));
    }

    public String plainSubstrByWidth(String text, int maxWidth, boolean tail) {
        return tail ? this.splitter.plainTailByWidth(text, maxWidth, Style.EMPTY) : this.splitter.plainHeadByWidth(text, maxWidth, Style.EMPTY);
    }

    public String plainSubstrByWidth(String text, int maxWidth) {
        return this.splitter.plainHeadByWidth(text, maxWidth, Style.EMPTY);
    }

    public FormattedText substrByWidth(FormattedText text, int maxWidth) {
        return this.splitter.headByWidth(text, maxWidth, Style.EMPTY);
    }

    /**
     * Returns the height (in pixels) of the given string if it is wordwrapped to the given max width.
     */
    public int wordWrapHeight(String text, int maxWidth) {
        return 9 * this.splitter.splitLines(text, maxWidth, Style.EMPTY).size();
    }

    public int wordWrapHeight(FormattedText text, int maxWidth) {
        return 9 * this.splitter.splitLines(text, maxWidth, Style.EMPTY).size();
    }

    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
        return Language.getInstance().getVisualOrder(this.splitter.splitLines(text, maxWidth, Style.EMPTY));
    }

    public boolean isBidirectional() {
        return Language.getInstance().isDefaultRightToLeft();
    }

    public StringSplitter getSplitter() {
        return this.splitter;
    }

    @Override public Font self() { return this; }

    @OnlyIn(Dist.CLIENT)
    public static enum DisplayMode {
        NORMAL,
        SEE_THROUGH,
        POLYGON_OFFSET;
    }

    @OnlyIn(Dist.CLIENT)
    class StringRenderOutput implements FormattedCharSink {
        final MultiBufferSource bufferSource;
        private final boolean dropShadow;
        private final float dimFactor;
        private final float r;
        private final float g;
        private final float b;
        private final float a;
        private final Matrix4f pose;
        private final Font.DisplayMode mode;
        private final int packedLightCoords;
        float x;
        float y;
        @Nullable
        private List<BakedGlyph.Effect> effects;

        private void addEffect(BakedGlyph.Effect effect) {
            if (this.effects == null) {
                this.effects = Lists.newArrayList();
            }

            this.effects.add(effect);
        }

        public StringRenderOutput(
            MultiBufferSource bufferSource,
            float x,
            float y,
            int color,
            boolean dropShadow,
            Matrix4f pose,
            Font.DisplayMode mode,
            int packedLightCoords
        ) {
            this.bufferSource = bufferSource;
            this.x = x;
            this.y = y;
            this.dropShadow = dropShadow;
            this.dimFactor = dropShadow ? 0.25F : 1.0F;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F * this.dimFactor;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F * this.dimFactor;
            this.b = (float)(color & 0xFF) / 255.0F * this.dimFactor;
            this.a = (float)(color >> 24 & 0xFF) / 255.0F;
            this.pose = pose;
            this.mode = mode;
            this.packedLightCoords = packedLightCoords;
        }

        /**
         * Accepts a single code point from a {@link net.minecraft.util.FormattedCharSequence}.
         * @return {@code true} to accept more characters, {@code false} to stop traversing the sequence.
         *
         * @param positionInCurrentSequence Contains the relative position of the
         *                                  character in the current sub-sequence. If
         *                                  multiple formatted char sequences have been
         *                                  combined, this value will reset to {@code 0}
         *                                  after each sequence has been fully consumed.
         */
        @Override
        public boolean accept(int positionInCurrentSequence, Style style, int codePoint) {
            FontSet fontset = Font.this.getFontSet(style.getFont());
            GlyphInfo glyphinfo = fontset.getGlyphInfo(codePoint, Font.this.filterFishyGlyphs);
            BakedGlyph bakedglyph = style.isObfuscated() && codePoint != 32 ? fontset.getRandomGlyph(glyphinfo) : fontset.getGlyph(codePoint);
            boolean flag = style.isBold();
            float f3 = this.a;
            TextColor textcolor = style.getColor();
            float f;
            float f1;
            float f2;
            if (textcolor != null) {
                int i = textcolor.getValue();
                f = (float)(i >> 16 & 0xFF) / 255.0F * this.dimFactor;
                f1 = (float)(i >> 8 & 0xFF) / 255.0F * this.dimFactor;
                f2 = (float)(i & 0xFF) / 255.0F * this.dimFactor;
            } else {
                f = this.r;
                f1 = this.g;
                f2 = this.b;
            }

            if (!(bakedglyph instanceof EmptyGlyph)) {
                float f5 = flag ? glyphinfo.getBoldOffset() : 0.0F;
                float f4 = this.dropShadow ? glyphinfo.getShadowOffset() : 0.0F;
                VertexConsumer vertexconsumer = this.bufferSource.getBuffer(bakedglyph.renderType(this.mode));
                Font.this.renderChar(
                    bakedglyph, flag, style.isItalic(), f5, this.x + f4, this.y + f4, this.pose, vertexconsumer, f, f1, f2, f3, this.packedLightCoords
                );
            }

            float f6 = glyphinfo.getAdvance(flag);
            float f7 = this.dropShadow ? 1.0F : 0.0F;
            if (style.isStrikethrough()) {
                this.addEffect(new BakedGlyph.Effect(this.x + f7 - 1.0F, this.y + f7 + 4.5F, this.x + f7 + f6, this.y + f7 + 4.5F - 1.0F, 0.01F, f, f1, f2, f3));
            }

            if (style.isUnderlined()) {
                this.addEffect(new BakedGlyph.Effect(this.x + f7 - 1.0F, this.y + f7 + 9.0F, this.x + f7 + f6, this.y + f7 + 9.0F - 1.0F, 0.01F, f, f1, f2, f3));
            }

            this.x += f6;
            return true;
        }

        public float finish(int backgroundColor, float x) {
            if (backgroundColor != 0) {
                float f = (float)(backgroundColor >> 24 & 0xFF) / 255.0F;
                float f1 = (float)(backgroundColor >> 16 & 0xFF) / 255.0F;
                float f2 = (float)(backgroundColor >> 8 & 0xFF) / 255.0F;
                float f3 = (float)(backgroundColor & 0xFF) / 255.0F;
                this.addEffect(new BakedGlyph.Effect(x - 1.0F, this.y + 9.0F, this.x + 1.0F, this.y - 1.0F, 0.01F, f1, f2, f3, f));
            }

            if (this.effects != null) {
                BakedGlyph bakedglyph = Font.this.getFontSet(Style.DEFAULT_FONT).whiteGlyph();
                VertexConsumer vertexconsumer = this.bufferSource.getBuffer(bakedglyph.renderType(this.mode));

                for (BakedGlyph.Effect bakedglyph$effect : this.effects) {
                    bakedglyph.renderEffect(bakedglyph$effect, this.pose, vertexconsumer, this.packedLightCoords);
                }
            }

            return this.x;
        }
    }
}
