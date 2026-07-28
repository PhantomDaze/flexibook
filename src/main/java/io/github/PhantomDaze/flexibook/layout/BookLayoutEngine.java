package io.github.PhantomDaze.flexibook.layout;

import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
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

    public static List<RenderedPage> layout(AdaptiveBookContent content, Font font, BookTheme theme, String languageCode, int guiScale, String searchQuery) {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        String fontKey = content.defaultFont().map(ResourceLocation::toString).orElse("-");
        String key = content.hashCode() + "|" + languageCode + "|" + guiScale + "|" + theme.revision() + "|" + fontKey + "|" + q;
        List<RenderedPage> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        List<BookElement> elements = content.resolveElements();
        float startScale = 1.0f;
        if (looksMostlyCjk(elements)) {
            startScale = 0.92f;
        }

        LayoutParams params = theme.baseParams();
        params.scale = startScale;
        params.columns = 1;

        Optional<ResourceLocation> bookFont = content.defaultFont();
        List<RenderedPage> pages = tryLayout(elements, font, params, q, bookFont);

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
            pages = tryLayout(elements, font, params, q, bookFont);
        }

        if (pages.isEmpty()) {
            RenderedPage empty = new RenderedPage();
            StyleFlags emptyStyle = applyBookFont(StyleFlags.EMPTY, bookFont);
            empty.add(new RenderedElement.TextLine(0, 0, 1f, I18n.get("flexibook.book.empty.body"), emptyStyle, Optional.empty(), 100, 9, false));
            pages = List.of(empty);
        }

        CACHE.put(key, pages);
        return pages;
    }

    private static boolean isOvercrowded(List<RenderedPage> pages, LayoutParams params) {
        if (pages.size() > 40) {
            return true;
        }
        // if average elements per page is huge at large scale, consider overcrowded
        return params.scale > 0.95f && pages.size() > 20;
    }

    private static boolean looksMostlyCjk(List<BookElement> elements) {
        int cjk = 0;
        int total = 0;
        for (BookElement el : elements) {
            String s = switch (el) {
                case BookElement.Heading h -> h.text().resolvePlain();
                case BookElement.Paragraph p -> joinSpans(p.spans());
                case BookElement.Bullet b -> joinSpans(b.spans());
                default -> "";
            };
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

    private static String joinSpans(List<InlineSpan> spans) {
        StringBuilder sb = new StringBuilder();
        for (InlineSpan s : spans) {
            sb.append(s.resolvePlain());
        }
        return sb.toString();
    }

    private static List<RenderedPage> tryLayout(List<BookElement> elements, Font font, LayoutParams params, String searchLower, Optional<ResourceLocation> bookFont) {
        List<RenderedPage> pages = new ArrayList<>();
        RenderedPage page = new RenderedPage();
        pages.add(page);

        int colW = params.columnWidth();
        float[] colY = new float[params.columns];
        int col = 0;

        for (BookElement element : elements) {
            switch (element) {
                case BookElement.Heading heading -> {
                    float sizeMul = heading.level() <= 1 ? 1.35f : 1.15f;
                    float scale = params.scale * sizeMul;
                    String text = heading.text().resolvePlain();
                    boolean hi = matchesSearch(text, searchLower);
                    StyleFlags style = StyleFlags.EMPTY.withBold(true);
                    style = applyFontOverride(style, heading.font(), bookFont);
                    col = placeWrappedText(pages, page, colY, col, params, font, text, style, Optional.empty(), scale, 0, params.headingGap, hi);
                    page = pages.getLast();
                }
                case BookElement.Paragraph paragraph -> {
                    col = placeInlineSpans(pages, page, colY, col, params, font, paragraph.spans(), 0, params.paragraphGap, searchLower, bookFont);
                    page = pages.getLast();
                }
                case BookElement.Bullet bullet -> {
                    // bullet marker
                    float markerScale = params.scale;
                    float x = columnX(col, params);
                    if (colY[col] + params.lineHeight * markerScale > params.pageContentHeight) {
                        int next = advanceColumn(pages, colY, col, params);
                        col = next;
                        page = pages.getLast();
                        x = columnX(col, params);
                    }
                    StyleFlags markerStyle = applyBookFont(StyleFlags.EMPTY, bookFont);
                    page.add(new RenderedElement.TextLine(
                            x, colY[col], markerScale, "•", markerStyle, Optional.empty(),
                            measureWidth(font, "•", toMeasureStyle(markerStyle)), params.lineHeight, false
                    ));
                    col = placeInlineSpans(pages, page, colY, col, params, font, bullet.spans(), params.bulletIndent, params.paragraphGap, searchLower, bookFont);
                    page = pages.getLast();
                }
                case BookElement.LineBreak ignored -> {
                    colY[col] += params.lineHeight * params.scale * 0.5f;
                }
                case BookElement.Divider ignored -> {
                    float h = params.dividerHeight * params.scale;
                    if (colY[col] + h > params.pageContentHeight) {
                        col = advanceColumn(pages, colY, col, params);
                        page = pages.getLast();
                    }
                    float x = columnX(col, params);
                    page.add(new RenderedElement.DividerLine(x, colY[col], params.scale, colW, h));
                    colY[col] += h + params.paragraphGap * params.scale;
                }
                case BookElement.Image image -> {
                    float w = image.width() * params.scale;
                    float h = image.height() * params.scale;
                    if (w > colW) {
                        float fit = colW / (float) image.width();
                        w = image.width() * fit;
                        h = image.height() * fit;
                    }
                    if (colY[col] + h > params.pageContentHeight) {
                        col = advanceColumn(pages, colY, col, params);
                        page = pages.getLast();
                    }
                    float x = columnX(col, params);
                    page.add(new RenderedElement.ImageBlock(x, colY[col], 1f, image.src(), Math.round(w), Math.round(h), image.tooltipKey()));
                    colY[col] += h + params.paragraphGap * params.scale;
                }
                case BookElement.Box box -> {
                    for (BookElement child : box.children()) {
                        col = layoutOne(child, pages, page, colY, col, params, font, searchLower, bookFont);
                        page = pages.getLast();
                    }
                }
            }
        }
        return pages;
    }

    private static int layoutOne(BookElement element, List<RenderedPage> pages, RenderedPage page, float[] colY, int col,
                                 LayoutParams params, Font font, String searchLower, Optional<ResourceLocation> bookFont) {
        return switch (element) {
            case BookElement.Heading heading -> {
                float sizeMul = heading.level() <= 1 ? 1.35f : 1.15f;
                float scale = params.scale * sizeMul;
                String text = heading.text().resolvePlain();
                boolean hi = matchesSearch(text, searchLower);
                StyleFlags style = applyFontOverride(StyleFlags.EMPTY.withBold(true), heading.font(), bookFont);
                yield placeWrappedText(pages, page, colY, col, params, font, text, style, Optional.empty(), scale, 0, params.headingGap, hi);
            }
            case BookElement.Paragraph paragraph -> placeInlineSpans(pages, page, colY, col, params, font, paragraph.spans(), 0, params.paragraphGap, searchLower, bookFont);
            case BookElement.Bullet bullet -> {
                float x = columnX(col, params);
                if (colY[col] + params.lineHeight * params.scale > params.pageContentHeight) {
                    col = advanceColumn(pages, colY, col, params);
                    page = pages.getLast();
                    x = columnX(col, params);
                }
                StyleFlags markerStyle = applyBookFont(StyleFlags.EMPTY, bookFont);
                pages.getLast().add(new RenderedElement.TextLine(
                        x, colY[col], params.scale, "•", markerStyle, Optional.empty(),
                        measureWidth(font, "•", toMeasureStyle(markerStyle)), params.lineHeight, false
                ));
                yield placeInlineSpans(pages, pages.getLast(), colY, col, params, font, bullet.spans(), params.bulletIndent, params.paragraphGap, searchLower, bookFont);
            }
            case BookElement.LineBreak ignored -> {
                colY[col] += params.lineHeight * params.scale * 0.5f;
                yield col;
            }
            case BookElement.Divider ignored -> {
                float h = params.dividerHeight * params.scale;
                if (colY[col] + h > params.pageContentHeight) {
                    col = advanceColumn(pages, colY, col, params);
                }
                float x = columnX(col, params);
                pages.getLast().add(new RenderedElement.DividerLine(x, colY[col], params.scale, params.columnWidth(), h));
                colY[col] += h + params.paragraphGap * params.scale;
                yield col;
            }
            case BookElement.Image image -> {
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
                pages.getLast().add(new RenderedElement.ImageBlock(x, colY[col], 1f, image.src(), Math.round(w), Math.round(h), image.tooltipKey()));
                colY[col] += h + params.paragraphGap * params.scale;
                yield col;
            }
            case BookElement.Box box -> {
                int c = col;
                for (BookElement child : box.children()) {
                    c = layoutOne(child, pages, pages.getLast(), colY, c, params, font, searchLower, bookFont);
                }
                yield c;
            }
        };
    }

    private static int placeInlineSpans(List<RenderedPage> pages, RenderedPage page, float[] colY, int col,
                                        LayoutParams params, Font font, List<InlineSpan> spans,
                                        int indent, int gapAfter, String searchLower, Optional<ResourceLocation> bookFont) {
        // Flatten spans into styled runs, wrap by character/word using font width at scale
        for (InlineSpan span : spans) {
            String text = span.resolvePlain();
            if (text.isEmpty()) {
                continue;
            }
            StyleFlags style = applyBookFont(span.style(), bookFont);
            // handle explicit newlines inside span
            String[] parts = text.split("\n", -1);
            for (int pi = 0; pi < parts.length; pi++) {
                if (pi > 0) {
                    colY[col] += params.lineHeight * params.scale;
                }
                boolean hi = matchesSearch(parts[pi], searchLower);
                col = placeWrappedText(pages, pages.getLast(), colY, col, params, font,
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
        // Work in unscaled font units: available width = colW / scale
        int maxUnscaled = Math.max(4, Mth.floor(colW / scale));
        // Links render underlined; bake that into measurement style for consistent advances.
        StyleFlags measureStyle = link.isPresent() ? style.withUnderline(true) : style;
        Style mcStyle = toMeasureStyle(measureStyle);

        List<String> lines = wrap(font, text, maxUnscaled, mcStyle);
        float lineH = params.lineHeight * scale;

        for (String line : lines) {
            if (colY[col] + lineH > params.pageContentHeight) {
                col = advanceColumn(pages, colY, col, params);
                page = pages.getLast();
            }
            float x = columnX(col, params) + indent;
            float w = measureWidth(font, line, mcStyle);
            pages.getLast().add(new RenderedElement.TextLine(
                    x, colY[col], scale, line, style, link, w, params.lineHeight, highlight
            ));
            colY[col] += lineH;
        }
        colY[col] += gapAfter * scale;
        return col;
    }

    /** Bold/italic/font change glyph advances — always measure with the same Style used at draw time. */
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
            style = style.withFont(flags.font().get());
        }
        return style;
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
            // try break at space
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
}
