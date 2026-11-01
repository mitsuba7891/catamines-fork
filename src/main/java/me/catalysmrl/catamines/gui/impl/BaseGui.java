package me.catalysmrl.catamines.gui.impl;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.utils.message.Message;
import me.catalysmrl.catamines.utils.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class BaseGui implements InventoryHolder {
    protected final CataMines plugin;
    protected final Player player;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> buttons = new HashMap<>();

    protected BaseGui(CataMines plugin, Player player, String title) {
        this.plugin = plugin;
        this.player = player;
        inventory = Bukkit.createInventory(this, 54, title);
    }

    public abstract void setItems();
    public void open() {
        if (!player.hasPermission("catamines.gui")) { Message.NO_PERMISSION.send(player); return; }
        buttons.clear();
        inventory.clear();
        setItems();
        player.openInventory(inventory);
    }

    public void click(InventoryClickEvent event) {
        if (!event.getWhoClicked().getUniqueId().equals(player.getUniqueId())) return;
        Consumer<InventoryClickEvent> action = buttons.get(event.getRawSlot());
        if (action == null) return;
        // Inventory transitions and edits must not happen inside the inventory transaction.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == inventory
                    && player.hasPermission("catamines.gui")) action.accept(event);
        });
    }

    protected void button(int slot, Material material, String name, List<String> lore, Consumer<InventoryClickEvent> action) {
        button(slot, new ItemStack(material), name, lore, action);
    }
    protected void button(int slot, ItemStack original, String name, List<String> lore, Consumer<InventoryClickEvent> action) {
        ItemStack icon = original.clone();
        var meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        icon.setItemMeta(meta);
        inventory.setItem(slot, icon);
        if (action != null) buttons.put(slot, action);
    }
    protected String text(String key, Object... args) { return MessageService.format(player, "catamines.gui." + key, args); }
    @Override @NotNull public Inventory getInventory() { return inventory; }
}
