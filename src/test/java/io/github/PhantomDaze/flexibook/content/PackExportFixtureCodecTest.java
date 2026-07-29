package io.github.PhantomDaze.flexibook.content;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import io.github.PhantomDaze.flexibook.client.theme.ImageFit;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures JSON produced by the editor pack exporter (Phase D) parses with game codecs.
 * Fixtures written by {@code editor/scripts/test-pack-export.mjs} and
 * {@code editor/scripts/test-pack-demo-export.mjs}.
 */
class PackExportFixtureCodecTest {

    @Test
    void exportedThemeParses() throws Exception {
        var el = load("pack_export_fixture/theme.json");
        BookTheme theme = BookTheme.CODEC.parse(JsonOps.INSTANCE, el).getOrThrow();

        assertEquals(ResourceLocation.parse("myguide:textures/gui/book.png"), theme.bookTexture());
        assertEquals(ResourceLocation.parse("myguide:textures/gui/book_widgets.png"), theme.widgetsTexture());
        assertEquals(192, theme.bookTexWidth());
        assertEquals(216, theme.bookTexHeight());
        assertEquals(160, theme.pageContentWidth());
        assertEquals(ImageFit.STRETCH, theme.imageFit());
        assertEquals(1, theme.revision());
    }

    @Test
    void exportedBookParses() throws Exception {
        var el = load("pack_export_fixture/book.json");
        AdaptiveBookContent book = AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, el).getOrThrow();

        assertEquals("myguide.book.guide.title", book.title().key());
        assertEquals(OptionalRL("flexibook:default"), book.defaultFont());
        assertEquals(OptionalRL("myguide:main"), book.themeId());
        assertTrue(book.elements().isPresent());
        assertEquals(4, book.elements().get().size());
        assertInstanceOf(BookElement.Heading.class, book.elements().get().get(0));
        assertInstanceOf(BookElement.Paragraph.class, book.elements().get().get(1));
        assertInstanceOf(BookElement.Divider.class, book.elements().get().get(2));
        assertInstanceOf(BookElement.Image.class, book.elements().get().get(3));

        BookElement.Image img = (BookElement.Image) book.elements().get().get(3);
        assertEquals(ResourceLocation.parse("flexibook:textures/gui/icon.png"), img.src());
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
    void realDemoGuideExportParses() throws Exception {
        var el = load("pack_export_fixture/demo_guide_export.json");
        AdaptiveBookContent book = AdaptiveBookContent.CODEC.parse(JsonOps.INSTANCE, el).getOrThrow();
        assertFalse(book.isEmpty());
        assertEquals(OptionalRL("demopack:default"), book.themeId());
        assertEquals(OptionalRL("flexibook:default"), book.defaultFont());
        assertTrue(book.elements().isPresent());
        assertTrue(book.elements().get().size() >= 10, "demo guide has many elements");
    }

    @Test
    void realDefaultThemeExportParses() throws Exception {
        var el = load("pack_export_fixture/default_theme_export.json");
        BookTheme theme = BookTheme.CODEC.parse(JsonOps.INSTANCE, el).getOrThrow();
        assertEquals(ResourceLocation.parse("demopack:textures/gui/book.png"), theme.bookTexture());
        assertEquals(ResourceLocation.parse("demopack:textures/gui/book_widgets.png"), theme.widgetsTexture());
    }

    private static Optional<ResourceLocation> OptionalRL(String s) {
        return Optional.of(ResourceLocation.parse(s));
    }

    private static com.google.gson.JsonElement load(String classpath) throws Exception {
        try (var in = PackExportFixtureCodecTest.class.getClassLoader().getResourceAsStream(classpath)) {
            assertNotNull(in, "missing classpath resource: " + classpath);
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8));
        }
    }
}
