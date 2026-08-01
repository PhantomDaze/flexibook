package io.github.PhantomDaze.flexibook.client.theme;

import io.github.PhantomDaze.flexibook.util.FlexiBookIds;

import io.github.PhantomDaze.flexibook.util.Compat;

import com.mojang.serialization.JsonOps;
import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookThemeRegistryTest {

    @BeforeEach
    void seedBuiltins() {
        BookThemeRegistry.bootstrap();
    }

    @Test
    void builtinsAreRegistered() {
        assertTrue(BookThemeRegistry.isRegistered(BookThemes.DEFAULT_ID));
        assertTrue(BookThemeRegistry.isRegistered(BookThemes.CONTAIN_ID));
        assertEquals(ImageFit.STRETCH, BookThemeRegistry.resolve(BookThemes.DEFAULT_ID).imageFit());
        assertEquals(ImageFit.CONTAIN, BookThemeRegistry.resolve(BookThemes.CONTAIN_ID).imageFit());
    }

    @Test
    void unknownFallsBackToDefault() {
        BookTheme t = BookThemeRegistry.resolve(FlexiBookIds.of("nope", "missing"));
        assertEquals(BookThemes.DEFAULT.imageFit(), t.imageFit());
        assertEquals(BookThemes.DEFAULT.pageContentWidth(), t.pageContentWidth());
    }

    @Test
    void registerAndResolveCustom() {
        ResourceLocation id = FlexiBookIds.of("testmod", "wide");
        BookTheme custom = BookTheme.builder()
                .pageContentWidth(200)
                .imageFit(ImageFit.CONTAIN)
                .revision(99)
                .build();
        BookThemeRegistry.register(id, custom);
        assertTrue(BookThemeRegistry.isRegistered(id));
        assertEquals(200, BookThemeRegistry.resolve(id).pageContentWidth());
        assertEquals(ImageFit.CONTAIN, BookThemeRegistry.resolve(id).imageFit());
        assertTrue(BookThemeRegistry.unregister(id));
        assertFalse(BookThemeRegistry.isRegistered(id));
    }

    @Test
    void cannotUnregisterBuiltinDefault() {
        assertFalse(BookThemeRegistry.unregister(BookThemes.DEFAULT_ID));
        assertTrue(BookThemeRegistry.isRegistered(BookThemes.DEFAULT_ID));
    }

    @Test
    void codecRoundTrip() {
        BookTheme original = BookThemes.DEFAULT.withImageFit(ImageFit.CONTAIN);
        var encoded = BookTheme.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(!Compat.isError(encoded), () -> String.valueOf(encoded.error()));
        BookTheme back =Compat.getOrThrow( BookTheme.CODEC.parse(JsonOps.INSTANCE, Compat.getOrThrow(encoded)));
        assertEquals(original, back);
        assertEquals(ImageFit.CONTAIN, back.imageFit());
    }

    @Test
    void apiFacades() {
        assertEquals(BookThemes.DEFAULT_ID, FlexiBookAPI.defaultThemeId());
        assertEquals(BookThemes.CONTAIN_ID, FlexiBookAPI.containThemeId());
        assertEquals(BookThemes.DEFAULT, FlexiBookAPI.resolveTheme(BookThemes.DEFAULT_ID));
        ResourceLocation id = FlexiBookIds.of("testmod", "api_theme");
        BookTheme t = BookTheme.builder().lineHeight(12).build();
        FlexiBookAPI.registerTheme(id, t);
        assertTrue(FlexiBookAPI.getTheme(id).isPresent());
        assertEquals(12, FlexiBookAPI.resolveTheme(id).lineHeight());
        BookThemeRegistry.unregister(id);
    }
}
