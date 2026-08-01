package io.github.PhantomDaze.flexibook.content;

import io.github.PhantomDaze.flexibook.util.FlexiBookIds;

import io.github.PhantomDaze.flexibook.util.Compat;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContentModelTest {

    @Test
    void dualFormPrefersStructuredElements() {
        AdaptiveBookContent both = new AdaptiveBookContent(
                new TranslatableText("title.key"),
                Optional.of("[p]raw.should.be.ignored[/p]"),
                Optional.of(List.of(BookElement.Divider.INSTANCE)),
                Optional.empty(),
                Optional.empty()
        );
        List<BookElement> resolved = both.resolveElements();
        assertEquals(1, resolved.size());
        assertInstanceOf(BookElement.Divider.class, resolved.get(0));
    }

    @Test
    void dualFormFallsBackToMarkupParse() {
        AdaptiveBookContent raw = AdaptiveBookContent.ofMarkup(
                new TranslatableText("title.key"),
                "[h1]a.b.c[/h1][br]"
        );
        List<BookElement> resolved = raw.resolveElements();
        assertEquals(2, resolved.size());
        assertInstanceOf(BookElement.Heading.class, resolved.get(0));
        assertInstanceOf(BookElement.LineBreak.class, resolved.get(1));
    }

    @Test
    void emptyContentDetectsEmpty() {
        assertTrue(AdaptiveBookContent.EMPTY.isEmpty());
        assertFalse(AdaptiveBookContent.ofMarkup(new TranslatableText("t"), "[p]x[/p]").isEmpty());
    }

    @Test
    void contentCodecRoundTripElements() {
        AdaptiveBookContent original = AdaptiveBookContent.ofElements(
                TranslatableText.of("demo.title", "arg0"),
                List.of(
                        new BookElement.Heading(1, new TranslatableText("demo.h1")),
                        new BookElement.Paragraph(List.of(
                                InlineSpan.key("demo.p", StyleFlags.EMPTY.withBold(true)),
                                InlineSpan.literal(" literal ", StyleFlags.EMPTY.withColor(0x112233))
                        )),
                        BookElement.Divider.INSTANCE,
                        new BookElement.Bullet(List.of(InlineSpan.key("demo.b1")))
                )
        );

        var encoded = AdaptiveBookContent.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(!Compat.isError(encoded), () -> "encode failed: " + encoded.error());

        var decoded = AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, Compat.getOrThrow(encoded));
        assertTrue(!Compat.isError(decoded), () -> "decode failed: " + decoded.error());
        assertEquals(original, Compat.getOrThrow(decoded));
    }

    @Test
    void contentCodecRoundTripMarkup() {
        AdaptiveBookContent original = AdaptiveBookContent.ofMarkup(
                new TranslatableText("demo.title"),
                "[p]hello[/p]"
        );
        var encoded =Compat.getOrThrow( AdaptiveBookContent.CODEC.encodeStart(JsonOps.INSTANCE, original));
        AdaptiveBookContent back =Compat.getOrThrow( AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, encoded));
        assertEquals(original, back);
        assertTrue(back.rawMarkup().isPresent());
        assertTrue(back.elements().isEmpty());
    }

    @Test
    void contentCodecRoundTripDefaultFont() {
        Identifier font = FlexiBookIds.of("minecraft", "alt");
        AdaptiveBookContent original = AdaptiveBookContent.ofElements(
                new TranslatableText("demo.title"),
                List.of(new BookElement.Paragraph(List.of(
                        InlineSpan.key("demo.p", StyleFlags.EMPTY.withFont(font))
                ))),
                Optional.of(font)
        );
        var encoded =Compat.getOrThrow( AdaptiveBookContent.CODEC.encodeStart(JsonOps.INSTANCE, original));
        AdaptiveBookContent back =Compat.getOrThrow( AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, encoded));
        assertEquals(original, back);
        assertEquals(Optional.of(font), back.defaultFont());
        BookElement.Paragraph p = (BookElement.Paragraph) back.resolveElements().get(0);
        assertEquals(Optional.of(font), p.spans().get(0).style().font());
    }

    @Test
    void contentCodecRoundTripThemeId() {
        Identifier theme = FlexiBookIds.of("flexibook", "contain");
        AdaptiveBookContent original = AdaptiveBookContent.ofMarkup(
                new TranslatableText("demo.title"),
                "[p]x[/p]",
                Optional.empty(),
                Optional.of(theme)
        );
        var encoded =Compat.getOrThrow( AdaptiveBookContent.CODEC.encodeStart(JsonOps.INSTANCE, original));
        AdaptiveBookContent back =Compat.getOrThrow( AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, encoded));
        assertEquals(original, back);
        assertEquals(Optional.of(theme), back.themeId());
        assertEquals(Optional.of(theme), original.withThemeId(theme).themeId());
    }

    @Test
    void styleFlagsMerge() {
        StyleFlags a = StyleFlags.EMPTY.withBold(true).withColor(0xFF0000);
        StyleFlags b = StyleFlags.EMPTY.withItalic(true).withUnderline(true);
        StyleFlags m = a.merge(b);
        assertTrue(m.bold());
        assertTrue(m.italic());
        assertTrue(m.underline());
        assertEquals(Optional.of(0xFF0000), m.color());

        StyleFlags c = b.merge(a.withColor(0x00FF00));
        assertEquals(Optional.of(0x00FF00), c.color());
    }

    @Test
    void styleFlagsFontMergePrefersOther() {
        Identifier aFont = FlexiBookIds.of("minecraft", "default");
        Identifier bFont = FlexiBookIds.of("minecraft", "alt");
        StyleFlags a = StyleFlags.EMPTY.withFont(aFont);
        StyleFlags b = StyleFlags.EMPTY.withFont(bFont);
        assertEquals(Optional.of(bFont), a.merge(b).font());
        assertEquals(Optional.of(aFont), StyleFlags.EMPTY.merge(a).font());
        assertTrue(a.withFont(null).font().isEmpty());
    }

    @Test
    void linkActionSimpleCodec() {
        LinkAction cmd = LinkAction.commandId("flexibook:say_hi");
        var json =Compat.getOrThrow( LinkAction.SIMPLE_CODEC.encodeStart(JsonOps.INSTANCE, cmd));
        LinkAction back =Compat.getOrThrow( LinkAction.SIMPLE_CODEC.parse(JsonOps.INSTANCE, json));
        assertEquals(cmd, back);

        LinkAction url = LinkAction.url("https://example.com/path");
        json =Compat.getOrThrow( LinkAction.SIMPLE_CODEC.encodeStart(JsonOps.INSTANCE, url));
        back =Compat.getOrThrow( LinkAction.SIMPLE_CODEC.parse(JsonOps.INSTANCE, json));
        assertEquals(url, back);
    }

    @Test
    void linkActionUrlRejectsNonHttp() {
        var bad = LinkAction.Url.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createString("javascript:alert(1)"));
        assertTrue(Compat.isError(bad));
    }

    @Test
    void linkActionRegistryDispatch() {
        AtomicReference<String> seen = new AtomicReference<>();
        LinkActionRegistry.register("test:ping", ctx -> ctx.message("test.pong"));
        assertTrue(LinkActionRegistry.isRegistered("test:ping"));
        assertFalse(LinkActionRegistry.isRegistered("test:missing"));

        LinkActionRegistry.get("test:ping").accept((key, args) -> seen.set(key));
        assertEquals("test.pong", seen.get());
    }

    @Test
    void translatableTextLooksLikeKey() {
        assertEquals("a.b", new TranslatableText("a.b").key());
        TranslatableText literal = new TranslatableText("hello world");
        assertDoesNotThrow(() -> literal.resolvePlain());
        assertEquals("hello world", literal.resolvePlain());
    }
}
