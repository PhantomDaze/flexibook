package io.github.PhantomDaze.flexibook.registry;

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

    private ModDataComponents() {}
}
