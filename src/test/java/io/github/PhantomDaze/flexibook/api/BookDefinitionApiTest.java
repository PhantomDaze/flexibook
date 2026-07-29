package io.github.PhantomDaze.flexibook.api;

import io.github.PhantomDaze.flexibook.client.theme.BookContentRegistry;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
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
 * Data-driven book definition registry + override semantics (no ItemStack / game bootstrap).
 */
class BookDefinitionApiTest {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("testmod", "guide");

    @BeforeEach
    void seed() {
        BookContentRegistry.clear();
        AdaptiveBookContent base = AdaptiveBookContent.ofElements(
                new TranslatableText("testmod.guide.title"),
                List.of(new BookElement.Paragraph(List.of(InlineSpan.literal("hi")))),
                Optional.empty(),
                Optional.of(ResourceLocation.fromNamespaceAndPath("testmod", "old_theme"))
        );
        BookContentRegistry.register(ID, base);
    }

    @AfterEach
    void tearDown() {
        BookContentRegistry.clear();
    }

    @Test
    void resolveRegisteredContent() {
        AdaptiveBookContent c = FlexiBookAPI.resolveBookContent(ID);
        assertEquals("testmod.guide.title", c.title().key());
        assertTrue(c.themeId().isPresent());
        assertEquals("testmod:old_theme", c.themeId().get().toString());
    }

    @Test
    void unknownResolvesToEmpty() {
        AdaptiveBookContent c = FlexiBookAPI.resolveBookContent(
                ResourceLocation.fromNamespaceAndPath("nope", "missing"));
        assertTrue(c.isEmpty() || c == AdaptiveBookContent.EMPTY
                || c.title().key().contains("empty"));
    }

    @Test
    void functionOverrideReturnsNewInstance() {
        AdaptiveBookContent base = FlexiBookAPI.resolveBookContent(ID);
        ResourceLocation newTheme = ResourceLocation.fromNamespaceAndPath("testmod", "main");
        AdaptiveBookContent tweaked = base.withThemeId(newTheme);
        assertNotSame(base, tweaked);
        assertEquals(Optional.of(newTheme), tweaked.themeId());
        // base unchanged
        assertEquals("testmod:old_theme", base.themeId().orElseThrow().toString());
    }

    @Test
    void getBookContentOptional() {
        assertTrue(FlexiBookAPI.getBookContent(ID).isPresent());
        assertTrue(FlexiBookAPI.getBookContent(
                ResourceLocation.fromNamespaceAndPath("x", "y")).isEmpty());
    }
}
