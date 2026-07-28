package io.github.PhantomDaze.flexibook.item;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.List;

public class FlexiBookItem extends Item {
    private static Method openBookMethod;

    public FlexiBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openClientScreen(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

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
        AdaptiveBookContent content = stack.get(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get());
        if (content != null) {
            return content.title().resolve();
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("flexibook.item.flexi_book.tooltip"));
        AdaptiveBookContent content = stack.get(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get());
        if (content == null || content.isEmpty()) {
            tooltipComponents.add(Component.translatable("flexibook.item.flexi_book.empty"));
        }
    }
}
