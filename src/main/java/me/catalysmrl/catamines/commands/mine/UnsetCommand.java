package me.catalysmrl.catamines.commands.mine;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.Flag;
import me.catalysmrl.catamines.command.abstraction.mine.AbstractMineCommand;
import me.catalysmrl.catamines.command.utils.CommandContext;
import me.catalysmrl.catamines.command.utils.CommandException;
import me.catalysmrl.catamines.command.utils.MineTarget;
import me.catalysmrl.catamines.utils.helper.Predicates;
import me.catalysmrl.catamines.utils.message.Message;
import me.catalysmrl.catamines.utils.message.Messages;

import org.bukkit.command.CommandSender;

public class UnsetCommand extends AbstractMineCommand {
    public UnsetCommand() {
        super("unset", "catamines.unset", Predicates.equals(1), false);
    }

    @Override
    public void execute(CataMines plugin, CommandSender sender, CommandContext ctx, MineTarget target)
            throws CommandException {
        assertArgLength(ctx);
        String input = ctx.next();
        target.resolveDefaults();
        var composition = target.getComposition();
        if (composition == null) { Message.INVALID_TARGET.send(sender, target.toPath()); return; }
        try {
            var parsed = me.catalysmrl.catamines.utils.worldedit.BaseBlockParser.parse(input);
            var matched = composition.getBlocks().stream().filter(block -> block.getBaseBlock().equals(parsed)).findFirst();
            if (matched.isEmpty()) { Message.SET_INVALID_BLOCKSTATE.send(sender); return; }
            composition.removeBlock(matched.get());
        } catch (com.sk89q.worldedit.extension.input.InputParseException | IllegalArgumentException e) {
            Message.SET_INVALID_BLOCKSTATE.send(sender); return;
        }
        Message.UNSET_SUCCESS.send(sender, input, target.toPath());
        requireSave();
    }

    @Override
    public Message getDescription() {
        return Message.UNSET_DESCRIPTION;
    }

    @Override
    public Message getUsage() {
        return Message.UNSET_USAGE;
    }
}
