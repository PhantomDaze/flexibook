package io.github.PhantomDaze.flexibook.registry;

//? if neoforge {
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FlexiBookMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AdaptiveBookContent>> ADAPTIVE_BOOK_CONTENT =
            DATA_COMPONENTS.registerComponentType("adaptive_book_content", builder -> builder
                    .persistent(AdaptiveBookContent.CODEC)
                    .networkSynchronized(AdaptiveBookContent.STREAM_CODEC)
                    .cacheEncoding());

    // Resolved component type (loader-agnostic).
    public static DataComponentType<AdaptiveBookContent> adaptiveBookContent() {
        return ADAPTIVE_BOOK_CONTENT.get();
    }

    private ModDataComponents() {}
}
//?} else {
/*//? if fabric {
/^//? if >=1.21 {
import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModDataComponents {
    public static DataComponentType<AdaptiveBookContent> ADAPTIVE_BOOK_CONTENT;

    public static void register() {
        ADAPTIVE_BOOK_CONTENT = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                FlexiBookIds.of(FlexiBookMod.MOD_ID, "adaptive_book_content"),
                DataComponentType.<AdaptiveBookContent>builder()
                        .persistent(AdaptiveBookContent.CODEC)
                        .networkSynchronized(AdaptiveBookContent.STREAM_CODEC)
                        .cacheEncoding()
                        .build());
    }

    public static DataComponentType<AdaptiveBookContent> adaptiveBookContent() {
        return ADAPTIVE_BOOK_CONTENT;
    }

    private ModDataComponents() {}
}
//?} else {
/^¹// 1.20.1 Fabric: book payload lives in NBT (BookContentAccess).
public final class ModDataComponents {
    public static void register() {
    }

    private ModDataComponents() {}
}
¹^///?}
^///?} else {
// Forge 1.20.1 — no DataComponents.
public final class ModDataComponents {
    private ModDataComponents() {}
}
//?}
*///?}
