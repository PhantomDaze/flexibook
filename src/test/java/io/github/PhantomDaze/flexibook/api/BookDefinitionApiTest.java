package io.github.PhantomDaze.flexibook.api;

import io.github.PhantomDaze.flexibook.client.theme.BookContentRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookDefinition;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.TranslatableText;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Split book definition (index) + content body registry + override semantics.
 */
class BookDefinitionApiTest {

    private static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath("testmod", "guide");
    private static final ResourceLocation CONTENT_ID =
            ResourceLocation.fromNamespaceAndPath("testmod", "guide_body");

    @BeforeEach
    void seed() {
        BookContentRegistry.clear();
        BookDefinitionRegistry.clear();
        AdaptiveBookContent body = AdaptiveBookContent.ofElements(
                new TranslatableText("testmod.guide.title"),
                List.of(new BookElement.Paragraph(List.of(InlineSpan.literal("hi")))),
                Optional.empty(),
                Optional.of(ResourceLocation.fromNamespaceAndPath("testmod", "old_theme"))
        );
        BookContentRegistry.register(CONTENT_ID, body);
        BookDefinitionRegistry.register(
                BOOK_ID,
                BookDefinition.of(CONTENT_ID, ResourceLocation.fromNamespaceAndPath("testmod", "main"))
        );
    }

    @AfterEach
    void tearDown() {
        BookContentRegistry.clear();
        BookDefinitionRegistry.clear();
    }

    @Test
    void resolveBookMergesDefinitionTheme() {
        AdaptiveBookContent c = FlexiBookAPI.resolveBook(BOOK_ID);
        assertEquals("testmod.guide.title", c.title().key());
        assertTrue(c.themeId().isPresent());
        // definition theme wins over body theme
        assertEquals("testmod:main", c.themeId().get().toString());
    }

    @Test
    void resolveContentBodyOnly() {
        AdaptiveBookContent body = FlexiBookAPI.resolveBookContent(CONTENT_ID);
        assertEquals("testmod:old_theme", body.themeId().orElseThrow().toString());
    }

    @Test
    void unknownResolvesToEmpty() {
        AdaptiveBookContent c = FlexiBookAPI.resolveBook(
                ResourceLocation.fromNamespaceAndPath("nope", "missing"));
        assertTrue(c.isEmpty() || c == AdaptiveBookContent.EMPTY
                || c.title().key().contains("empty"));
    }

    @Test
    void bodyOnlyIdIsNotABook() {
        // Content body registered without a book index must not resolve via resolveBook
        ResourceLocation bodyOnly = ResourceLocation.fromNamespaceAndPath("testmod", "orphan_body");
        BookContentRegistry.register(
                bodyOnly,
                AdaptiveBookContent.ofElements(
                        new TranslatableText("orphan"),
                        List.of(new BookElement.Paragraph(List.of(InlineSpan.literal("x"))))
                )
        );
        AdaptiveBookContent c = FlexiBookAPI.resolveBook(bodyOnly);
        assertTrue(c.isEmpty() || c == AdaptiveBookContent.EMPTY || c.title().key().contains("empty"));
    }

    @Test
    void functionOverrideReturnsNewInstance() {
        AdaptiveBookContent base = FlexiBookAPI.resolveBook(BOOK_ID);
        ResourceLocation newTheme = ResourceLocation.fromNamespaceAndPath("testmod", "dark");
        AdaptiveBookContent tweaked = base.withThemeId(newTheme);
        assertNotSame(base, tweaked);
        assertEquals(Optional.of(newTheme), tweaked.themeId());
        assertEquals("testmod:main", base.themeId().orElseThrow().toString());
    }

    @Test
    void getBookDefinitionOptional() {
        assertTrue(FlexiBookAPI.getBookDefinition(BOOK_ID).isPresent());
        assertTrue(FlexiBookAPI.getBookContent(CONTENT_ID).isPresent());
        assertTrue(FlexiBookAPI.getBookDefinition(
                ResourceLocation.fromNamespaceAndPath("x", "y")).isEmpty());
    }
}
