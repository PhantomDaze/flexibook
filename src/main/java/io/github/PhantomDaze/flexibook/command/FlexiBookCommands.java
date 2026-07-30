package io.github.PhantomDaze.flexibook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /flexibook give <bookId> [player]} — give a data-driven book from the registry.
 */
public final class FlexiBookCommands {
    private static final SimpleCommandExceptionType UNKNOWN_BOOK =
            new SimpleCommandExceptionType(Component.literal("Unknown FlexiBook id (not in books/ registry)"));

    private FlexiBookCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("flexibook")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .executes(ctx -> give(
                                                ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "book"),
                                                ctx.getSource().getPlayerOrException()
                                        ))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> give(
                                                        ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "book"),
                                                        EntityArgument.getPlayer(ctx, "player")
                                                ))
                                        )
                                )
                                .then(Commands.argument("bookStr", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String raw = StringArgumentType.getString(ctx, "bookStr");
                                            ResourceLocation id = ResourceLocation.tryParse(raw);
                                            if (id == null) {
                                                throw new SimpleCommandExceptionType(
                                                        Component.literal("Invalid id: " + raw)
                                                ).create();
                                            }
                                            return give(ctx.getSource(), id, ctx.getSource().getPlayerOrException());
                                        })
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    var ids = BookDefinitionRegistry.ids();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("FlexiBook definitions (" + ids.size() + "): " + ids),
                                            false
                                    );
                                    return ids.size();
                                })
                        )
        );
    }

    private static int give(CommandSourceStack source, ResourceLocation bookId, ServerPlayer player)
            throws CommandSyntaxException {
        if (!BookDefinitionRegistry.isRegistered(bookId)) {
            throw UNKNOWN_BOOK.create();
        }
        ItemStack stack = FlexiBookAPI.createBookFromDefinition(bookId);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(
                () -> Component.literal("Gave " + bookId + " to " + player.getGameProfile().getName()),
                true
        );
        return 1;
    }
}
