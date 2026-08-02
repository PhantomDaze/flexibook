package io.github.PhantomDaze.flexibook.content;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Version-agnostic read/write of {@link AdaptiveBookContent} on an {@link ItemStack}.
 * <ul>
 *   <li>1.21+ NeoForge / Fabric: DataComponent {@code flexibook:adaptive_book_content}</li>
 *   <li>1.20.1 Forge / Fabric: NBT key {@link #NBT_KEY} via {@link AdaptiveBookContent#CODEC} + NbtOps</li>
 * </ul>
 */
public final class BookContentAccess {
    /** CompoundTag key used on 1.20.1 (no DataComponents). */
    public static final String NBT_KEY = "flexibook:content";

    private BookContentAccess() {}

    public static @Nullable AdaptiveBookContent get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        //? if >=1.21 {
        /*return stack.get(io.github.PhantomDaze.flexibook.registry.ModDataComponents.adaptiveBookContent());
        *///?} else {
        return readNbt(stack);
        //?}
    }

    public static AdaptiveBookContent getOrEmpty(ItemStack stack) {
        AdaptiveBookContent c = get(stack);
        return c != null ? c : AdaptiveBookContent.EMPTY;
    }

    public static void set(ItemStack stack, AdaptiveBookContent content) {
        if (stack == null) {
            return;
        }
        //? if >=1.21 {
        /*stack.set(io.github.PhantomDaze.flexibook.registry.ModDataComponents.adaptiveBookContent(), content);
        *///?} else {
        writeNbt(stack, content);
        //?}
    }

    //? if <1.21 {
    private static @Nullable AdaptiveBookContent readNbt(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY)) {
            return null;
        }
        net.minecraft.nbt.Tag raw = tag.get(NBT_KEY);
        return AdaptiveBookContent.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, raw)
                .result()
                .orElse(null);
    }

    private static void writeNbt(ItemStack stack, AdaptiveBookContent content) {
        if (content == null || content.isEmpty()) {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(NBT_KEY);
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
            return;
        }
        AdaptiveBookContent.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, content)
                .result()
                .ifPresent(encoded -> stack.getOrCreateTag().put(NBT_KEY, encoded));
    }
    //?}
}
