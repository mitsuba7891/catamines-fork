package me.catalysmrl.catamines.gui.generic;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.gui.impl.BaseGui;
import me.catalysmrl.catamines.utils.message.MessageService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.List;

public final class MineListGui extends BaseGui {
    private final int page;
    public MineListGui(CataMines plugin, Player player, int page) {
        super(plugin, player, MessageService.format(player, "catamines.gui.mines"));
        this.page = Math.max(0, page);
    }
    @Override public void setItems() {
        var mines = List.copyOf(plugin.getMineManager().getMines());
        for (int slot = 0; slot < 45 && page * 45 + slot < mines.size(); slot++) {
            var mine = mines.get(page * 45 + slot);
            button(slot, Material.IRON_PICKAXE, "§b" + mine.getDisplayName(), List.of("§7" + mine.getName()),
                    event -> new MineEditorGui(plugin, player, mine).open());
        }
        if (page > 0) button(45, Material.ARROW, text("previous"), List.of(), event -> new MineListGui(plugin, player, page - 1).open());
        button(49, Material.BARRIER, text("close"), List.of(), event -> player.closeInventory());
        if ((page + 1) * 45 < mines.size()) button(53, Material.ARROW, text("next"), List.of(), event -> new MineListGui(plugin, player, page + 1).open());
    }
}
