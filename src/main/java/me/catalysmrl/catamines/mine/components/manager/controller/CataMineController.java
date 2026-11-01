package me.catalysmrl.catamines.mine.components.manager.controller;

import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.api.mine.Flag;
import me.catalysmrl.catamines.api.serialization.SectionSerializable;
import me.catalysmrl.catamines.utils.helper.CountdownFormatter;
import me.catalysmrl.catamines.utils.message.MessageService;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Objects;

public class CataMineController implements SectionSerializable {
    private final CataMine mine;
    private ResetMode resetMode = ResetMode.TIME;
    private int resetDelay = 300;
    private double resetPercentage = 30;
    private int countdown = 300;
    private long blockCount;
    private long totalBlocks;
    private boolean resetting;
    private int retryDelay;

    public CataMineController(CataMine mine) { this.mine = Objects.requireNonNull(mine); }

    public void tick() {
        if (resetting || Boolean.TRUE.equals(mine.getFlagRecursive(Flag.STOPPED))) return;
        if (retryDelay > 0) { retryDelay--; return; }
        switch (resetMode) {
            case TIME -> tickTime();
            case PERCENTAGE -> tickPercentage();
            case TIME_PERCENTAGE -> { if (!tickTime()) tickPercentage(); }
        }
    }

    public boolean tickTime() {
        if (resetting || resetDelay <= 0) return false;
        if (countdown > 0) countdown--;
        if (countdown <= 0) {
            mine.reset(mine.getPlugin());
            return true;
        }
        notifyPlayers("warning", false);
        return false;
    }

    public boolean tickPercentage() {
        if (resetting) return false;
        // No active region means no completed reset yet; bootstrap instead of getting stuck forever.
        if (mine.getRegionManager().getCurrent().isEmpty() || getRemainingPercentage() <= resetPercentage) {
            mine.reset(mine.getPlugin());
            return true;
        }
        return false;
    }

    public void resetCompleted(long nonAirBlocks) {
        totalBlocks = Math.max(0, nonAirBlocks);
        blockCount = totalBlocks;
        countdown = resetDelay;
        resetting = false;
        retryDelay = 0;
        notifyPlayers("reset", true);
    }

    public void resetFailed() { resetting = false; retryDelay = 5; }
    public boolean isResetting() { return resetting; }
    public void setResetting(boolean resetting) { this.resetting = resetting; }
    public long getTotalBlocks() { return totalBlocks; }
    public void setTotalBlocks(long totalBlocks) { this.totalBlocks = Math.max(0, totalBlocks); }
    public double getRemainingPercentage() { return totalBlocks <= 0 ? 0 : 100d * blockCount / totalBlocks; }
    public void blockBroken() { if (!resetting) blockCount = Math.max(0, blockCount - 1); }
    public void blockPlaced() { if (!resetting) blockCount = Math.min(totalBlocks, blockCount + 1); }

    private void notifyPlayers(String kind, boolean completed) {
        if (mine.getPlugin() == null || MessageService.getLocaleManager() == null) return;
        boolean chat = Boolean.TRUE.equals(mine.getFlagRecursive(Flag.WARN));
        boolean hotbar = !completed && Boolean.TRUE.equals(mine.getFlagRecursive(Flag.WARN_HOTBAR));
        Integer warningSeconds = mine.getFlagRecursive(Flag.WARN_SECONDS);
        if (!completed && countdown != (warningSeconds == null ? 5 : warningSeconds)) chat = false;
        if (!chat && !hotbar) return;
        for (var player : mine.getPlugin().getServer().getOnlinePlayers()) {
            boolean global = Boolean.TRUE.equals(mine.getFlagRecursive(Flag.WARN_GLOBAL));
            if (!global && mine.getRegionManager().getCurrent().filter(region -> region.contains(player.getLocation())).isEmpty()) continue;
            var locales = MessageService.getLocaleManager();
            String text = locales.getMessage(locales.getLocale(player), "catamines.runtime." + kind);
            text = CountdownFormatter.format(text, countdown, locales.getLocale(player), mine);
            if (chat) player.sendMessage(text);
            if (hotbar) player.sendActionBar(CountdownFormatter.format(
                    locales.getMessage(locales.getLocale(player), "catamines.runtime.hotbar"), countdown, locales.getLocale(player), mine));
        }
    }

    @Override
    public void serialize(ConfigurationSection section) {
        section.set("reset-mode", resetMode.toString());
        section.set("reset-delay", resetDelay);
        section.set("reset-percentage", resetPercentage);
        section.set("countdown", countdown);
        section.set("block-count", blockCount);
        section.set("total-blocks", totalBlocks);
    }

    public static CataMineController deserialize(ConfigurationSection section, CataMine mine) {
        CataMineController controller = new CataMineController(mine);
        controller.setResetMode(ResetMode.valueOf(section.getString("reset-mode", "TIME").toUpperCase(Locale.ROOT).replace('-', '_')));
        controller.setResetDelay(section.getInt("reset-delay", 300));
        controller.setResetPercentage(section.getDouble("reset-percentage", 30));
        controller.setCountdown(section.getInt("countdown", controller.resetDelay));
        controller.setTotalBlocks(section.getLong("total-blocks", 0));
        controller.setBlockCount(section.getLong("block-count", controller.totalBlocks));
        return controller;
    }

    public ResetMode getResetMode() { return resetMode; }
    public void setResetMode(ResetMode resetMode) { this.resetMode = Objects.requireNonNull(resetMode); }
    public int getResetDelay() { return resetDelay; }
    public void setResetDelay(int resetDelay) {
        if (resetDelay < -1) throw new IllegalArgumentException("Reset delay must be -1 (disabled) or a nonnegative number");
        this.resetDelay = resetDelay;
        countdown = resetDelay;
    }
    public double getResetPercentage() { return resetPercentage; }
    public void setResetPercentage(double resetPercentage) {
        if (!Double.isFinite(resetPercentage) || resetPercentage < 0 || resetPercentage > 100)
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        this.resetPercentage = resetPercentage;
    }
    public int getCountdown() { return countdown; }
    public void setCountdown(int countdown) { this.countdown = Math.max(0, countdown); }
    public long getBlockCount() { return blockCount; }
    public void setBlockCount(long blockCount) { this.blockCount = Math.max(0, blockCount); }
    public enum ResetMode { TIME, PERCENTAGE, TIME_PERCENTAGE }
}
