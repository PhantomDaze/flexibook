package io.github.PhantomDaze.flexibook.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;
import java.util.Optional;

/**
 * Safe link target. Arbitrary shell/command strings are never executed —
 * only registered command ids and http(s) URLs are allowed at click time.
 */
public sealed interface LinkAction permits LinkAction.None, LinkAction.CommandId, LinkAction.Url {
    Codec<LinkAction> CODEC = Codec.STRING.dispatch(
            "type",
            action -> switch (action) {
                case None ignored -> "none";
                case CommandId ignored -> "command_id";
                case Url ignored -> "url";
            },
            type -> switch (type.toLowerCase(Locale.ROOT)) {
                case "none" -> None.CODEC.fieldOf("value");
                case "command_id" -> CommandId.CODEC.fieldOf("value");
                case "url" -> Url.CODEC.fieldOf("value");
                default -> Codec.unit(None.INSTANCE).fieldOf("value");
            }
    );

    // Simpler map-style codec that is easier to hand-author
    Codec<LinkAction> SIMPLE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
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

    StreamCodec<RegistryFriendlyByteBuf, LinkAction> STREAM_CODEC = StreamCodec.of(
            (buf, action) -> {
                switch (action) {
                    case None ignored -> {
                        buf.writeByte(0);
                    }
                    case CommandId commandId -> {
                        buf.writeByte(1);
                        ByteBufCodecs.STRING_UTF8.encode(buf, commandId.id());
                    }
                    case Url url -> {
                        buf.writeByte(2);
                        ByteBufCodecs.STRING_UTF8.encode(buf, url.url());
                    }
                }
            },
            buf -> switch (buf.readByte()) {
                case 1 -> new CommandId(ByteBufCodecs.STRING_UTF8.decode(buf));
                case 2 -> new Url(ByteBufCodecs.STRING_UTF8.decode(buf));
                default -> None.INSTANCE;
            }
    );

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
        public static final Codec<None> CODEC = Codec.unit(INSTANCE);
    }

    record CommandId(String id) implements LinkAction {
        public static final Codec<CommandId> CODEC = Codec.STRING.xmap(CommandId::new, CommandId::id);
    }

    record Url(String url) implements LinkAction {
        public static final Codec<Url> CODEC = Codec.STRING.validate(s -> {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                return DataResult.success(s);
            }
            return DataResult.error(() -> "Only http(s) URLs are allowed: " + s);
        }).xmap(Url::new, Url::url);
    }
}
