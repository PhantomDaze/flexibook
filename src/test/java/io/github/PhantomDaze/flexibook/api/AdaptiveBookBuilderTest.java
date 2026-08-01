package io.github.PhantomDaze.flexibook.api;

import io.github.PhantomDaze.flexibook.util.FlexiBookIds;

import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveBookBuilderTest {

    @Test
    void buildsStructuredContent() {
        AdaptiveBookContent content = new AdaptiveBookBuilder("guide")
                .titleKey("demo.title")
                .h1("demo.h1")
                .h2("demo.h2")
                .p("demo.p")
                .bullet("demo.b")
                .divider()
                .br()
                .link("demo.link", LinkAction.commandId("flexibook:say_hi"))
                .buildContent();

        assertEquals("demo.title", content.title().key());
        assertTrue(content.elements().isPresent());
        assertTrue(content.rawMarkup().isEmpty());

        var elements = content.resolveElements();
        assertTrue(elements.size() >= 6);
        assertInstanceOf(BookElement.Heading.class, elements.get(0));
        assertEquals(1, ((BookElement.Heading) elements.get(0)).level());
        assertInstanceOf(BookElement.Heading.class, elements.get(1));
        assertEquals(2, ((BookElement.Heading) elements.get(1)).level());
        assertTrue(elements.stream().anyMatch(e -> e instanceof BookElement.Divider));
        assertTrue(elements.stream().anyMatch(e -> e instanceof BookElement.LineBreak));
        assertTrue(elements.stream().anyMatch(e -> e instanceof BookElement.Paragraph p
                && p.spans().stream().anyMatch(s -> s.link().isPresent())));
    }

    @Test
    void fromMarkupOnlyUsesRawWhenNoElements() {
        AdaptiveBookContent content = new AdaptiveBookBuilder("m")
                .titleKey("t")
                .fromMarkup("[p]only.raw[/p]")
                .buildContent();
        assertTrue(content.rawMarkup().isPresent());
        assertTrue(content.elements().isEmpty());
        assertEquals(1, content.resolveElements().size());
    }

    @Test
    void pRawAppendsParsedElements() {
        AdaptiveBookContent content = new AdaptiveBookBuilder("m")
                .titleKey("t")
                .p("structured")
                .pRaw("[bullet]from.markup[/bullet]")
                .buildContent();
        assertTrue(content.elements().isPresent());
        assertEquals(2, content.resolveElements().size());
        assertInstanceOf(BookElement.Bullet.class, content.resolveElements().get(1));
    }

    @Test
    void defaultFontAndPerSpanFont() {
        var font = FlexiBookIds.of("minecraft", "alt");
        AdaptiveBookContent content = new AdaptiveBookBuilder("guide")
                .titleKey("demo.title")
                .defaultFont(font)
                .p("demo.p")
                .font("demo.fancy", font)
                .buildContent();
        assertEquals(Optional.of(font), content.defaultFont());
        assertTrue(content.resolveElements().stream().anyMatch(e ->
                e instanceof BookElement.Paragraph p
                        && p.spans().stream().anyMatch(s -> s.style().font().equals(Optional.of(font)))));
    }

    @Test
    void themeIdOnContent() {
        var theme = FlexiBookIds.of("flexibook", "contain");
        AdaptiveBookContent content = new AdaptiveBookBuilder("guide")
                .titleKey("demo.title")
                .theme(theme)
                .p("demo.p")
                .buildContent();
        assertEquals(Optional.of(theme), content.themeId());
        assertEquals(Optional.of(theme), new AdaptiveBookBuilder("g").theme("flexibook:contain").buildContent().themeId());
    }

    @Test
    void apiFacadesCreateLinkActions() {
        assertInstanceOf(LinkAction.CommandId.class, FlexiBookAPI.commandAction("a:b"));
        assertInstanceOf(LinkAction.Url.class, FlexiBookAPI.urlAction("https://example.com"));
        assertDoesNotThrow(FlexiBookAPI::registerDefaultActions);
        assertTrue(io.github.PhantomDaze.flexibook.content.LinkActionRegistry.isRegistered("flexibook:say_hi"));
    }
}
