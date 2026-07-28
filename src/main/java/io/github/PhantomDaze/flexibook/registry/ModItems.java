package io.github.PhantomDaze.flexibook.registry;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.item.FlexiBookItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FlexiBookMod.MOD_ID);

    public static final DeferredItem<FlexiBookItem> FLEXI_BOOK = ITEMS.register("flexi_book",
            () -> new FlexiBookItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
