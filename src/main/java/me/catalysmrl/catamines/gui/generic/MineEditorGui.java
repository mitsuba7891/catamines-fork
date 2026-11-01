package me.catalysmrl.catamines.gui.generic;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.gui.impl.BaseGui;
import me.catalysmrl.catamines.mine.components.composition.CataMineBlock;
import me.catalysmrl.catamines.mine.components.composition.CataMineComposition;
import me.catalysmrl.catamines.mine.components.composition.drop.CataMineItem;
import me.catalysmrl.catamines.mine.components.region.CataMineRegion;
import me.catalysmrl.catamines.utils.message.Message;
import me.catalysmrl.catamines.utils.undo.UndoManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MineEditorGui extends BaseGui {
    private final CataMine mine;
    private final CataMineRegion region;
    private final CataMineComposition composition;
    private final CataMineBlock block;
    private final int page;

    public MineEditorGui(CataMines plugin, Player player, CataMine mine) { this(plugin, player, mine, null, null, null, 0); }
    private MineEditorGui(CataMines plugin, Player player, CataMine mine, CataMineRegion region,
                          CataMineComposition composition, CataMineBlock block, int page) {
        super(plugin, player, "CataMines • " + mine.getName());
        this.mine = mine;
        this.region = region;
        this.composition = composition;
        this.block = block;
        this.page = Math.max(0, page);
    }
    @Override public void open() {
        if (plugin.getMineManager().getMine(mine.getName()).orElse(null) != mine) {
            Message.MINE_NOT_EXISTS.send(player); player.closeInventory(); return;
        }
        super.open();
    }

    @Override public void setItems() {
        if (block != null) loot();
        else if (composition != null) blocks();
        else if (region != null) compositions();
        else root();
        button(49, Material.BARRIER, text("close"), List.of(), event -> player.closeInventory());
    }

    private void root() {
        var controller = mine.getController();
        button(10, Material.REDSTONE, text("reset"), List.of(), event -> command("reset " + mine.getName()));
        button(11, Material.LIME_DYE, text("start"), List.of(), event -> command("start " + mine.getName()));
        button(12, Material.RED_DYE, text("stop"), List.of(), event -> command("stop " + mine.getName()));
        button(14, Material.COMPARATOR, text("mode", controller.getResetMode()), List.of(text("cycle")), event -> {
            var modes = me.catalysmrl.catamines.mine.components.manager.controller.CataMineController.ResetMode.values();
            command("resetmode " + mine.getName() + " " + modes[(controller.getResetMode().ordinal() + 1) % modes.length]);
        });
        button(15, Material.CLOCK, text("delay", controller.getResetDelay()), List.of(text("adjust")),
                event -> command("timer " + mine.getName() + " " + Math.clamp((long) controller.getResetDelay() + (event.isRightClick() ? -10 : 10), 1, Integer.MAX_VALUE)));
        button(16, Material.HOPPER, text("percentage", controller.getResetPercentage()), List.of(text("adjust")),
                event -> command("resetpercentage " + mine.getName() + " " + Math.clamp(controller.getResetPercentage() + (event.isRightClick() ? -5 : 5), 0, 100)));
        var regions = mine.getRegionManager().getChoices();
        for (int i = 0; i < 18 && page * 18 + i < regions.size(); i++) {
            var selected = regions.get(page * 18 + i);
            button(27 + i, Material.CHEST, text("region", selected.getName()), List.of(),
                    event -> navigate(selected, null, null, 0));
        }
        pages(regions.size(), 18);
        button(48, Material.ARROW, text("back"), List.of(), event -> new MineListGui(plugin, player, 0).open());
    }

    private void compositions() {
        var entries = region.getCompositionManager().getChoices();
        for (int i = 0; i < 45 && page * 45 + i < entries.size(); i++) {
            var selected = entries.get(page * 45 + i);
            button(i, Material.CHEST, text("composition", selected.getName()), List.of(), event -> navigate(region, selected, null, 0));
        }
        pages(entries.size(), 45);
        button(48, Material.ARROW, text("back"), List.of(), event -> navigate(null, null, null, 0));
    }

    private void blocks() {
        var entries = List.copyOf(composition.getBlocks());
        for (int i = 0; i < 45 && page * 45 + i < entries.size(); i++) {
            var selected = entries.get(page * 45 + i);
            Material material = Material.matchMaterial(selected.getBaseBlock().getBlockType().id());
            button(i, material == null || !material.isItem() ? Material.STONE : material, selected.getBaseBlock().getAsString(),
                    List.of(text("chance", selected.getChance()), text("edit-drops")), event -> navigate(region, composition, selected, 0));
        }
        pages(entries.size(), 45);
        button(50, Material.EMERALD, text("add-block"), List.of(text("copy-hand")), event -> edit(() -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!held.getType().isBlock() || held.getType().isAir()) return;
            try { composition.addBlock(new CataMineBlock(held.getType().getKey().toString(), 100)); }
            catch (com.sk89q.worldedit.extension.input.InputParseException e) { throw new IllegalArgumentException(e); }
        }));
        button(48, Material.ARROW, text("back"), List.of(), event -> navigate(region, null, null, 0));
    }

    private void loot() {
        var entries = List.copyOf(block.getItems());
        for (int i = 0; i < 36 && page * 36 + i < entries.size(); i++) {
            var selected = entries.get(page * 36 + i);
            button(i, selected.getItem(), text("drop"), List.of(text("chance", selected.getChance()),
                    text("fortune", selected.isFortune()), text("drop-controls")), event -> edit(() -> {
                if (event.isShiftClick()) block.getItems().remove(selected);
                else if (event.isRightClick()) selected.setFortune(!selected.isFortune());
                else selected.setChance(selected.getChance() >= 100 ? 5 : Math.min(100, selected.getChance() + 5));
            }));
        }
        button(38, Material.COMPARATOR, text("chance", block.getChance()), List.of(text("adjust")), event -> edit(() ->
                block.setChance(Math.clamp(block.getChance() + (event.isRightClick() ? -5 : 5), 0, 100))));
        button(39, Material.HOPPER, text("drop-mode", block.getDropType()), List.of(text("cycle")), event -> edit(() -> {
            var modes = CataMineBlock.DropType.values();
            block.setDropType(modes[(block.getDropType().ordinal() + 1) % modes.length]);
        }));
        button(41, Material.EMERALD, text("add-drop"), List.of(text("copy-hand")), event -> edit(() -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) return;
            block.getItems().add(new CataMineItem(held));
            block.setDropType(CataMineBlock.DropType.CUSTOM);
        }));
        button(42, Material.TNT, text("remove-block"), List.of(), event -> {
            if (edit(() -> composition.removeBlock(block))) navigate(region, composition, null, 0);
        });
        pages(entries.size(), 36);
        button(48, Material.ARROW, text("back"), List.of(), event -> navigate(region, composition, null, 0));
    }

    private void pages(int count, int size) {
        if (page > 0) button(45, Material.ARROW, text("previous"), List.of(), event -> navigate(region, composition, block, page - 1));
        if ((page + 1) * size < count) button(53, Material.ARROW, text("next"), List.of(), event -> navigate(region, composition, block, page + 1));
    }
    private void navigate(CataMineRegion region, CataMineComposition composition, CataMineBlock block, int page) {
        new MineEditorGui(plugin, player, mine, region, composition, block, page).open();
    }
    private void command(String command) { Bukkit.dispatchCommand(player, "cm " + command); open(); }

    private boolean edit(Runnable change) {
        if (plugin.getMineManager().getMine(mine.getName()).orElse(null) != mine) { player.closeInventory(); return false; }
        if (!player.hasPermission("catamines.set")) { Message.NO_PERMISSION.send(player); return false; }
        if (!mine.getRegionManager().getChoices().contains(region)
                || !region.getCompositionManager().getChoices().contains(composition)
                || (block != null && !composition.getBlocks().contains(block))) { player.closeInventory(); return false; }
        try {
            UndoManager.record(player, List.of(mine), "GUI edit");
            plugin.getMineManager().cancelReset(mine);
            change.run();
            plugin.getMineManager().saveMine(mine);
            open();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("GUI edit failed for " + mine.getName() + ": " + e.getMessage());
            Message.MINE_SAVE_EXCEPTION.send(player, mine.getName());
            return false;
        }
    }
}
