package io.github.PhantomDaze.flexibook.registry;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.data.ExampleBooks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FlexiBookMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLEXIBOOK_TAB =
            CREATIVE_MODE_TABS.register("flexibook", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.flexibook"))
                    .icon(() -> new ItemStack(ModItems.FLEXI_BOOK.get()))
                    .displayItems((params, output) -> {
                        output.accept(new ItemStack(ModItems.FLEXI_BOOK.get()));
                        output.accept(ExampleBooks.demoGuide());
                    })
                    .build());

    private ModCreativeTabs() {}
}
