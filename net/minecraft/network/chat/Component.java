package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.Message;
import com.mojang.serialization.JsonOps;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.contents.DataSource;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;

public interface Component extends Message, FormattedText {
    Style getStyle();

    ComponentContents getContents();

    @Override
    default String getString() {
        return FormattedText.super.getString();
    }

    /**
     * Get the plain text of this FormattedText, without any styling or formatting codes, limited to {@code maxLength} characters.
     */
    default String getString(int maxLength) {
        StringBuilder stringbuilder = new StringBuilder();
        this.visit(p_130673_ -> {
            int i = maxLength - stringbuilder.length();
            if (i <= 0) {
                return STOP_ITERATION;
            } else {
                stringbuilder.append(p_130673_.length() <= i ? p_130673_ : p_130673_.substring(0, i));
                return Optional.empty();
            }
        });
        return stringbuilder.toString();
    }

    List<Component> getSiblings();

    @Nullable
    default String tryCollapseToString() {
        if (this.getContents() instanceof PlainTextContents plaintextcontents && this.getSiblings().isEmpty() && this.getStyle().isEmpty()) {
            return plaintextcontents.text();
        }

        return null;
    }

    default MutableComponent plainCopy() {
        return MutableComponent.create(this.getContents());
    }

    default MutableComponent copy() {
        return new MutableComponent(this.getContents(), new ArrayList<>(this.getSiblings()), this.getStyle());
    }

    FormattedCharSequence getVisualOrderText();

    @Override
    default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> acceptor, Style p_style) {
        Style style = this.getStyle().applyTo(p_style);
        Optional<T> optional = this.getContents().visit(acceptor, style);
        if (optional.isPresent()) {
            return optional;
        } else {
            for (Component component : this.getSiblings()) {
                Optional<T> optional1 = component.visit(acceptor, style);
                if (optional1.isPresent()) {
                    return optional1;
                }
            }

            return Optional.empty();
        }
    }

    @Override
    default <T> Optional<T> visit(FormattedText.ContentConsumer<T> acceptor) {
        Optional<T> optional = this.getContents().visit(acceptor);
        if (optional.isPresent()) {
            return optional;
        } else {
            for (Component component : this.getSiblings()) {
                Optional<T> optional1 = component.visit(acceptor);
                if (optional1.isPresent()) {
                    return optional1;
                }
            }

            return Optional.empty();
        }
    }

    default List<Component> toFlatList() {
        return this.toFlatList(Style.EMPTY);
    }

    default List<Component> toFlatList(Style style) {
        List<Component> list = Lists.newArrayList();
        this.visit((p_178403_, p_178404_) -> {
            if (!p_178404_.isEmpty()) {
                list.add(literal(p_178404_).withStyle(p_178403_));
            }

            return Optional.empty();
        }, style);
        return list;
    }

    default boolean contains(Component other) {
        if (this.equals(other)) {
            return true;
        } else {
            List<Component> list = this.toFlatList();
            List<Component> list1 = other.toFlatList(this.getStyle());
            return Collections.indexOfSubList(list, list1) != -1;
        }
    }

    static Component nullToEmpty(@Nullable String text) {
        return (Component)(text != null ? literal(text) : CommonComponents.EMPTY);
    }

    static MutableComponent literal(String text) {
        return MutableComponent.create(PlainTextContents.create(text));
    }

    static MutableComponent translatable(String key) {
        return MutableComponent.create(new TranslatableContents(key, null, TranslatableContents.NO_ARGS));
    }

    static MutableComponent translatable(String key, Object... args) {
        return MutableComponent.create(new TranslatableContents(key, null, args));
    }

    static MutableComponent translatableEscape(String key, Object... args) {
        for (int i = 0; i < args.length; i++) {
            Object object = args[i];
            if (!TranslatableContents.isAllowedPrimitiveArgument(object) && !(object instanceof Component)) {
                args[i] = String.valueOf(object);
            }
        }

        return translatable(key, args);
    }

    static MutableComponent translatableWithFallback(String key, @Nullable String fallback) {
        return MutableComponent.create(new TranslatableContents(key, fallback, TranslatableContents.NO_ARGS));
    }

    static MutableComponent translatableWithFallback(String key, @Nullable String fallback, Object... args) {
        return MutableComponent.create(new TranslatableContents(key, fallback, args));
    }

    static MutableComponent empty() {
        return MutableComponent.create(PlainTextContents.EMPTY);
    }

    static MutableComponent keybind(String name) {
        return MutableComponent.create(new KeybindContents(name));
    }

    static MutableComponent nbt(String nbtPathPattern, boolean interpreting, Optional<Component> separator, DataSource dataSource) {
        return MutableComponent.create(new NbtContents(nbtPathPattern, interpreting, separator, dataSource));
    }

    static MutableComponent score(String name, String objective) {
        return MutableComponent.create(new ScoreContents(name, objective));
    }

    static MutableComponent selector(String pattern, Optional<Component> separator) {
        return MutableComponent.create(new SelectorContents(pattern, separator));
    }

    static Component translationArg(Date date) {
        return literal(date.toString());
    }

    static Component translationArg(Message message) {
        return (Component)(message instanceof Component component ? component : literal(message.getString()));
    }

    static Component translationArg(UUID uuid) {
        return literal(uuid.toString());
    }

    static Component translationArg(ResourceLocation location) {
        return literal(location.toString());
    }

    static Component translationArg(ChunkPos chunkPos) {
        return literal(chunkPos.toString());
    }

    static Component translationArg(URI uri) {
        return literal(uri.toString());
    }

    public static class Serializer {
        private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

        private Serializer() {
        }

        static MutableComponent deserialize(JsonElement json, HolderLookup.Provider provider) {
            return (MutableComponent)ComponentSerialization.CODEC
                .parse(provider.createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow(JsonParseException::new);
        }

        static JsonElement serialize(Component component, HolderLookup.Provider provider) {
            return ComponentSerialization.CODEC
                .encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), component)
                .getOrThrow(JsonParseException::new);
        }

        public static String toJson(Component component, HolderLookup.Provider registries) {
            return GSON.toJson(serialize(component, registries));
        }

        @Nullable
        public static MutableComponent fromJson(String json, HolderLookup.Provider registries) {
            JsonElement jsonelement = JsonParser.parseString(json);
            return jsonelement == null ? null : deserialize(jsonelement, registries);
        }

        @Nullable
        public static MutableComponent fromJson(@Nullable JsonElement json, HolderLookup.Provider registries) {
            return json == null ? null : deserialize(json, registries);
        }

        @Nullable
        public static MutableComponent fromJsonLenient(String json, HolderLookup.Provider registries) {
            JsonReader jsonreader = new JsonReader(new StringReader(json));
            jsonreader.setLenient(true);
            JsonElement jsonelement = JsonParser.parseReader(jsonreader);
            return jsonelement == null ? null : deserialize(jsonelement, registries);
        }
    }

    public static class SerializerAdapter implements JsonDeserializer<MutableComponent>, JsonSerializer<Component> {
        private final HolderLookup.Provider registries;

        public SerializerAdapter(HolderLookup.Provider registries) {
            this.registries = registries;
        }

        public MutableComponent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Component.Serializer.deserialize(json, this.registries);
        }

        public JsonElement serialize(Component src, Type typeOfSrc, JsonSerializationContext context) {
            return Component.Serializer.serialize(src, this.registries);
        }
    }
}
