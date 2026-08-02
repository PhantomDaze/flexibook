package io.github.PhantomDaze.flexibook.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
//? if >=1.21 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
*///?}

import java.util.Locale;
import java.util.Optional;

/**
 * Safe link target. Arbitrary shell/command strings are never executed —
 * only registered command ids and http(s) URLs are allowed at click time.
 */
public sealed interface LinkAction permits LinkAction.None, LinkAction.CommandId, LinkAction.Url {
    /**
     * Prefer {@link #SIMPLE_CODEC} for JSON authoring. Kept as an alias for older call sites.
     */
    Codec<LinkAction> CODEC = SIMPLE_CODEC_HOLDER.CODEC;

    // Simpler map-style codec that is easier to hand-author ({"cmd":...} / {"url":...})
    Codec<LinkAction> SIMPLE_CODEC = SIMPLE_CODEC_HOLDER.CODEC;

    //? if >=1.21 {
    /*StreamCodec<RegistryFriendlyByteBuf, LinkAction> STREAM_CODEC = StreamCodec.of(
            (buf, action) -> {
                if (action instanceof None) {
                    buf.writeByte(0);
                } else if (action instanceof CommandId commandId) {
                    buf.writeByte(1);
                    ByteBufCodecs.STRING_UTF8.encode(buf, commandId.id());
                } else if (action instanceof Url url) {
                    buf.writeByte(2);
                    ByteBufCodecs.STRING_UTF8.encode(buf, url.url());
                } else {
                    buf.writeByte(0);
                }
            },
            buf -> switch (buf.readByte()) {
                case 1 -> new CommandId(ByteBufCodecs.STRING_UTF8.decode(buf));
                case 2 -> new Url(ByteBufCodecs.STRING_UTF8.decode(buf));
                default -> None.INSTANCE;
            }
    );
    *///?}

    static LinkAction commandId(String id) {
        return new CommandId(id);
    }

    static LinkAction url(String url) {
        return new Url(url);
    }

    static LinkAction none() {
        return None.INSTANCE;
    }

    record None() implements LinkAction {
        public static final None INSTANCE = new None();
        public static final Codec<None> CODEC = MapCodec.unit(INSTANCE).codec();
    }

    record CommandId(String id) implements LinkAction {
        public static final Codec<CommandId> CODEC = Codec.STRING.xmap(CommandId::new, CommandId::id);
    }

    record Url(String url) implements LinkAction {
        public static final Codec<Url> CODEC = Codec.STRING.flatXmap(
                s -> {
                    String lower = s.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("http://") || lower.startsWith("https://")) {
                        return DataResult.success(new Url(s));
                    }
                    return DataResult.error(() -> "Only http(s) URLs are allowed: " + s);
                },
                u -> DataResult.success(u.url())
        );
    }

    /** Holds SIMPLE_CODEC so interface static init order is valid on all JVMs. */
    final class SIMPLE_CODEC_HOLDER {
        private SIMPLE_CODEC_HOLDER() {}

        static final Codec<LinkAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("cmd").forGetter(a -> a instanceof CommandId c ? Optional.of(c.id()) : Optional.empty()),
                Codec.STRING.optionalFieldOf("url").forGetter(a -> a instanceof Url u ? Optional.of(u.url()) : Optional.empty())
        ).apply(instance, (cmd, url) -> {
            if (cmd.isPresent()) {
                return new CommandId(cmd.get());
            }
            if (url.isPresent()) {
                return new Url(url.get());
            }
            return None.INSTANCE;
        }));
    }
}
