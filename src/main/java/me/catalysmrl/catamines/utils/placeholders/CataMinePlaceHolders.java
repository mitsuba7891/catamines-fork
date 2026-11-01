package me.catalysmrl.catamines.utils.placeholders;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.utils.message.Message;
import me.catalysmrl.catamines.utils.message.Messages;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.regex.Pattern;

public class CataMinePlaceHolders extends PlaceholderExpansion {

    private final CataMines plugin;

    public CataMinePlaceHolders(CataMines plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "catamines";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getPluginMeta().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        // Split once: underscores are valid in mine IDs.
        String[] args = identifier.split("_", 2);

        if (args.length <= 1) {
            return switch (args[0]) {
                case "prefix", "p" -> Messages.PREFIX;
                default -> null;
            };
        }

        Optional<CataMine> mineOptional = plugin.getMineManager().getMine(args[1]);
        if (mineOptional.isEmpty()) {
            return null;
        }

        CataMine mine = mineOptional.get();

        var controller = mine.getController();
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "countdown", "time", "timestring" -> me.catalysmrl.catamines.utils.helper.CountdownFormatter.clock(controller.getCountdown());
            case "remainingseconds" -> Integer.toString(controller.getCountdown());
            case "totalblocks" -> Long.toString(controller.getTotalBlocks());
            case "remainingblocks" -> Long.toString(controller.getBlockCount());
            case "minedblocks" -> Long.toString(Math.max(0, controller.getTotalBlocks() - controller.getBlockCount()));
            case "remainingblockspercentage", "remainingblocksper" -> String.format(java.util.Locale.ROOT, "%.2f", controller.getRemainingPercentage());
            case "resetpercentage" -> Double.toString(controller.getResetPercentage());
            default -> null;
        };
    }
}
