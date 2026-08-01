package io.github.PhantomDaze.flexibook.layout;

import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * One drawable atom on a page, in page-local coordinates (before screen offset).
 */
public sealed interface RenderedElement permits
        RenderedElement.TextLine,
        RenderedElement.ImageBlock,
        RenderedElement.DividerLine {

    float x();

    float y();

    float scale();

    record TextLine(
            float x,
            float y,
            float scale,
            String text,
            StyleFlags style,
            Optional<LinkAction> link,
            float width,
            float height,
            boolean highlight
    ) implements RenderedElement {}

    record ImageBlock(
            float x,
            float y,
            float scale,
            Identifier texture,
            int width,
            int height,
            Optional<String> tooltipKey
    ) implements RenderedElement {}

    record DividerLine(
            float x,
            float y,
            float scale,
            float width,
            float height
    ) implements RenderedElement {}
}
