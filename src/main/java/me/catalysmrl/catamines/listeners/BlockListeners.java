package me.catalysmrl.catamines.listeners;

import me.catalysmrl.catamines.managers.MineManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListeners implements Listener {

    private MineManager mineManager;

    public BlockListeners(MineManager mineManager) {
        this.mineManager = mineManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        mineManager.callBlockBreak(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterBlockBreak(BlockBreakEvent event) {
        mineManager.finishBlockBreak(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        mineManager.callBlockPlace(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardResetPlacement(BlockPlaceEvent event) {
        if (mineManager.isResettingAt(event.getBlock().getLocation())) event.setCancelled(true);
    }
}
