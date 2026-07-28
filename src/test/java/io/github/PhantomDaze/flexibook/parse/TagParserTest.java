package io.github.PhantomDaze.flexibook.parse;

import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagParserTest {

    @Test
    void emptyAndBlankYieldEmptyList() {
        assertTrue(TagParser.parse(null).isEmpty());
        assertTrue(TagParser.parse("").isEmpty());
        assertTrue(TagParser.parse("   \n\t  ").isEmpty());
    }

    @Test
    void parsesHeadingsParagraphsAndBullets() {
        String markup = """
                [h1]flexibook.book.demo.h1[/h1]
                [h2]flexibook.book.demo.features[/h2]
                [p]flexibook.book.demo.intro[/p]
                [bullet]flexibook.book.demo.feature.adaptive[/bullet]
                """;
        List<BookElement> elements = TagParser.parse(markup);
        assertEquals(4, elements.size());
        assertInstanceOf(BookElement.Heading.class, elements.get(0));
        assertEquals(1, ((BookElement.Heading) elements.get(0)).level());
        assertEquals("flexibook.book.demo.h1", ((BookElement.Heading) elements.get(0)).text().key());

        assertInstanceOf(BookElement.Heading.class, elements.get(1));
        assertEquals(2, ((BookElement.Heading) elements.get(1)).level());

        assertInstanceOf(BookElement.Paragraph.class, elements.get(2));
        assertInstanceOf(BookElement.Bullet.class, elements.get(3));
    }

    @Test
    void parsesVoidTags() {
        List<BookElement> elements = TagParser.parse("[br][divider][img src=\"textures/gui/icon.png\" width=\"32\" height=\"32\" /]");
        assertEquals(3, elements.size());
        assertInstanceOf(BookElement.LineBreak.class, elements.get(0));
        assertInstanceOf(BookElement.Divider.class, elements.get(1));
        assertInstanceOf(BookElement.Image.class, elements.get(2));
        BookElement.Image img = (BookElement.Image) elements.get(2);
        assertEquals(32, img.width());
        assertEquals(32, img.height());
        assertEquals("flexibook", img.src().getNamespace());
        assertEquals("textures/gui/icon.png", img.src().getPath());
    }

    @Test
    void parsesInlineStylesAndNesting() {
        List<BookElement> elements = TagParser.parse(
                "[p]hello [b]bold[/b] [i]italic[/i] [u]under[/u] [color=#FF0000]red[/color] world[/p]"
        );
        assertEquals(1, elements.size());
        BookElement.Paragraph p = (BookElement.Paragraph) elements.get(0);
        List<InlineSpan> spans = p.spans();
        assertFalse(spans.isEmpty());

        boolean sawBold = spans.stream().anyMatch(s -> s.style().bold() && s.text().contains("bold"));
        boolean sawItalic = spans.stream().anyMatch(s -> s.style().italic() && s.text().contains("italic"));
        boolean sawUnder = spans.stream().anyMatch(s -> s.style().underline() && s.text().contains("under"));
        boolean sawColor = spans.stream().anyMatch(s ->
                s.style().color().isPresent() && s.style().color().get() == 0xFF0000 && s.text().contains("red"));
        assertTrue(sawBold, "expected bold span");
        assertTrue(sawItalic, "expected italic span");
        assertTrue(sawUnder, "expected underline span");
        assertTrue(sawColor, "expected colored span");
    }

    @Test
    void parsesCommandAndUrlLinks() {
        List<BookElement> cmd = TagParser.parse("[p][link cmd=\"flexibook:say_hi\"]click me[/link][/p]");
        BookElement.Paragraph p1 = (BookElement.Paragraph) cmd.get(0);
        assertTrue(p1.spans().stream().anyMatch(s ->
                s.link().isPresent() && s.link().get() instanceof LinkAction.CommandId c && c.id().equals("flexibook:say_hi")));

        List<BookElement> url = TagParser.parse("[p][link url=\"https://neoforged.net/\"]site[/link][/p]");
        BookElement.Paragraph p2 = (BookElement.Paragraph) url.get(0);
        assertTrue(p2.spans().stream().anyMatch(s ->
                s.link().isPresent() && s.link().get() instanceof LinkAction.Url u && u.url().startsWith("https://")));
    }

    @Test
    void escapesBrackets() {
        List<BookElement> elements = TagParser.parse("[p]use \\[b\\] for bold[/p]");
        BookElement.Paragraph p = (BookElement.Paragraph) elements.get(0);
        String joined = p.spans().stream().map(InlineSpan::text).reduce("", String::concat);
        assertTrue(joined.contains("[b]"), "escaped brackets should become literal: " + joined);
        assertFalse(joined.contains("\\["), joined);
    }

    @Test
    void parsesDivContainer() {
        List<BookElement> elements = TagParser.parse(
                "[div class=\"note\"][p]flexibook.book.demo.intro[/p][/div]"
        );
        assertEquals(1, elements.size());
        assertInstanceOf(BookElement.Box.class, elements.get(0));
        BookElement.Box box = (BookElement.Box) elements.get(0);
        assertEquals("note", box.className().orElse(null));
        assertEquals(1, box.children().size());
        assertInstanceOf(BookElement.Paragraph.class, box.children().get(0));
    }

    @Test
    void looseTextBecomesParagraph() {
        List<BookElement> elements = TagParser.parse("just some plain text");
        assertEquals(1, elements.size());
        assertInstanceOf(BookElement.Paragraph.class, elements.get(0));
    }

    @Test
    void parsesInlineFontTags() {
        List<BookElement> elements = TagParser.parse(
                "[p]plain [font font=\"minecraft:alt\"]fancy[/font] again [font=minecraft:uniform]mono[/font][/p]"
        );
        assertEquals(1, elements.size());
        BookElement.Paragraph p = (BookElement.Paragraph) elements.get(0);
        boolean sawAlt = p.spans().stream().anyMatch(s ->
                s.style().font().isPresent()
                        && s.style().font().get().toString().equals("minecraft:alt")
                        && s.text().contains("fancy"));
        boolean sawUniform = p.spans().stream().anyMatch(s ->
                s.style().font().isPresent()
                        && s.style().font().get().toString().equals("minecraft:uniform")
                        && s.text().contains("mono"));
        assertTrue(sawAlt, "expected minecraft:alt span: " + p.spans());
        assertTrue(sawUniform, "expected minecraft:uniform span: " + p.spans());
    }

    @Test
    void parsesHeadingFontAttribute() {
        List<BookElement> elements = TagParser.parse("[h1 font=\"minecraft:alt\"]demo.h1[/h1]");
        assertEquals(1, elements.size());
        BookElement.Heading h = (BookElement.Heading) elements.get(0);
        assertTrue(h.font().isPresent());
        assertEquals("minecraft:alt", h.font().get().toString());
    }

    @Test
    void unknownTagsDoNotThrow() {
        assertDoesNotThrow(() -> TagParser.parse("[unknown]stuff[/unknown][p]ok[/p]"));
        List<BookElement> elements = TagParser.parse("[p]still works[/p]");
        assertFalse(elements.isEmpty());
    }
}
