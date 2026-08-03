package io.github.PhantomDaze.flexibook.data;

import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.theme.BookContentRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeRegistry;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures jar-bundled pack samples are available for server-side {@code /flexibook give}
 * without relying on client resource reload.
 */
class ClasspathPackBootstrapTest {

    @BeforeEach
    void clear() {
        BookContentRegistry.clear();
        BookDefinitionRegistry.clear();
        // themes keep builtins via bootstrap; drop extras if any
        BookThemeRegistry.bootstrap();
    }

    @AfterEach
    void tearDown() {
        BookContentRegistry.clear();
        BookDefinitionRegistry.clear();
        BookThemeRegistry.bootstrap();
    }

    @Test
    void bundledSamplesRegisterDemoGuideAndJournal() {
        ClasspathPackBootstrap.loadBundledSamples();

        ResourceLocation demo = FlexiBookIds.of("flexibook", "demo_guide");
        ResourceLocation journal = FlexiBookIds.of("fieldnotes", "journal");

        assertTrue(BookDefinitionRegistry.isRegistered(demo), "demo_guide must be registered from classpath");
        assertTrue(BookDefinitionRegistry.isRegistered(journal), "fieldnotes:journal must be registered from classpath");

        AdaptiveBookContent demoContent = FlexiBookAPI.resolveBook(demo);
        assertFalse(demoContent.isEmpty(), "demo_guide body must resolve from classpath content");

        AdaptiveBookContent journalContent = FlexiBookAPI.resolveBook(journal);
        assertFalse(journalContent.isEmpty(), "journal body must resolve from classpath content");
    }
}
