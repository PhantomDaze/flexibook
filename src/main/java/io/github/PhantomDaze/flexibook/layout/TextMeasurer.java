package io.github.PhantomDaze.flexibook.layout;

import io.github.PhantomDaze.flexibook.content.StyleFlags;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Measures the advance width (in unscaled font units) of a run of text
 * under given style flags and optional per-run font.
 * <p>
 * This is the ONLY thing the layout engine needs from a font system.
 * Implementations:
 * - MC side: wraps net.minecraft.client.gui.Font
 * - Editor side: uses java.awt.Font + FontMetrics, or a baked width table
 */
public interface TextMeasurer {
    /**
     * @param text    raw text to measure (already resolved/plain)
     * @param style   style flags (bold/italic/underline/font override)
     * @param fontId  explicit font id for this run (from StyleFlags or Heading), or empty to use current context default
     * @return width in the font's native units (what MC's Font.width returns for the equivalent Style)
     */
    int width(String text, StyleFlags style, Optional<Identifier> fontId);
}
