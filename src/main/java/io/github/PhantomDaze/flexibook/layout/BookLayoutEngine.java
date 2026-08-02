package io.github.PhantomDaze.flexibook.layout;

import io.github.PhantomDaze.flexibook.util.Compat;

import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.FlexiBookFonts;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.content.TranslatableText;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adaptive book layout: resolve translations, measure with Font, paginate,
 * and optionally shrink scale / switch to two columns when content is long.
 */
public final class BookLayoutEngine {
    private static final LayoutCache CACHE = new LayoutCache(32);

    private BookLayoutEngine() {}

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Primary layout entry: providers drive measurement and translation for identical behavior between MC and editor.
     */
    public static List<RenderedPage> layout(AdaptiveBookContent content,
                                            TextMeasurer measurer,
                                            TranslationProvider translator,
                                            BookTheme theme,
                                            String languageCode,
                                            int guiScale,
                                            String searchQuery) {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        ResourceLocation resolvedFont = content.resolvedFont();
        String fontKey = resolvedFont.toString();
        String key = content.hashCode() + "|" + languageCode + "|" + guiScale + "|" + theme.revision() + "|" + fontKey + "|" + q;
        List<RenderedPage> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        List<BookElement> elements = content.resolveElements();
        float startScale = 1.0f;
        if (looksMostlyCjk(elements, translator)) {
            startScale = 0.92f;
        }

        LayoutParams params = theme.baseParams();
        params.scale = startScale;
        params.columns = 1;

        Optional<ResourceLocation> bookFont = Optional.of(resolvedFont);
        List<RenderedPage> pages = tryLayout(elements, measurer, translator, params, q, bookFont);

        int guard = 0;
        while (guard++ < 12 && (pages.size() >= 60 || isOvercrowded(pages, params))) {
            if (params.scale > 0.6f + 1e-3f) {
                params.scale = Math.max(0.6f, params.scale - 0.1f);
            } else if (params.columns < 2) {
                params.columns = 2;
                params.scale = Math.max(0.85f, startScale - 0.05f);
            } else {
                break;
            }
            pages = tryLayout(elements, measurer, translator, params, q, bookFont);
        }

        if (pages.isEmpty()) {
            RenderedPage empty = new RenderedPage();
            StyleFlags emptyStyle = applyBookFont(StyleFlags.EMPTY, bookFont);
            String emptyText = translator != null ? translator.get("flexibook.book.empty.body") : "flexibook.book.empty.body";
            empty.add(new RenderedElement.TextLine(0, 0, 1f, emptyText, emptyStyle, Optional.empty(), 100, 9, false));
            pages = List.of(empty);
        }

        CACHE.put(key, pages);
        return pages;
    }

    /**
     * Backward-compatible overload for inside-MC usage. Delegates to the provider path via MC adapters.
     */
    public static List<RenderedPage> layout(AdaptiveBookContent content, Font font, BookTheme theme, String languageCode, int guiScale, String searchQuery) {
        return layout(content, new McTextMeasurer(font), new McTranslationProvider(), theme, languageCode, guiScale, searchQuery);
    }

    private static boolean isOvercrowded(List<RenderedPage> pages, LayoutParams params) {
        if (pages.size() > 40) {
            return true;
        }
        // if average elements per page is huge at large scale, consider overcrowded
        return params.scale > 0.95f && pages.size() > 20;
    }

    private static boolean looksMostlyCjk(List<BookElement> elements, TranslationProvider translator) {
        int cjk = 0;
        int total = 0;
        for (BookElement el : elements) {
            // instanceof chains (not pattern-switch) — final on Java 17; sealed switch is preview.
            String s;
            if (el instanceof BookElement.Heading h) {
                s = resolveForDetection(h.text(), translator);
            } else if (el instanceof BookElement.Paragraph p) {
                s = joinSpans(p.spans(), translator);
            } else if (el instanceof BookElement.Bullet b) {
                s = joinSpans(b.spans(), translator);
            } else {
                s = "";
            }
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) {
                    continue;
                }
                total++;
                Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
                if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                        || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                        || block == Character.UnicodeBlock.HIRAGANA
                        || block == Character.UnicodeBlock.KATAKANA
                        || block == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                    cjk++;
                }
            }
        }
        return total > 0 && (cjk / (float) total) > 0.3f;
    }

    private static String resolveForDetection(TranslatableText t, TranslationProvider translator) {
        if (translator == null) return t.resolvePlain();
        return t.resolvePlain(translator);
    }

    private static String joinSpans(List<InlineSpan> spans, TranslationProvider translator) {
        StringBuilder sb = new StringBuilder();
        for (InlineSpan s : spans) {
            sb.append(resolveSpanPlain(s, translator));
        }
        return sb.toString();
    }

    private static String resolveSpanPlain(InlineSpan span, TranslationProvider translator) {
        try {
            // Prefer provider path if available
            var m = InlineSpan.class.getMethod("resolvePlain", TranslationProvider.class);
            Object res = m.invoke(span, translator);
            return res == null ? "" : res.toString();
        } catch (Exception ignored) {
            // Fallback to MC path
            return span.resolvePlain();
        }
    }

    private static List<RenderedPage> tryLayout(List<BookElement> elements,
                                                TextMeasurer measurer,
                                                TranslationProvider translator,
                                                LayoutParams params,
                                                String searchLower,
                                                Optional<ResourceLocation> bookFont) {
        List<RenderedPage> pages = new ArrayList<>();
        RenderedPage page = new RenderedPage();
        pages.add(page);

        int colW = params.columnWidth();
        float[] colY = new float[params.columns];
        int col = 0;

        for (BookElement element : elements) {
            // instanceof chains (not pattern-switch) — final on Java 17; sealed switch is preview.
            if (element instanceof BookElement.Heading heading) {
                float sizeMul = heading.level() <= 1 ? 1.35f : 1.15f;
                float scale = params.scale * sizeMul;
                String text = resolveTranslatablePlain(heading.text(), translator);
                boolean hi = matchesSearch(text, searchLower);
                StyleFlags style = StyleFlags.EMPTY.withBold(true);
                style = applyFontOverride(style, heading.font(), bookFont);
                col = placeWrappedText(pages, page, colY, col, params, measurer, text, style, Optional.empty(), scale, 0, params.headingGap, hi);
                page = Compat.last(pages);
            } else if (element instanceof BookElement.Paragraph paragraph) {
                col = placeInlineSpans(pages, page, colY, col, params, measurer, translator, paragraph.spans(), 0, params.paragraphGap, searchLower, bookFont);
                page = Compat.last(pages);
            } else if (element instanceof BookElement.Bullet bullet) {
                float markerScale = params.scale;
                float x = columnX(col, params);
                if (colY[col] + params.lineHeight * markerScale > params.pageContentHeight) {
                    int next = advanceColumn(pages, colY, col, params);
                    col = next;
                    page = Compat.last(pages);
                    x = columnX(col, params);
                }
                StyleFlags markerStyle = applyBookFont(StyleFlags.EMPTY, bookFont);
                int markerW = measureWidth(measurer, "•", markerStyle, Optional.empty());
                page.add(new RenderedElement.TextLine(
                        x, colY[col], markerScale, "•", markerStyle, Optional.empty(),
                        markerW, params.lineHeight, false
                ));
                col = placeInlineSpans(pages, page, colY, col, params, measurer, translator, bullet.spans(), params.bulletIndent, params.paragraphGap, searchLower, bookFont);
                page = Compat.last(pages);
            } else if (element instanceof BookElement.LineBreak) {
                colY[col] += params.lineHeight * params.scale * 0.5f;
            } else if (element instanceof BookElement.Divider) {
                float h = params.dividerHeight * params.scale;
                if (colY[col] + h > params.pageContentHeight) {
                    col = advanceColumn(pages, colY, col, params);
                    page = Compat.last(pages);
                }
                float x = columnX(col, params);
                page.add(new RenderedElement.DividerLine(x, colY[col], params.scale, colW, h));
                colY[col] += h + params.paragraphGap * params.scale;
            } else if (element instanceof BookElement.Image image) {
                float w = image.width() * params.scale;
                float h = image.height() * params.scale;
                if (w > colW) {
                    float fit = colW / (float) image.width();
                    w = image.width() * fit;
                    h = image.height() * fit;
                }
                if (colY[col] + h > params.pageContentHeight) {
                    col = advanceColumn(pages, colY, col, params);
                    page = Compat.last(pages);
                }
                float x = columnX(col, params);
                page.add(new RenderedElement.ImageBlock(x, colY[col], 1f, image.src(), Math.round(w), Math.round(h), image.tooltipKey()));
                colY[col] += h + params.paragraphGap * params.scale;
            } else if (element instanceof BookElement.Box box) {
                for (BookElement child : box.children()) {
                    col = layoutOne(child, pages, page, colY, col, params, measurer, translator, searchLower, bookFont);
                    page = Compat.last(pages);
                }
            }
        }
        return pages;
    }

    private static int layoutOne(BookElement element, List<RenderedPage> pages, RenderedPage page, float[] colY, int col,
                                 LayoutParams params, TextMeasurer measurer, TranslationProvider translator,
                                 String searchLower, Optional<ResourceLocation> bookFont) {
        // instanceof chains (not pattern-switch) — final on Java 17; sealed switch is preview.
        if (element instanceof BookElement.Heading heading) {
            float sizeMul = heading.level() <= 1 ? 1.35f : 1.15f;
            float scale = params.scale * sizeMul;
            String text = resolveTranslatablePlain(heading.text(), translator);
            boolean hi = matchesSearch(text, searchLower);
            StyleFlags style = applyFontOverride(StyleFlags.EMPTY.withBold(true), heading.font(), bookFont);
            return placeWrappedText(pages, page, colY, col, params, measurer, text, style, Optional.empty(), scale, 0, params.headingGap, hi);
        }
        if (element instanceof BookElement.Paragraph paragraph) {
            return placeInlineSpans(pages, page, colY, col, params, measurer, translator, paragraph.spans(), 0, params.paragraphGap, searchLower, bookFont);
        }
        if (element instanceof BookElement.Bullet bullet) {
            float x = columnX(col, params);
            if (colY[col] + params.lineHeight * params.scale > params.pageContentHeight) {
                col = advanceColumn(pages, colY, col, params);
                page = Compat.last(pages);
                x = columnX(col, params);
            }
            StyleFlags markerStyle = applyBookFont(StyleFlags.EMPTY, bookFont);
            int markerW = measureWidth(measurer, "•", markerStyle, Optional.empty());
            Compat.last(pages).add(new RenderedElement.TextLine(
                    x, colY[col], params.scale, "•", markerStyle, Optional.empty(),
                    markerW, params.lineHeight, false
            ));
            return placeInlineSpans(pages, Compat.last(pages), colY, col, params, measurer, translator, bullet.spans(), params.bulletIndent, params.paragraphGap, searchLower, bookFont);
        }
        if (element instanceof BookElement.LineBreak) {
            colY[col] += params.lineHeight * params.scale * 0.5f;
            return col;
        }
        if (element instanceof BookElement.Divider) {
            float h = params.dividerHeight * params.scale;
            if (colY[col] + h > params.pageContentHeight) {
                col = advanceColumn(pages, colY, col, params);
            }
            float x = columnX(col, params);
            Compat.last(pages).add(new RenderedElement.DividerLine(x, colY[col], params.scale, params.columnWidth(), h));
            colY[col] += h + params.paragraphGap * params.scale;
            return col;
        }
        if (element instanceof BookElement.Image image) {
            float w = image.width() * params.scale;
            float h = image.height() * params.scale;
            int colW = params.columnWidth();
            if (w > colW) {
                float fit = colW / (float) image.width();
                w = image.width() * fit;
                h = image.height() * fit;
            }
            if (colY[col] + h > params.pageContentHeight) {
                col = advanceColumn(pages, colY, col, params);
            }
            float x = columnX(col, params);
            Compat.last(pages).add(new RenderedElement.ImageBlock(x, colY[col], 1f, image.src(), Math.round(w), Math.round(h), image.tooltipKey()));
            colY[col] += h + params.paragraphGap * params.scale;
            return col;
        }
        if (element instanceof BookElement.Box box) {
            int c = col;
            for (BookElement child : box.children()) {
                c = layoutOne(child, pages, Compat.last(pages), colY, c, params, measurer, translator, searchLower, bookFont);
            }
            return c;
        }
        return col;
    }

    private static int placeInlineSpans(List<RenderedPage> pages, RenderedPage page, float[] colY, int col,
                                        LayoutParams params, TextMeasurer measurer, TranslationProvider translator,
                                        List<InlineSpan> spans,
                                        int indent, int gapAfter, String searchLower, Optional<ResourceLocation> bookFont) {
        for (InlineSpan span : spans) {
            String text = resolveSpanPlain(span, translator);
            if (text.isEmpty()) {
                continue;
            }
            StyleFlags style = applyBookFont(span.style(), bookFont);
            String[] parts = text.split("\n", -1);
            for (int pi = 0; pi < parts.length; pi++) {
                if (pi > 0) {
                    colY[col] += params.lineHeight * params.scale;
                }
                boolean hi = matchesSearch(parts[pi], searchLower);
                col = placeWrappedText(pages, Compat.last(pages), colY, col, params, measurer,
                        parts[pi], style, span.link(), params.scale, indent, 0, hi);
            }
        }
        colY[col] += gapAfter * params.scale;
        return col;
    }

    /** Span/heading font wins; otherwise fall back to the book default. */
    private static StyleFlags applyFontOverride(StyleFlags base, Optional<ResourceLocation> local, Optional<ResourceLocation> bookFont) {
        if (local != null && local.isPresent()) {
            return base.withFont(local.get());
        }
        return applyBookFont(base, bookFont);
    }

    private static StyleFlags applyBookFont(StyleFlags style, Optional<ResourceLocation> bookFont) {
        if (style.font().isPresent() || bookFont == null || bookFont.isEmpty()) {
            return style;
        }
        return style.withFont(bookFont.get());
    }

    private static int placeWrappedText(List<RenderedPage> pages, RenderedPage page, float[] colY, int col,
                                        LayoutParams params, TextMeasurer measurer, String text, StyleFlags style,
                                        Optional<LinkAction> link, float scale, int indent, int gapAfter, boolean highlight) {
        if (text == null || text.isEmpty()) {
            colY[col] += gapAfter * scale;
            return col;
        }
        int colW = params.columnWidth() - indent;
        if (colW < 8) {
            colW = 8;
        }
        int maxUnscaled = Math.max(4, Mth.floor(colW / scale));
        StyleFlags measureStyle = link.isPresent() ? style.withUnderline(true) : style;

        List<String> lines = wrap(measurer, text, maxUnscaled, measureStyle);
        float lineH = params.lineHeight * scale;

        for (String line : lines) {
            if (colY[col] + lineH > params.pageContentHeight) {
                col = advanceColumn(pages, colY, col, params);
                page = Compat.last(pages);
            }
            float x = columnX(col, params) + indent;
            float w = measureWidth(measurer, line, measureStyle, Optional.empty());
            Compat.last(pages).add(new RenderedElement.TextLine(
                    x, colY[col], scale, line, style, link, w, params.lineHeight, highlight
            ));
            colY[col] += lineH;
        }
        colY[col] += gapAfter * scale;
        return col;
    }

    private static int measureWidth(TextMeasurer measurer, String text, StyleFlags style, Optional<ResourceLocation> fontId) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return measurer.width(text, style, fontId);
    }

    private static List<String> wrap(TextMeasurer measurer, String text, int maxWidth, StyleFlags style) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0) {
            lines.add(text);
            return lines;
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            int low = start + 1;
            int high = len;
            int best = start + 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                String sub = text.substring(start, mid);
                if (measureWidth(measurer, sub, style, Optional.empty()) <= maxWidth) {
                    best = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            int breakAt = best;
            if (best < len) {
                int space = text.lastIndexOf(' ', best - 1);
                if (space >= start + 1) {
                    breakAt = space + 1;
                }
            }
            if (breakAt <= start) {
                breakAt = Math.min(start + 1, len);
            }
            lines.add(text.substring(start, breakAt));
            start = breakAt;
            while (start < len && text.charAt(start) == ' ') {
                start++;
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    // ---- Legacy Font-based internals kept for any direct callers; prefer provider path above ----

    private static int placeWrappedText(List<RenderedPage> pages, RenderedPage page, float[] colY, int col,
                                        LayoutParams params, Font font, String text, StyleFlags style,
                                        Optional<LinkAction> link, float scale, int indent, int gapAfter, boolean highlight) {
        if (text == null || text.isEmpty()) {
            colY[col] += gapAfter * scale;
            return col;
        }
        int colW = params.columnWidth() - indent;
        if (colW < 8) {
            colW = 8;
        }
        int maxUnscaled = Math.max(4, Mth.floor(colW / scale));
        StyleFlags measureStyle = link.isPresent() ? style.withUnderline(true) : style;
        Style mcStyle = toMeasureStyle(measureStyle);

        List<String> lines = wrap(font, text, maxUnscaled, mcStyle);
        float lineH = params.lineHeight * scale;

        for (String line : lines) {
            if (colY[col] + lineH > params.pageContentHeight) {
                col = advanceColumn(pages, colY, col, params);
                page = Compat.last(pages);
            }
            float x = columnX(col, params) + indent;
            float w = measureWidth(font, line, mcStyle);
            Compat.last(pages).add(new RenderedElement.TextLine(
                    x, colY[col], scale, line, style, link, w, params.lineHeight, highlight
            ));
            colY[col] += lineH;
        }
        colY[col] += gapAfter * scale;
        return col;
    }

    private static int measureWidth(Font font, String text, Style style) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return font.width(Component.literal(text).withStyle(style));
    }

    private static List<String> wrap(Font font, String text, int maxWidth, Style style) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0) {
            lines.add(text);
            return lines;
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            int low = start + 1;
            int high = len;
            int best = start + 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                String sub = text.substring(start, mid);
                if (measureWidth(font, sub, style) <= maxWidth) {
                    best = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            int breakAt = best;
            if (best < len) {
                int space = text.lastIndexOf(' ', best - 1);
                if (space >= start + 1) {
                    breakAt = space + 1;
                }
            }
            if (breakAt <= start) {
                breakAt = Math.min(start + 1, len);
            }
            lines.add(text.substring(start, breakAt));
            start = breakAt;
            while (start < len && text.charAt(start) == ' ') {
                start++;
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static int advanceColumn(List<RenderedPage> pages, float[] colY, int col, LayoutParams params) {
        if (col + 1 < params.columns) {
            colY[col + 1] = 0;
            return col + 1;
        }
        // new page
        pages.add(new RenderedPage());
        for (int i = 0; i < colY.length; i++) {
            colY[i] = 0;
        }
        return 0;
    }

    private static float columnX(int col, LayoutParams params) {
        if (params.columns <= 1) {
            return 0;
        }
        return col * (params.columnWidth() + params.gutter);
    }

    private static boolean matchesSearch(String text, String searchLower) {
        if (searchLower == null || searchLower.isEmpty() || text == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(searchLower);
    }

    private static String resolveTranslatablePlain(TranslatableText t, TranslationProvider translator) {
        if (translator == null) return t.resolvePlain();
        return t.resolvePlain(translator);
    }

    /** Bold/italic/font change glyph advances — used by legacy Font path. */
    private static Style toMeasureStyle(StyleFlags flags) {
        Style style = Style.EMPTY;
        if (flags.bold()) {
            style = style.withBold(true);
        }
        if (flags.italic()) {
            style = style.withItalic(true);
        }
        if (flags.underline()) {
            style = style.withUnderlined(true);
        }
        if (flags.font().isPresent()) {
            style = io.github.PhantomDaze.flexibook.util.McFonts.withFont(style, flags.font().get());
        }
        return style;
    }
}
