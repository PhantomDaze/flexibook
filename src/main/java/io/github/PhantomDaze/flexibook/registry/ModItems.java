package io.github.PhantomDaze.flexibook.registry;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.item.FlexiBookItem;
import net.minecraft.world.item.Item;

//? if neoforge {
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
//?} else {
/*//? if forge {
/^import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
^///?} else {
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
//?}
*///?}

public final class ModItems {
    //? if neoforge {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FlexiBookMod.MOD_ID);

    // MC 26+ requires Item.Properties#setId before construction; registerItem does that.
    public static final DeferredItem<FlexiBookItem> FLEXI_BOOK = ITEMS.registerItem(
            "flexi_book",
            FlexiBookItem::new,
            props -> props.stacksTo(1));

    /** Resolved item instance (loader-agnostic). */
    public static FlexiBookItem book() {
        return FLEXI_BOOK.get();
    }
    //?} else {
    /*//? if forge {
    /^public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FlexiBookMod.MOD_ID);

    public static final RegistryObject<FlexiBookItem> FLEXI_BOOK = ITEMS.register("flexi_book",
            () -> new FlexiBookItem(new Item.Properties().stacksTo(1)));

    public static FlexiBookItem book() {
        return FLEXI_BOOK.get();
    }
    ^///?} else {
    public static FlexiBookItem FLEXI_BOOK;

    public static void register() {
        var id = FlexiBookIds.of(FlexiBookMod.MOD_ID, "flexi_book");
        var key = ResourceKey.create(Registries.ITEM, id);
        // MC 26+ requires Properties#setId before Item construction.
        FLEXI_BOOK = Registry.register(
                BuiltInRegistries.ITEM,
                key,
                new FlexiBookItem(new Item.Properties().stacksTo(1).setId(key)));
    }

    public static FlexiBookItem book() {
        return FLEXI_BOOK;
    }
    //?}
    *///?}

    private ModItems() {}
}
