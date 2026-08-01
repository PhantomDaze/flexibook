package io.github.PhantomDaze.flexibook.layout;

import io.github.PhantomDaze.flexibook.util.FlexiBookIds;

import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.FlexiBookFonts;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import io.github.PhantomDaze.flexibook.client.theme.BookThemes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic layout test using a fake measurer.
 * Ensures pagination and element placement logic works without a real font.
 */
class BookLayoutEngineTest {

    private static final BookTheme THEME = BookThemes.DEFAULT;

    @Test
    void deterministicPaginationWithFixedWidths() {
        AdaptiveBookContent content = AdaptiveBookContent.ofElements(
                io.github.PhantomDaze.flexibook.content.TranslatableText.of("title"),
                List.of(
                        new BookElement.Paragraph(List.of(
                                InlineSpan.literal("AAAAAAAAAA"),
                                InlineSpan.literal(" BBBBBBBBBB")
                        )),
                        BookElement.Divider.INSTANCE,
                        new BookElement.Paragraph(List.of(
                                InlineSpan.literal("CCCCCCCCCC")
                        ))
                )
        );

        // Fake measurer: 6 units per character (monospace simulation)
        TextMeasurer meas = (text, style, fontId) -> (text == null ? 0 : text.length() * 6);
        TranslationProvider tx = (key, args) -> key;

        // Very narrow column so we force wrapping
        // Very narrow column so we force wrapping
        // Use a theme copy with tiny content width to guarantee multiple lines
        BookTheme narrow = BookTheme.Builder.from(THEME)
                .pageContentWidth(48)
                .build();

        List<RenderedPage> pages = BookLayoutEngine.layout(content, meas, tx, narrow, "en_us", 2, "");
        assertFalse(pages.isEmpty(), "should produce at least one page");

        // Sanity: first page has some text lines
        RenderedPage first = pages.get(0);
        long textLines = first.elements().stream().filter(e -> e instanceof RenderedElement.TextLine).count();
        assertTrue(textLines >= 2, "expect multiple lines due to narrow width");

        // Divider should appear somewhere
        boolean hasDivider = pages.stream()
                .flatMap(p -> p.elements().stream())
                .anyMatch(e -> e instanceof RenderedElement.DividerLine);
        assertTrue(hasDivider, "divider should be rendered");
    }

    @Test
    void emptyContentProducesEmptyPage() {
        AdaptiveBookContent empty = AdaptiveBookContent.EMPTY;
        TextMeasurer meas = (t, s, f) -> 0;
        TranslationProvider tx = (k, a) -> k;

        List<RenderedPage> pages = BookLayoutEngine.layout(empty, meas, tx, THEME, "en_us", 2, "");
        // Engine always produces at least one page container (even if it has zero elements)
        assertEquals(1, pages.size());
        // For truly empty content, the page has no elements (the "empty.body" fallback
        // only kicks in if tryLayout somehow returned zero pages, which it never does).
        assertTrue(pages.get(0).elements().isEmpty());
    }

    @Test
    void defaultFontResolvesToFlexiBookDefaultWhenAbsent() {
        AdaptiveBookContent content = AdaptiveBookContent.ofElements(
                io.github.PhantomDaze.flexibook.content.TranslatableText.of("title"),
                List.of(new BookElement.Paragraph(List.of(InlineSpan.literal("Hello"))))
        );
        // No defaultFont set
        assertTrue(content.defaultFont().isEmpty());

        TextMeasurer meas = (text, style, fontId) -> (text == null ? 0 : text.length() * 6);
        TranslationProvider tx = (k, a) -> k;

        List<RenderedPage> pages = BookLayoutEngine.layout(content, meas, tx, THEME, "en_us", 2, "");
        assertFalse(pages.isEmpty());
        RenderedPage page = pages.get(0);
        List<RenderedElement> lines = page.elements().stream()
                .filter(e -> e instanceof RenderedElement.TextLine)
                .toList();
        assertFalse(lines.isEmpty(), "should have text lines");

        Identifier expected = FlexiBookFonts.DEFAULT;
        for (RenderedElement el : lines) {
            if (el instanceof RenderedElement.TextLine tl) {
                assertEquals(Optional.of(expected), tl.style().font(),
                        "body text without explicit defaultFont must use flexibook:default");
            }
        }
    }

    @Test
    void explicitBookDefaultFontIsApplied() {
        Identifier custom = FlexiBookIds.of("mymod", "fancy");
        AdaptiveBookContent content = AdaptiveBookContent.ofElements(
                io.github.PhantomDaze.flexibook.content.TranslatableText.of("title"),
                List.of(new BookElement.Paragraph(List.of(InlineSpan.literal("Hello")))),
                Optional.of(custom)
        );

        TextMeasurer meas = (text, style, fontId) -> (text == null ? 0 : text.length() * 6);
        TranslationProvider tx = (k, a) -> k;

        List<RenderedPage> pages = BookLayoutEngine.layout(content, meas, tx, THEME, "en_us", 2, "");
        RenderedPage page = pages.get(0);
        RenderedElement.TextLine line = (RenderedElement.TextLine) page.elements().stream()
                .filter(e -> e instanceof RenderedElement.TextLine).findFirst().orElseThrow();
        assertEquals(Optional.of(custom), line.style().font());
    }

    @Test
    void spanLevelFontOverridesBookFont() {
        Identifier bookFont = FlexiBookFonts.DEFAULT;
        Identifier spanFont = FlexiBookIds.of("mymod", "fancy");
        AdaptiveBookContent content = AdaptiveBookContent.ofElements(
                io.github.PhantomDaze.flexibook.content.TranslatableText.of("title"),
                List.of(new BookElement.Paragraph(List.of(
                        InlineSpan.literal("book", StyleFlags.EMPTY),
                        InlineSpan.literal("span", StyleFlags.EMPTY.withFont(spanFont))
                ))),
                Optional.of(bookFont)
        );

        TextMeasurer meas = (text, style, fontId) -> (text == null ? 0 : text.length() * 6);
        TranslationProvider tx = (k, a) -> k;

        List<RenderedPage> pages = BookLayoutEngine.layout(content, meas, tx, THEME, "en_us", 2, "");
        RenderedPage page = pages.get(0);
        List<RenderedElement.TextLine> lines = page.elements().stream()
                .filter(e -> e instanceof RenderedElement.TextLine)
                .map(e -> (RenderedElement.TextLine) e)
                .toList();

        // Find the span one by content? Simplistic: at least one uses spanFont, book uses book default
        boolean hasBookDefault = lines.stream().anyMatch(l -> l.text().contains("book") && l.style().font().equals(Optional.of(bookFont)));
        boolean hasSpanOverride = lines.stream().anyMatch(l -> l.text().contains("span") && l.style().font().equals(Optional.of(spanFont)));
        assertTrue(hasBookDefault || lines.stream().anyMatch(l -> l.style().font().equals(Optional.of(bookFont))));
        assertTrue(hasSpanOverride || lines.stream().anyMatch(l -> l.style().font().equals(Optional.of(spanFont))));
    }
}
