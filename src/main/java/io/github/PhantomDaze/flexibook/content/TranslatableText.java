package io.github.PhantomDaze.flexibook.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Stores a translation key plus optional string args. Resolved only on the client at layout time.
 */
public record TranslatableText(String key, List<String> args) {
    public static final Codec<TranslatableText> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("key").forGetter(TranslatableText::key),
            Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(TranslatableText::args)
    ).apply(instance, TranslatableText::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TranslatableText> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TranslatableText::key,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TranslatableText::args,
            TranslatableText::new
    );

    public TranslatableText(String key) {
        this(key, List.of());
    }

    public static TranslatableText of(String key, String... args) {
        return new TranslatableText(key, List.of(args));
    }

    public Component resolve() {
        // Literals (parser/builder fallthrough) are stored without looking like translation keys.
        if (!looksLikeKey(key)) {
            return Component.literal(key);
        }
        if (args.isEmpty()) {
            return Component.translatable(key);
        }
        return Component.translatable(key, args.toArray());
    }

    public String resolvePlain() {
        return resolve().getString();
    }

    private static boolean looksLikeKey(String value) {
        return value != null && value.indexOf('.') > 0 && value.indexOf(' ') < 0;
    }
}
