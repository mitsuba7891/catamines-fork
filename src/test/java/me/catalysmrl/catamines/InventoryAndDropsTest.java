package me.catalysmrl.catamines;

import me.catalysmrl.catamines.command.utils.CommandContext;
import me.catalysmrl.catamines.commands.mine.GuiCommand;
import me.catalysmrl.catamines.gui.GuiListener;
import me.catalysmrl.catamines.gui.impl.BaseGui;
import me.catalysmrl.catamines.managers.MineManager;
import me.catalysmrl.catamines.mine.components.composition.CataMineBlock;
import me.catalysmrl.catamines.mine.components.composition.drop.*;
import me.catalysmrl.catamines.mine.mines.AdvancedCataMine;
import me.catalysmrl.catamines.utils.message.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryAndDropsTest {
    @TempDir Path folder;
    private ServerMock server;
    private CataMines plugin;
    private MineManager manager;
    @BeforeEach void setup() {
        server = MockBukkit.mock();
        plugin = mock(CataMines.class);
        when(plugin.getName()).thenReturn("CataMines"); when(plugin.isEnabled()).thenReturn(true);
        var metadata = mock(io.papermc.paper.plugin.configuration.PluginMeta.class);
        when(metadata.getVersion()).thenReturn("test"); when(plugin.getPluginMeta()).thenReturn(metadata);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("inventory-test"));
        when(plugin.getDataFolder()).thenReturn(folder.toFile()); when(plugin.getDataPath()).thenReturn(folder);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration()); when(plugin.getServer()).thenReturn(server);
        when(plugin.getResource(anyString())).thenAnswer(call -> getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        var locales = new LocaleManager(plugin); locales.loadLocales(folder); MessageService.init(locales);
        manager = new MineManager(plugin); when(plugin.getMineManager()).thenReturn(manager);
        server.getScheduler().performTicks(3);
    }
    @AfterEach void teardown() { try { if (manager != null) manager.shutDown(); } finally { MockBukkit.unmock(); } }

    private ItemStack coin() {
        var item = new ItemStack(Material.GOLD_NUGGET, 3);
        var meta = item.getItemMeta(); meta.setDisplayName("§6Moneda"); meta.setLore(List.of("Custom currency"));
        meta.getPersistentDataContainer().set(new NamespacedKey("test", "coin"), PersistentDataType.STRING, "currency-id");
        item.setItemMeta(meta);
        return item;
    }
    @Test void customCoinRollPreservesMetadataAndNeverFallsBackToStone() {
        CataMineItem entry = new CataMineItem(coin()); entry.setChance(100);
        CataMineBlock block = mock(CataMineBlock.class);
        when(block.getItems()).thenReturn(List.of(entry)); when(block.getDropType()).thenReturn(CataMineBlock.DropType.CUSTOM);
        var rolled = DropTable.roll(block, 0, new Random(17));
        assertEquals(1, rolled.size()); assertEquals(coin(), rolled.getFirst());
        entry.setChance(0); assertTrue(DropTable.roll(block, 0, new Random(17)).isEmpty());
        assertEquals(3, entry.getItem().getAmount());
    }
    @Test void itemYamlRoundTripKeepsCurrencyTagsAndFortuneSetting() throws Exception {
        CataMineItem entry = new CataMineItem(coin()); entry.setChance(62.5); entry.setFortune(true);
        var yaml = new YamlConfiguration(); entry.serialize(yaml.createSection("drop"));
        var restoredYaml = new YamlConfiguration(); restoredYaml.loadFromString(yaml.saveToString());
        var restored = CataMineItem.deserialize(restoredYaml.getConfigurationSection("drop"));
        assertEquals(coin(), restored.getItem()); assertEquals(62.5, restored.getChance()); assertTrue(restored.isFortune());
        assertThrows(IllegalArgumentException.class, () -> restored.setChance(Double.NaN));
    }
    @Test void bothGuiCommandFormsOpenUsableInventories() throws Exception {
        var player = server.addPlayer(); player.setOp(true);
        manager.registerMine(new AdvancedCataMine(plugin, "test"));
        new GuiCommand().execute(plugin, player, new CommandContext(List.of()));
        assertInstanceOf(BaseGui.class, player.getOpenInventory().getTopInventory().getHolder());
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(0));
        new GuiCommand().execute(plugin, player, new CommandContext(List.of("test")));
        assertInstanceOf(BaseGui.class, player.getOpenInventory().getTopInventory().getHolder());
        assertEquals(Material.COMPARATOR, player.getOpenInventory().getTopInventory().getItem(14).getType());
    }
    @Test void guiCancelsShiftClickAndHotbarSwapToPreventItemTheft() throws Exception {
        var player = server.addPlayer(); player.setOp(true);
        new GuiCommand().execute(plugin, player, new CommandContext(List.of()));
        var shift = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        new GuiListener().click(shift); assertTrue(shift.isCancelled());
        var number = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        new GuiListener().click(number); assertTrue(number.isCancelled());
        var drag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.STONE),
                new ItemStack(Material.STONE), false, Map.of(0, new ItemStack(Material.STONE)));
        new GuiListener().drag(drag); assertTrue(drag.isCancelled());
    }
    @Test void lackOfGuiPermissionDoesNotOpenMenu() throws Exception {
        var player = server.addPlayer(); player.setOp(false);
        new GuiCommand().execute(plugin, player, new CommandContext(List.of()));
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || !(top.getHolder() instanceof BaseGui));
    }

    private AdvancedCataMine activeMine() {
        var mine = new AdvancedCataMine(plugin, "break-test");
        var region = mock(me.catalysmrl.catamines.mine.components.region.CataMineRegion.class);
        when(region.getName()).thenReturn("region"); when(region.getChance()).thenReturn(100d);
        when(region.contains(any())).thenReturn(true); when(region.getParent()).thenReturn(mine);
        var compositions = new me.catalysmrl.catamines.mine.components.manager.choice.ChoiceManager<me.catalysmrl.catamines.mine.components.composition.CataMineComposition>();
        when(region.getCompositionManager()).thenReturn(compositions);
        var composition = new me.catalysmrl.catamines.mine.components.composition.CataMineComposition("composition");
        var block = mock(CataMineBlock.class);
        when(block.matches(any())).thenReturn(true); when(block.hasCustomDrops()).thenReturn(true);
        when(block.getItems()).thenReturn(List.of(new CataMineItem(coin()))); when(block.getDropType()).thenReturn(CataMineBlock.DropType.CUSTOM);
        composition.setBlocks(List.of(block)); compositions.add(composition); compositions.setCurrent(composition);
        mine.getRegionManager().add(region); mine.getRegionManager().setCurrent(region);
        mine.getController().resetCompleted(10); manager.registerMine(mine);
        return mine;
    }

    @Test void cancelledBreakNeverPaysOrCounts() {
        var mine = activeMine(); var player = server.addPlayer(); var world = server.addSimpleWorld("cancel-test");
        var block = world.getBlockAt(1, 70, 1); block.setType(Material.STONE);
        var event = new org.bukkit.event.block.BlockBreakEvent(block, player);
        manager.callBlockBreak(event); assertFalse(event.isDropItems());
        event.setCancelled(true); manager.finishBlockBreak(event);
        server.getScheduler().performTicks(1);
        assertEquals(10, mine.getController().getBlockCount());
        assertTrue(world.getEntitiesByClass(org.bukkit.entity.Item.class).isEmpty());
    }

    @Test void successfulBreakReplacesVanillaDropAndCountsExactlyOnce() {
        var mine = activeMine(); var player = server.addPlayer(); var world = server.addSimpleWorld("break-world");
        var block = world.getBlockAt(1, 70, 1); block.setType(Material.STONE);
        var event = new org.bukkit.event.block.BlockBreakEvent(block, player);
        manager.callBlockBreak(event); manager.finishBlockBreak(event); manager.finishBlockBreak(event);
        block.setType(Material.AIR); server.getScheduler().performTicks(1);
        assertEquals(9, mine.getController().getBlockCount());
        var items = world.getEntitiesByClass(org.bukkit.entity.Item.class);
        assertEquals(1, items.size()); assertEquals(coin(), items.iterator().next().getItemStack());
    }
}
