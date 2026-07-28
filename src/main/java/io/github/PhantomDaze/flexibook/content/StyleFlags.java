package io.github.PhantomDaze.flexibook.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record StyleFlags(
        boolean bold,
        boolean italic,
        boolean underline,
        Optional<Integer> color,
        Optional<ResourceLocation> font
) {
    public static final StyleFlags EMPTY = new StyleFlags(false, false, false, Optional.empty(), Optional.empty());

    public static final Codec<StyleFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("bold", false).forGetter(StyleFlags::bold),
            Codec.BOOL.optionalFieldOf("italic", false).forGetter(StyleFlags::italic),
            Codec.BOOL.optionalFieldOf("underline", false).forGetter(StyleFlags::underline),
            Codec.INT.optionalFieldOf("color").forGetter(StyleFlags::color),
            ResourceLocation.CODEC.optionalFieldOf("font").forGetter(StyleFlags::font)
    ).apply(instance, StyleFlags::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StyleFlags> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, StyleFlags::bold,
            ByteBufCodecs.BOOL, StyleFlags::italic,
            ByteBufCodecs.BOOL, StyleFlags::underline,
            ByteBufCodecs.optional(ByteBufCodecs.INT), StyleFlags::color,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), StyleFlags::font,
            StyleFlags::new
    );

    public StyleFlags withBold(boolean value) {
        return new StyleFlags(value, italic, underline, color, font);
    }

    public StyleFlags withItalic(boolean value) {
        return new StyleFlags(bold, value, underline, color, font);
    }

    public StyleFlags withUnderline(boolean value) {
        return new StyleFlags(bold, italic, value, color, font);
    }

    public StyleFlags withColor(Integer value) {
        return new StyleFlags(bold, italic, underline, Optional.ofNullable(value), font);
    }

    public StyleFlags withFont(ResourceLocation value) {
        return new StyleFlags(bold, italic, underline, color, Optional.ofNullable(value));
    }

    public StyleFlags merge(StyleFlags other) {
        return new StyleFlags(
                this.bold || other.bold,
                this.italic || other.italic,
                this.underline || other.underline,
                other.color.isPresent() ? other.color : this.color,
                other.font.isPresent() ? other.font : this.font
        );
    }
}
