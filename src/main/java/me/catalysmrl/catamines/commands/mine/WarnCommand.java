package me.catalysmrl.catamines.commands.mine;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.command.abstraction.mine.AbstractMineCommand;
import me.catalysmrl.catamines.command.utils.CommandContext;
import me.catalysmrl.catamines.command.utils.CommandException;
import me.catalysmrl.catamines.command.utils.MineTarget;
import me.catalysmrl.catamines.utils.helper.Predicates;
import me.catalysmrl.catamines.utils.message.Message;
import org.bukkit.command.CommandSender;

public class WarnCommand extends AbstractMineCommand {

    public WarnCommand() {
        super("warn", "catamines.warn", Predicates.equals(2), false);
    }

    @Override
    public void execute(CataMines plugin, CommandSender sender, CommandContext ctx, MineTarget target)
            throws CommandException {
        assertArgLength(ctx);
        String option = ctx.next().toLowerCase(java.util.Locale.ROOT);
        String raw = ctx.next();
        var holder = (me.catalysmrl.catamines.api.mine.PropertyHolder) target.getTarget();
        try {
            switch (option) {
                case "seconds", "distance" -> {
                    int value = Integer.parseInt(raw);
                    if (value < 0) throw new IllegalArgumentException("Negative warning value");
                    holder.setFlag(option.equals("seconds") ? me.catalysmrl.catamines.api.mine.Flag.WARN_SECONDS
                            : me.catalysmrl.catamines.api.mine.Flag.WARN_DISTANCE, value);
                }
                case "enabled", "global", "hotbar" -> {
                    if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) throw new IllegalArgumentException("Invalid boolean");
                    holder.setFlag(switch (option) {
                        case "global" -> me.catalysmrl.catamines.api.mine.Flag.WARN_GLOBAL;
                        case "hotbar" -> me.catalysmrl.catamines.api.mine.Flag.WARN_HOTBAR;
                        default -> me.catalysmrl.catamines.api.mine.Flag.WARN;
                    }, Boolean.parseBoolean(raw));
                }
                default -> throw new IllegalArgumentException("Unknown warning option");
            }
        } catch (IllegalArgumentException e) { getUsage().send(sender); return; }
        Message.WARN_SUCCESS.send(sender);
        requireSave();
    }

    @Override
    public Message getDescription() {
        return Message.WARN_DESCRIPTION;
    }

    @Override
    public Message getUsage() {
        return Message.WARN_USAGE;
    }
}
