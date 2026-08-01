package io.github.PhantomDaze.flexibook.content;

import io.github.PhantomDaze.flexibook.util.FlexiBookIds;

import io.github.PhantomDaze.flexibook.util.Compat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import io.github.PhantomDaze.flexibook.client.theme.ImageFit;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures JSON produced by the editor pack exporter parses with game codecs.
 * Fixtures written by {@code editor/scripts/test-pack-export.mjs} and
 * {@code editor/scripts/test-pack-demo-export.mjs}.
 * <p>
 * Split layout: books/ = index ({@link BookDefinition}), contents/ = body ({@link AdaptiveBookContent}).
 */
class PackExportFixtureCodecTest {

    @Test
    void exportedThemeParses() throws Exception {
        var el = load("pack_export_fixture/theme.json");
        BookTheme theme =Compat.getOrThrow( BookTheme.CODEC.parse(JsonOps.INSTANCE, el));

        assertEquals(java.util.Objects.requireNonNull(FlexiBookIds.tryParse("myguide:textures/gui/book.png")), theme.bookTexture());
        assertEquals(192, theme.bookTexWidth());
        assertEquals(216, theme.bookTexHeight());
        assertEquals(160, theme.pageContentWidth());
        assertEquals(ImageFit.STRETCH, theme.imageFit());
        assertEquals(1, theme.revision());
    }

    @Test
    void exportedBookIndexParses() throws Exception {
        var el = load("pack_export_fixture/book.json");
        BookDefinition def =Compat.getOrThrow( BookDefinition.CODEC.parse(JsonOps.INSTANCE, el));

        assertEquals(java.util.Objects.requireNonNull(FlexiBookIds.tryParse("myguide:guide")), def.contentId());
        assertEquals(OptionalRL("myguide:main"), def.themeId());
        assertEquals(OptionalRL("myguide:title"), def.font());
    }

    @Test
    void exportedContentBodyParses() throws Exception {
        var el = load("pack_export_fixture/content.json");
        AdaptiveBookContent body =Compat.getOrThrow( AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, el));

        assertEquals("myguide.book.guide.title", body.title().key());
        assertTrue(body.elements().isPresent());
        assertTrue(body.elements().get().size() >= 4);
        assertInstanceOf(BookElement.Heading.class, body.elements().get().get(0));
        assertInstanceOf(BookElement.Paragraph.class, body.elements().get().get(1));
        assertInstanceOf(BookElement.Divider.class, body.elements().get().get(2));
        assertInstanceOf(BookElement.Image.class, body.elements().get().get(3));

        BookElement.Image img = (BookElement.Image) body.elements().get().get(3);
        assertEquals(java.util.Objects.requireNonNull(FlexiBookIds.tryParse("flexibook:textures/gui/icon.png")), img.src());
        assertEquals(32, img.width());
        assertEquals(32, img.height());
    }

    @Test
    void packMcmetaShape() throws Exception {
        var el = load("pack_export_fixture/pack.mcmeta").getAsJsonObject();
        assertTrue(el.has("pack"));
        var pack = el.getAsJsonObject("pack");
        assertEquals(34, pack.get("pack_format").getAsInt());
        assertTrue(pack.get("description").getAsString().contains("myguide"));
    }

    @Test
    void realDemoGuideIndexExportParses() throws Exception {
        var el = load("pack_export_fixture/demo_guide_export.json");
        BookDefinition def =Compat.getOrThrow( BookDefinition.CODEC.parse(JsonOps.INSTANCE, el));
        assertEquals(java.util.Objects.requireNonNull(FlexiBookIds.tryParse("demopack:demo_guide")), def.contentId());
        assertEquals(OptionalRL("demopack:default"), def.themeId());
    }

    @Test
    void realDemoGuideContentExportParses() throws Exception {
        var el = load("pack_export_fixture/demo_guide_content_export.json");
        AdaptiveBookContent body =Compat.getOrThrow( AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, el));
        assertFalse(body.isEmpty());
        assertEquals(OptionalRL("flexibook:default"), body.defaultFont());
        assertTrue(body.elements().isPresent());
        assertTrue(body.elements().get().size() >= 10, "demo guide has many elements");
    }

    @Test
    void realDefaultThemeExportParses() throws Exception {
        var el = load("pack_export_fixture/default_theme_export.json");
        BookTheme theme =Compat.getOrThrow( BookTheme.CODEC.parse(JsonOps.INSTANCE, el));
        assertEquals(java.util.Objects.requireNonNull(FlexiBookIds.tryParse("demopack:textures/gui/book.png")), theme.bookTexture());
    }

    private static Optional<Identifier> OptionalRL(String s) {
        return Optional.of(java.util.Objects.requireNonNull(FlexiBookIds.tryParse(s)));
    }

    private static com.google.gson.JsonElement load(String classpath) throws Exception {
        try (var in = PackExportFixtureCodecTest.class.getClassLoader().getResourceAsStream(classpath)) {
            assertNotNull(in, "missing classpath resource: " + classpath);
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8));
        }
    }
}
