package io.github.PhantomDaze.flexibook.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.PhantomDaze.flexibook.layout.TranslationProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * One styled run of text. {@code text} is either a translation key (when {@code translate=true})
 * or a literal fragment produced by the tag parser / builder.
 */
public record InlineSpan(
        String text,
        boolean translate,
        StyleFlags style,
        Optional<LinkAction> link
) {
    public static final Codec<InlineSpan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("text").forGetter(InlineSpan::text),
            Codec.BOOL.optionalFieldOf("translate", true).forGetter(InlineSpan::translate),
            StyleFlags.CODEC.optionalFieldOf("style", StyleFlags.EMPTY).forGetter(InlineSpan::style),
            LinkAction.SIMPLE_CODEC.optionalFieldOf("link").forGetter(InlineSpan::link)
    ).apply(instance, InlineSpan::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, InlineSpan> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InlineSpan::text,
            ByteBufCodecs.BOOL, InlineSpan::translate,
            StyleFlags.STREAM_CODEC, InlineSpan::style,
            ByteBufCodecs.optional(LinkAction.STREAM_CODEC), InlineSpan::link,
            InlineSpan::new
    );

    public static InlineSpan key(String translationKey) {
        return new InlineSpan(translationKey, true, StyleFlags.EMPTY, Optional.empty());
    }

    public static InlineSpan key(String translationKey, StyleFlags style) {
        return new InlineSpan(translationKey, true, style, Optional.empty());
    }

    public static InlineSpan key(String translationKey, StyleFlags style, LinkAction link) {
        return new InlineSpan(translationKey, true, style, Optional.ofNullable(link));
    }

    public static InlineSpan literal(String text) {
        return new InlineSpan(text, false, StyleFlags.EMPTY, Optional.empty());
    }

    public static InlineSpan literal(String text, StyleFlags style) {
        return new InlineSpan(text, false, style, Optional.empty());
    }

    public static InlineSpan literal(String text, StyleFlags style, LinkAction link) {
        return new InlineSpan(text, false, style, Optional.ofNullable(link));
    }

    public String resolvePlain() {
        if (translate) {
            return net.minecraft.network.chat.Component.translatable(text).getString();
        }
        return text;
    }

    /**
     * Resolve using a provider for standalone/editor usage.
     * When translate=true and the text looks like a key, use provider; otherwise treat as literal.
     */
    public String resolvePlain(TranslationProvider provider) {
        if (!translate) {
            return text;
        }
        if (provider == null) {
            return net.minecraft.network.chat.Component.translatable(text).getString();
        }
        // Reuse TranslatableText's heuristic for key detection
        if (io.github.PhantomDaze.flexibook.content.TranslatableText.class.getName() != null) {
            // simple heuristic: keys contain '.' and no spaces
            if (text != null && text.indexOf('.') > 0 && text.indexOf(' ') < 0) {
                return provider.get(text);
            }
            return text;
        }
        return text;
    }
}
