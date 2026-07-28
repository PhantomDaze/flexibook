package io.github.PhantomDaze.flexibook.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFitMathTest {
    @Test
    void containCentersWideTextureInTallBox() {
        // 64×32 into 48×48 → scale 48/64=0.75 → 48×24, y offset 12
        ImageFitMath.Fit f = ImageFitMath.contain(48, 48, 64, 32);
        assertEquals(48, f.drawW());
        assertEquals(24, f.drawH());
        assertEquals(0, f.offsetX());
        assertEquals(12, f.offsetY());
    }

    @Test
    void containCentersTallTextureInWideBox() {
        // 32×64 into 48×48 → scale 48/64 → 24×48, x offset 12
        ImageFitMath.Fit f = ImageFitMath.contain(48, 48, 32, 64);
        assertEquals(24, f.drawW());
        assertEquals(48, f.drawH());
        assertEquals(12, f.offsetX());
        assertEquals(0, f.offsetY());
    }

    @Test
    void containSameAspectFillsBox() {
        ImageFitMath.Fit f = ImageFitMath.contain(48, 48, 96, 96);
        assertEquals(48, f.drawW());
        assertEquals(48, f.drawH());
        assertEquals(0, f.offsetX());
        assertEquals(0, f.offsetY());
    }

    @Test
    void themeWithImageFitSwitchesMode() {
        assertEquals(ImageFit.STRETCH, BookThemes.DEFAULT.imageFit());
        BookTheme contain = BookThemes.DEFAULT.withImageFit(ImageFit.CONTAIN);
        assertEquals(ImageFit.CONTAIN, contain.imageFit());
        assertTrue(contain.revision() > BookThemes.DEFAULT.revision());
    }
}
