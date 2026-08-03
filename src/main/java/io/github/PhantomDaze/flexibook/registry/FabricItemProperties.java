package io.github.PhantomDaze.flexibook.registry;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Version-local Fabric item properties; 1.21.11 requires an id before Item construction. */
public final class FabricItemProperties {
    private FabricItemProperties() {}

    public static Item.Properties create() {
        //? if >=1.21.4 {
        return new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM,
                        FlexiBookIds.of(FlexiBookMod.MOD_ID, "flexi_book")))
                .stacksTo(1);
        //?} else {
        return new Item.Properties().stacksTo(1);
        //?}
    }
}
