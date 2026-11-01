package me.catalysmrl.catamines.commands.mine;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.command.abstraction.mine.AbstractMineCommand;
import me.catalysmrl.catamines.command.utils.CommandContext;
import me.catalysmrl.catamines.command.utils.CommandException;
import me.catalysmrl.catamines.command.utils.MineTarget;
import me.catalysmrl.catamines.mine.components.manager.controller.CataMineController;
import me.catalysmrl.catamines.utils.helper.Predicates;
import me.catalysmrl.catamines.utils.message.Message;
import org.bukkit.command.CommandSender;

public class ResetModeCommand extends AbstractMineCommand {
    public ResetModeCommand() {
        super("resetmode", "catamines.resetmode", Predicates.inRange(1, 1), false);
    }

    @Override
    public void execute(CataMines plugin, CommandSender sender, CommandContext ctx, MineTarget target)
            throws CommandException {
        assertArgLength(ctx);

        CataMine mine = target.getMine();

        CataMineController.ResetMode resetMode;

        try {
            resetMode = CataMineController.ResetMode.valueOf(ctx.peek().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            Message.RESETMODE_INVALID.send(sender);
            return;
        }

        mine.getController().setResetMode(resetMode);

        requireSave();
    }

    @Override
    public Message getDescription() {
        return Message.RESETMODE_DESCRIPTION;
    }

    @Override
    public java.util.List<String> tabComplete(CataMines plugin, CommandSender sender, CommandContext ctx, MineTarget target) {
        return java.util.Arrays.stream(CataMineController.ResetMode.values()).map(Enum::name)
                .filter(mode -> mode.startsWith(ctx.peek() == null ? "" : ctx.peek().toUpperCase(java.util.Locale.ROOT))).toList();
    }

    @Override
    public Message getUsage() {
        return Message.RESETMODE_USAGE;
    }
}
