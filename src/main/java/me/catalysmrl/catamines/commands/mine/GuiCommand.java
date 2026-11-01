package me.catalysmrl.catamines.commands.mine;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.command.abstraction.AbstractCommand;
import me.catalysmrl.catamines.command.utils.CommandContext;
import me.catalysmrl.catamines.command.utils.CommandException;
import me.catalysmrl.catamines.utils.helper.Predicates;
import me.catalysmrl.catamines.utils.message.Messages;
import me.catalysmrl.catamines.utils.message.Message;
import org.bukkit.command.CommandSender;

public class GuiCommand extends AbstractCommand {

    public GuiCommand() {
        super("gui", "catamines.gui", Predicates.inRange(0, 1), true);
    }

    @Override
    public void execute(CataMines plugin, CommandSender sender, CommandContext ctx) throws CommandException {
        assertArgLength(ctx);
        var player = (org.bukkit.entity.Player) sender;
        if (!ctx.hasNext()) {
            new me.catalysmrl.catamines.gui.generic.MineListGui(plugin, player, 0).open();
            return;
        }
        var mine = plugin.getMineManager().getMine(ctx.next());
        if (mine.isEmpty()) { Message.MINE_NOT_EXISTS.send(sender); return; }
        new me.catalysmrl.catamines.gui.generic.MineEditorGui(plugin, player, mine.get()).open();
    }

    @Override
    public Message getDescription() {
        return Message.GUI_DESCRIPTION;
    }

    @Override
    public Message getUsage() {
        return Message.GUI_USAGE;
    }
}
