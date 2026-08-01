package io.github.PhantomDaze.flexibook.item;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookContentAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.4 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.List;

//? if <1.21 {
/*import org.jetbrains.annotations.Nullable;
*///?}

public class FlexiBookItem extends Item {
    private static Method openBookMethod;

    public FlexiBookItem(Properties properties) {
        super(properties);
    }

    //? if >=1.21.4 {
    /*@Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        //? if >=1.21.11 {
        /^if (level.isClientSide()) {
        ^///?} else {
        if (level.isClientSide) {
        //?}
            openClientScreen(stack);
        }
        return InteractionResult.SUCCESS;
    }
    *///?} else {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openClientScreen(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    //?}

    /**
     * Reflective open keeps dedicated-server classpath free of Screen types.
     * {@code ClientModEvents} is only resolved on the client when the book is used.
     */
    private static void openClientScreen(ItemStack stack) {
        try {
            if (openBookMethod == null) {
                Class<?> cls = Class.forName("io.github.PhantomDaze.flexibook.client.ClientModEvents");
                openBookMethod = cls.getMethod("openBook", ItemStack.class);
            }
            openBookMethod.invoke(null, stack);
        } catch (ReflectiveOperationException e) {
            FlexiBookMod.LOGGER.error("Failed to open FlexiBook screen", e);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        AdaptiveBookContent content = BookContentAccess.get(stack);
        if (content != null) {
            return content.title().resolve();
        }
        return super.getName(stack);
    }

    //? if >=1.21.11 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        appendHover(stack, tooltipComponents);
    }

    private static void appendHover(ItemStack stack, java.util.function.Consumer<Component> tooltipComponents) {
        tooltipComponents.accept(Component.translatable("flexibook.item.flexi_book.tooltip"));
        AdaptiveBookContent content = BookContentAccess.get(stack);
        if (content == null || content.isEmpty()) {
            tooltipComponents.accept(Component.translatable("flexibook.item.flexi_book.empty"));
        }
    }
    *///?} else {
    //? if >=1.21 {
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        appendHover(stack, tooltipComponents);
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        appendHover(stack, tooltipComponents);
    }
    *///?}

    private static void appendHover(ItemStack stack, List<Component> tooltipComponents) {
        tooltipComponents.add(Component.translatable("flexibook.item.flexi_book.tooltip"));
        AdaptiveBookContent content = BookContentAccess.get(stack);
        if (content == null || content.isEmpty()) {
            tooltipComponents.add(Component.translatable("flexibook.item.flexi_book.empty"));
        }
    }
    //?}
}
