package io.github.PhantomDaze.flexibook.client.theme;

/**
 * Pure geometry for {@link ImageFit#CONTAIN} (no Minecraft client types — unit-testable).
 */
public final class ImageFitMath {
    private ImageFitMath() {
    }

    /**
     * @param boxW      declared layout width (screen px of the slot)
     * @param boxH      declared layout height
     * @param texW      native texture pixel width (&gt; 0)
     * @param texH      native texture pixel height (&gt; 0)
     * @return left/top offset inside the box and draw size
     */
    public static Fit contain(int boxW, int boxH, int texW, int texH) {
        if (boxW <= 0 || boxH <= 0 || texW <= 0 || texH <= 0) {
            return new Fit(0, 0, Math.max(0, boxW), Math.max(0, boxH));
        }
        float scale = Math.min(boxW / (float) texW, boxH / (float) texH);
        int drawW = Math.max(1, Math.round(texW * scale));
        int drawH = Math.max(1, Math.round(texH * scale));
        // Clamp if rounding overflowed the box
        drawW = Math.min(drawW, boxW);
        drawH = Math.min(drawH, boxH);
        int ox = (boxW - drawW) / 2;
        int oy = (boxH - drawH) / 2;
        return new Fit(ox, oy, drawW, drawH);
    }

    public record Fit(int offsetX, int offsetY, int drawW, int drawH) {
    }
}