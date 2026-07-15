package net.lanzr.burnjosspaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.lanzr.burnjosspaper.data.WishSavedData;
import net.lanzr.burnjosspaper.menu.PaginationContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class BegCommand {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("beg")
                        .executes(ctx -> openBegMenu(ctx,1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> openBegMenu(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                        )
        );
    }
    private static int openBegMenu(CommandContext<CommandSourceStack> ctx, int pageIndex) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        WishSavedData data = WishSavedData.get();
        if (data == null) {
            source.sendFailure(Component.literal("Wish inventory not initialized."));
            return 0;
        }

        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new PaginationContainer(id, inv, data, pageIndex, true),
                Component.literal("§a阴间供品")
        ));
        return Command.SINGLE_SUCCESS;
    }
}
