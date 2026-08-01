package io.github.PhantomDaze.flexibook.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;
import java.util.function.Supplier;

/**
 * Small polyfills so the same sources compile on Java 17 (MC 1.20.1) and Java 21 (MC 1.21.1).
 */
public final class Compat {
    private Compat() {}

    public static <T> T first(List<T> list) {
        return list.get(0);
    }

    public static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }

    public static boolean isError(DataResult<?> result) {
        return result.result().isEmpty();
    }

    public static <T> T getOrThrow(DataResult<T> result) {
        return result.result().orElseThrow(() ->
                new IllegalStateException(result.error().map(Object::toString).orElse("DataResult error")));
    }

    /** Lazy codec: {@code ExtraCodecs.lazyInitializedCodec} on 1.20.1, {@code Codec.lazyInitialized} on 1.21+. */
    public static <A> Codec<A> lazyCodec(Supplier<Codec<A>> supplier) {
        //? if >=1.21 {
        return Codec.lazyInitialized(supplier);
        //?} else {
        /*return net.minecraft.util.ExtraCodecs.lazyInitializedCodec(supplier);
        *///?}
    }
}
