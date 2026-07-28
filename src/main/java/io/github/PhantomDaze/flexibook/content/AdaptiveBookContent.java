package io.github.PhantomDaze.flexibook.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.PhantomDaze.flexibook.parse.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Dual-form book payload: either a tag markup string or a structured element list.
 * Structured elements win when both are present.
 * <p>
 * Optional {@link #defaultFont()} is the book-wide font id (resource-pack definition under
 * {@code assets/<ns>/font/<path>.json}). Per-span fonts on {@link StyleFlags} override it.
 */
public record AdaptiveBookContent(
        TranslatableText title,
        Optional<String> rawMarkup,
        Optional<List<BookElement>> elements,
        Optional<ResourceLocation> defaultFont
) {
    public static final AdaptiveBookContent EMPTY = new AdaptiveBookContent(
            new TranslatableText("flexibook.book.empty.title"),
            Optional.empty(),
            Optional.of(List.of()),
            Optional.empty()
    );

    public static final Codec<AdaptiveBookContent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TranslatableText.CODEC.fieldOf("title").forGetter(AdaptiveBookContent::title),
            Codec.STRING.optionalFieldOf("raw").forGetter(AdaptiveBookContent::rawMarkup),
            BookElement.CODEC.listOf().optionalFieldOf("elements").forGetter(AdaptiveBookContent::elements),
            ResourceLocation.CODEC.optionalFieldOf("font").forGetter(AdaptiveBookContent::defaultFont)
    ).apply(instance, AdaptiveBookContent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdaptiveBookContent> STREAM_CODEC = StreamCodec.composite(
            TranslatableText.STREAM_CODEC, AdaptiveBookContent::title,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), AdaptiveBookContent::rawMarkup,
            ByteBufCodecs.optional(BookElement.NETWORK_CODEC.apply(ByteBufCodecs.list())), AdaptiveBookContent::elements,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), AdaptiveBookContent::defaultFont,
            AdaptiveBookContent::new
    );

    public static AdaptiveBookContent ofElements(TranslatableText title, List<BookElement> elements) {
        return ofElements(title, elements, Optional.empty());
    }

    public static AdaptiveBookContent ofElements(TranslatableText title, List<BookElement> elements, Optional<ResourceLocation> defaultFont) {
        return new AdaptiveBookContent(title, Optional.empty(), Optional.of(List.copyOf(elements)), defaultFont);
    }

    public static AdaptiveBookContent ofMarkup(TranslatableText title, String markup) {
        return ofMarkup(title, markup, Optional.empty());
    }

    public static AdaptiveBookContent ofMarkup(TranslatableText title, String markup, Optional<ResourceLocation> defaultFont) {
        return new AdaptiveBookContent(title, Optional.of(markup), Optional.empty(), defaultFont);
    }

    public AdaptiveBookContent withDefaultFont(ResourceLocation font) {
        return new AdaptiveBookContent(title, rawMarkup, elements, Optional.ofNullable(font));
    }

    public List<BookElement> resolveElements() {
        if (elements.isPresent()) {
            return elements.get();
        }
        if (rawMarkup.isPresent()) {
            return TagParser.parse(rawMarkup.get());
        }
        return List.of();
    }

    public boolean isEmpty() {
        return resolveElements().isEmpty()
                && (rawMarkup.isEmpty() || rawMarkup.get().isBlank());
    }
}
