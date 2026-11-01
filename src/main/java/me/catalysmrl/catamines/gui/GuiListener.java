package me.catalysmrl.catamines.gui;

import me.catalysmrl.catamines.gui.impl.BaseGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {
    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BaseGui gui)) return;
        event.setCancelled(true); // Includes shift-clicks, number keys, double-clicks and the player's inventory.
        if (!event.isLeftClick() && !event.isRightClick()) return;
        if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) gui.click(event);
    }
    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BaseGui) event.setCancelled(true);
    }
}
