package io.github.PhantomDaze.flexibook.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public sealed interface BookElement permits
        BookElement.Heading,
        BookElement.Paragraph,
        BookElement.LineBreak,
        BookElement.Divider,
        BookElement.Image,
        BookElement.Bullet,
        BookElement.Box {

    String typeId();

    Codec<BookElement> CODEC = Codec.STRING.dispatch(
            BookElement::typeId,
            type -> switch (type.toLowerCase(Locale.ROOT)) {
                case "heading" -> Heading.CODEC.fieldOf("data");
                case "paragraph" -> Paragraph.CODEC.fieldOf("data");
                case "br" -> LineBreak.CODEC.fieldOf("data");
                case "divider" -> Divider.CODEC.fieldOf("data");
                case "image" -> Image.CODEC.fieldOf("data");
                case "bullet" -> Bullet.CODEC.fieldOf("data");
                case "box" -> Box.CODEC.fieldOf("data");
                default -> Paragraph.CODEC.fieldOf("data");
            }
    );

    StreamCodec<RegistryFriendlyByteBuf, BookElement> NETWORK_CODEC = new StreamCodec<>() {
        @Override
        public BookElement decode(RegistryFriendlyByteBuf buf) {
            byte id = buf.readByte();
            return switch (id) {
                case 1 -> Heading.STREAM_CODEC.decode(buf);
                case 2 -> Paragraph.STREAM_CODEC.decode(buf);
                case 3 -> LineBreak.INSTANCE;
                case 4 -> Divider.INSTANCE;
                case 5 -> Image.STREAM_CODEC.decode(buf);
                case 6 -> Bullet.STREAM_CODEC.decode(buf);
                case 7 -> Box.STREAM_CODEC.decode(buf);
                default -> LineBreak.INSTANCE;
            };
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BookElement value) {
            switch (value) {
                case Heading heading -> {
                    buf.writeByte(1);
                    Heading.STREAM_CODEC.encode(buf, heading);
                }
                case Paragraph paragraph -> {
                    buf.writeByte(2);
                    Paragraph.STREAM_CODEC.encode(buf, paragraph);
                }
                case LineBreak ignored -> buf.writeByte(3);
                case Divider ignored -> buf.writeByte(4);
                case Image image -> {
                    buf.writeByte(5);
                    Image.STREAM_CODEC.encode(buf, image);
                }
                case Bullet bullet -> {
                    buf.writeByte(6);
                    Bullet.STREAM_CODEC.encode(buf, bullet);
                }
                case Box box -> {
                    buf.writeByte(7);
                    Box.STREAM_CODEC.encode(buf, box);
                }
            }
        }
    };

    record Heading(int level, TranslatableText text, Optional<ResourceLocation> font) implements BookElement {
        public Heading(int level, TranslatableText text) {
            this(level, text, Optional.empty());
        }

        public static final Codec<Heading> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("level", 1).forGetter(Heading::level),
                TranslatableText.CODEC.fieldOf("text").forGetter(Heading::text),
                ResourceLocation.CODEC.optionalFieldOf("font").forGetter(Heading::font)
        ).apply(i, Heading::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Heading> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Heading::level,
                TranslatableText.STREAM_CODEC, Heading::text,
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Heading::font,
                Heading::new
        );

        @Override
        public String typeId() {
            return "heading";
        }
    }

    record Paragraph(List<InlineSpan> spans) implements BookElement {
        public static final Codec<Paragraph> CODEC = RecordCodecBuilder.create(i -> i.group(
                InlineSpan.CODEC.listOf().fieldOf("spans").forGetter(Paragraph::spans)
        ).apply(i, Paragraph::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Paragraph> STREAM_CODEC = StreamCodec.composite(
                InlineSpan.STREAM_CODEC.apply(ByteBufCodecs.list()), Paragraph::spans,
                Paragraph::new
        );

        @Override
        public String typeId() {
            return "paragraph";
        }
    }

    record LineBreak() implements BookElement {
        public static final LineBreak INSTANCE = new LineBreak();
        public static final Codec<LineBreak> CODEC = Codec.unit(INSTANCE);

        @Override
        public String typeId() {
            return "br";
        }
    }

    record Divider() implements BookElement {
        public static final Divider INSTANCE = new Divider();
        public static final Codec<Divider> CODEC = Codec.unit(INSTANCE);

        @Override
        public String typeId() {
            return "divider";
        }
    }

    record Image(ResourceLocation src, int width, int height, Optional<String> tooltipKey) implements BookElement {
        public static final Codec<Image> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("src").forGetter(Image::src),
                Codec.INT.optionalFieldOf("width", 16).forGetter(Image::width),
                Codec.INT.optionalFieldOf("height", 16).forGetter(Image::height),
                Codec.STRING.optionalFieldOf("tooltip").forGetter(Image::tooltipKey)
        ).apply(i, Image::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Image> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Image::src,
                ByteBufCodecs.VAR_INT, Image::width,
                ByteBufCodecs.VAR_INT, Image::height,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), Image::tooltipKey,
                Image::new
        );

        @Override
        public String typeId() {
            return "image";
        }
    }

    record Bullet(List<InlineSpan> spans) implements BookElement {
        public static final Codec<Bullet> CODEC = RecordCodecBuilder.create(i -> i.group(
                InlineSpan.CODEC.listOf().fieldOf("spans").forGetter(Bullet::spans)
        ).apply(i, Bullet::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Bullet> STREAM_CODEC = StreamCodec.composite(
                InlineSpan.STREAM_CODEC.apply(ByteBufCodecs.list()), Bullet::spans,
                Bullet::new
        );

        @Override
        public String typeId() {
            return "bullet";
        }
    }

    record Box(Optional<String> className, List<BookElement> children) implements BookElement {
        public static final Codec<Box> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("class").forGetter(Box::className),
                BookElement.CODEC.listOf().fieldOf("children").forGetter(Box::children)
        ).apply(i, Box::new)));

        public static final StreamCodec<RegistryFriendlyByteBuf, Box> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Box decode(RegistryFriendlyByteBuf buf) {
                Optional<String> className = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf);
                List<BookElement> children = BookElement.NETWORK_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                return new Box(className, children);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Box value) {
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, value.className());
                BookElement.NETWORK_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.children());
            }
        };

        @Override
        public String typeId() {
            return "box";
        }
    }
}
