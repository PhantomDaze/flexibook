package io.github.PhantomDaze.flexibook.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Resource-pack book <em>index</em> entry (not the body).
 * <p>
 * Path: {@code assets/<ns>/flexibook/books/<path>.json} → id {@code ns:path}.
 * Points at a content body ({@code flexibook/contents/}) and a theme ({@code flexibook/themes/}).
 * Optional {@link #font()} overrides the content body's book-level font when creating an item.
 */
public record BookDefinition(
        ResourceLocation contentId,
        Optional<ResourceLocation> themeId,
        Optional<ResourceLocation> font
) {
    public static final Codec<BookDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("content").forGetter(BookDefinition::contentId),
            ResourceLocation.CODEC.optionalFieldOf("theme").forGetter(BookDefinition::themeId),
            ResourceLocation.CODEC.optionalFieldOf("font").forGetter(BookDefinition::font)
    ).apply(instance, BookDefinition::new));

    public static BookDefinition of(ResourceLocation contentId) {
        return new BookDefinition(contentId, Optional.empty(), Optional.empty());
    }

    public static BookDefinition of(ResourceLocation contentId, ResourceLocation themeId) {
        return new BookDefinition(contentId, Optional.ofNullable(themeId), Optional.empty());
    }

    public BookDefinition withTheme(ResourceLocation theme) {
        return new BookDefinition(contentId, Optional.ofNullable(theme), font);
    }

    public BookDefinition withFont(ResourceLocation fontId) {
        return new BookDefinition(contentId, themeId, Optional.ofNullable(fontId));
    }

    /**
     * Merges this index onto a loaded content body:
     * definition theme/font win when present; otherwise body fields stay.
     */
    public AdaptiveBookContent applyTo(AdaptiveBookContent body) {
        if (body == null) {
            return AdaptiveBookContent.EMPTY;
        }
        AdaptiveBookContent out = body;
        if (themeId.isPresent()) {
            out = out.withThemeId(themeId.get());
        }
        if (font.isPresent()) {
            out = out.withDefaultFont(font.get());
        }
        return out;
    }
}
