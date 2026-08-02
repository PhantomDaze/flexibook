package io.github.PhantomDaze.flexibook.registry;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.data.ExampleBooks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

//? if neoforge {
/*import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?} else {
//? if forge {
/*import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
*///?} else {
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
//?}
//?}

public final class ModCreativeTabs {
    //? if neoforge {
    /*public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FlexiBookMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLEXIBOOK_TAB =
            CREATIVE_MODE_TABS.register("flexibook", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.flexibook"))
                    .icon(() -> new ItemStack(ModItems.book()))
                    .displayItems((params, output) -> {
                        output.accept(new ItemStack(ModItems.book()));
                        output.accept(ExampleBooks.demoGuide());
                    })
                    .build());
    *///?} else {
    //? if forge {
    /*public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FlexiBookMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> FLEXIBOOK_TAB =
            CREATIVE_MODE_TABS.register("flexibook", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.flexibook"))
                    .icon(() -> new ItemStack(ModItems.book()))
                    .displayItems((params, output) -> {
                        output.accept(new ItemStack(ModItems.book()));
                        output.accept(ExampleBooks.demoGuide());
                    })
                    .build());
    *///?} else {
    public static CreativeModeTab FLEXIBOOK_TAB;

    public static void register() {
        FLEXIBOOK_TAB = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                FlexiBookIds.of(FlexiBookMod.MOD_ID, "flexibook"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.flexibook"))
                        .icon(() -> new ItemStack(ModItems.book()))
                        .displayItems((params, output) -> {
                            output.accept(new ItemStack(ModItems.book()));
                            output.accept(ExampleBooks.demoGuide());
                        })
                        .build());
    }
    //?}
    //?}

    private ModCreativeTabs() {}
}
