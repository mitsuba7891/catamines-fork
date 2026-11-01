package me.catalysmrl.catamines.smoke;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.function.pattern.AbstractPattern;
import com.sk89q.worldedit.math.*;
import com.sk89q.worldedit.regions.*;
import com.sk89q.worldedit.world.block.BaseBlock;
import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.Flag;
import me.catalysmrl.catamines.mine.components.composition.CataMineBlock;
import me.catalysmrl.catamines.mine.components.composition.CataMineComposition;
import me.catalysmrl.catamines.mine.components.composition.drop.CataMineItem;
import me.catalysmrl.catamines.mine.components.manager.controller.CataMineController.ResetMode;
import me.catalysmrl.catamines.mine.components.region.CataMineRegion;
import me.catalysmrl.catamines.mine.components.region.impl.*;
import me.catalysmrl.catamines.mine.mines.AdvancedCataMine;
import me.catalysmrl.catamines.utils.worldedit.*;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;

/** Destructive test fixtures: only run in the disposable worlds described in docs/TESTING.md. */
public final class SmokeTests extends JavaPlugin {
    private CataMines plugin;
    private org.bukkit.World world;
    private final List<AdvancedCataMine> mines = new ArrayList<>();
    private final Map<BlockVector3, Material> expected = new HashMap<>();
    private AdvancedCataMine broken;
    private int waits;
    private String phase = "start";

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> guard(this::start), 20);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!phase.equals("done")) fail(new AssertionError("Timeout in phase " + phase));
        }, 1200);
    }

    private void start() throws Exception {
        plugin = CataMines.getInstance();
        check(plugin != null && plugin.isEnabled(), "CataMines is enabled");
        plugin.getConfig().set("regeneration.blocks-per-tick", 17);
        plugin.getConfig().set("regeneration.maximum-mine-blocks", 100_000);
        world = Bukkit.getWorlds().getFirst();
        world.getChunkAt(1, 1).addPluginChunkTicket(this); // The drop fixture uses a proxy, not a connected player.
        var weWorld = BukkitAdapter.adapt(world);
        List<Region> shapes = new ArrayList<>();
        shapes.add(new CuboidRegion(weWorld, BlockVector3.at(20, 100, 20), BlockVector3.at(24, 104, 24)));
        shapes.add(new CylinderRegion(weWorld, BlockVector3.at(50, 100, 50), Vector2.at(4, 3), 100, 104));
        shapes.add(new EllipsoidRegion(weWorld, BlockVector3.at(80, 104, 80), Vector3.at(4, 3, 2)));
        shapes.add(new Polygonal2DRegion(weWorld, List.of(BlockVector2.at(110, 110), BlockVector2.at(116, 110),
                BlockVector2.at(116, 112), BlockVector2.at(112, 112), BlockVector2.at(112, 116), BlockVector2.at(110, 116)), 100, 105));
        var hull = new ConvexPolyhedralRegion(weWorld);
        List.of(BlockVector3.at(140, 100, 140), BlockVector3.at(146, 100, 140), BlockVector3.at(140, 100, 146),
                BlockVector3.at(140, 106, 140)).forEach(hull::addVertex);
        hull.shift(BlockVector3.ZERO);
        shapes.add(hull);
        int index = 0;
        for (Region shape : shapes) {
            var mine = mine("shape" + index++);
            var region = new SelectionRegion("default", SelectionRegion.SelectionType.NONE, shape);
            var composition = new CataMineComposition("default");
            composition.addBlock(new CataMineBlock("minecraft:stone", 100));
            region.getCompositionManager().add(composition); mine.getRegionManager().add(region);
            // Round-trip the region before placing. This catches shape changes after a restart.
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            region.serialize(yaml);
            var restored = SelectionRegion.deserialize(yaml);
            mine.getRegionManager().remove(region); mine.getRegionManager().add(restored);
            mark(shape, point -> Material.STONE);
            for (int repeat = 0; repeat < 5; repeat++) mine.reset(plugin);
            check(mine.getRegionManager().getCurrent().isEmpty(), "No active region before completion");
        }
        schematic();
        broken = new AdvancedCataMine(plugin, "fault-injection");
        broken.setFlag(Flag.STOPPED, true);
        Region faultBounds = new CuboidRegion(weWorld, BlockVector3.at(260, 100, 260), BlockVector3.at(261, 101, 261));
        var badRegion = new SelectionRegion("fault", SelectionRegion.SelectionType.CUBOID, faultBounds) {
            @Override public BoundedReset prepareReset(long maximum) {
                return new BoundedReset(faultBounds, new AbstractPattern() {
                    @Override public BaseBlock applyBlock(BlockVector3 point) { throw new IllegalStateException("Expected smoke-test fault injection"); }
                }, maximum);
            }
        };
        broken.getRegionManager().add(badRegion); plugin.getMineManager().registerMine(broken); broken.reset(plugin);
        phase = "geometry";
        next(this::awaitGeometry);
    }

    private AdvancedCataMine mine(String name) {
        var mine = new AdvancedCataMine(plugin, name);
        mine.setFlag(Flag.STOPPED, true); plugin.getMineManager().registerMine(mine); mines.add(mine); return mine;
    }

    private void mark(Region shape, java.util.function.Function<BlockVector3, Material> contents) {
        BlockVector3 min = shape.getMinimumPoint().subtract(1, 1, 1), max = shape.getMaximumPoint().add(1, 1, 1);
        for (int x = min.x(); x <= max.x(); x++) for (int y = min.y(); y <= max.y(); y++) for (int z = min.z(); z <= max.z(); z++) {
            BlockVector3 point = BlockVector3.at(x, y, z);
            world.getBlockAt(x, y, z).setType(Material.GOLD_BLOCK, false);
            expected.put(point, shape.contains(point) ? contents.apply(point) : Material.GOLD_BLOCK);
        }
    }

    private void schematic() throws Exception {
        var clipboard = new BlockArrayClipboard(new CuboidRegion(BlockVector3.at(11, 22, 33), BlockVector3.at(13, 24, 35)));
        clipboard.setOrigin(BlockVector3.at(10, 21, 32));
        BaseBlock stone = BaseBlockParser.parse("minecraft:stone");
        for (BlockVector3 point : clipboard.getRegion()) clipboard.setBlock(point, stone);
        clipboard.setBlock(BlockVector3.at(11, 22, 33), BaseBlockParser.parse("minecraft:air"));
        var path = plugin.getDataPath().resolve("schematics/offset.schem");
        try (var output = Files.newOutputStream(path); var writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(output)) { writer.write(clipboard); }
        BlockVector3 destination = BlockVector3.at(200, 100, 200);
        var mine = mine("schematic");
        var region = new SchematicRegion("default", "offset.schem", BukkitAdapter.adapt(world), destination);
        mine.getRegionManager().add(region);
        Region target = WorldEditUtils.schematicReset(clipboard, BukkitAdapter.adapt(world), destination, 100_000).getBounds();
        mark(target, point -> point.equals(BlockVector3.at(201, 101, 201)) ? Material.AIR : Material.STONE);
        check(region.contains(new Location(world, 201, 101, 201)), "Translated schematic contains destination");
        check(!region.contains(new Location(world, 11, 22, 33)), "Schematic excludes old source coordinates");
        mine.reset(plugin);
    }

    private void awaitGeometry() throws Exception {
        if (mines.stream().anyMatch(mine -> mine.getController().isResetting())) { check(++waits < 300, "Geometry queue completed"); next(this::awaitGeometry); return; }
        for (var entry : expected.entrySet()) {
            BlockVector3 p = entry.getKey();
            check(world.getBlockAt(p.x(), p.y(), p.z()).getType() == entry.getValue(), "Geometry/sentinel mismatch at " + p);
        }
        check(!broken.getController().isResetting(), "A failed job is released");
        plugin.getMineManager().deleteMine(broken);
        for (var mine : mines) {
            check(mine.getRegionManager().getCurrent().isPresent(), "Region activated after successful completion");
            plugin.getMineManager().saveMine(mine);
        }
        plugin.getMineManager().loadMinesFromFolder(plugin.getMineManager().getMinesPath());
        check(plugin.getMineManager().getMine("shape3").orElseThrow().getRegionManager().getCurrent().orElseThrow().getVolume() > 6,
                "Polygon height persisted");
        phase = "timer"; waits = 0;
        var mine = plugin.getMineManager().getMine("shape0").orElseThrow();
        mine.setFlag(Flag.STOPPED, false); mine.getController().setResetDelay(1); mine.getController().setResetMode(ResetMode.TIME);
        world.getBlockAt(20, 100, 20).setType(Material.AIR, false);
        next(this::awaitTimer);
    }

    private void awaitTimer() throws Exception {
        if (world.getBlockAt(20, 100, 20).getType() != Material.STONE) { check(++waits < 100, "Automatic timer reset"); next(this::awaitTimer); return; }
        var mine = plugin.getMineManager().getMine("shape0").orElseThrow();
        if (mine.getController().isResetting()) { next(this::awaitTimer); return; }
        mine.getController().setResetMode(ResetMode.TIME_PERCENTAGE); mine.getController().setResetDelay(600);
        mine.getController().setBlockCount(0); world.getBlockAt(20, 100, 20).setType(Material.AIR, false);
        phase = "percentage"; waits = 0; next(this::awaitPercentage);
    }

    private void awaitPercentage() throws Exception {
        if (world.getBlockAt(20, 100, 20).getType() != Material.STONE) { check(++waits < 100, "Automatic percentage reset"); next(this::awaitPercentage); return; }
        var mine = plugin.getMineManager().getMine("shape0").orElseThrow();
        if (mine.getController().isResetting()) { next(this::awaitPercentage); return; }
        mine.setFlag(Flag.STOPPED, true);
        for (int x = 19; x <= 21; x++) for (int y = 100; y <= 102; y++) for (int z = 19; z <= 21; z++) {
            world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        world.getBlockAt(20, 100, 20).setType(Material.STONE, false);
        var block = mine.getRegionManager().getCurrent().orElseThrow().getCompositionManager().getCurrent().orElseThrow().getBlocks().getFirst();
        var coin = new ItemStack(Material.GOLD_NUGGET, 3);
        var meta = coin.getItemMeta(); meta.setDisplayName("§6Moneda");
        meta.getPersistentDataContainer().set(new NamespacedKey("smoke", "coin"), PersistentDataType.STRING, "currency");
        coin.setItemMeta(meta); block.getItems().add(new CataMineItem(coin)); block.setDropType(CataMineBlock.DropType.CUSTOM);
        var inventory = (PlayerInventory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlayerInventory.class},
                (proxy, method, args) -> method.getName().equals("getItemInMainHand") ? new ItemStack(Material.DIAMOND_PICKAXE) : fallback(method.getReturnType()));
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getInventory" -> inventory;
            case "getGameMode" -> GameMode.SURVIVAL;
            case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
            case "getName" -> "SmokeTestPlayer";
            default -> fallback(method.getReturnType());
        });
        BlockBreakEvent event = new BlockBreakEvent(world.getBlockAt(20, 100, 20), player);
        long before = mine.getController().getBlockCount();
        plugin.getMineManager().callBlockBreak(event); check(!event.isDropItems(), "Vanilla drops suppressed");
        plugin.getMineManager().finishBlockBreak(event); check(mine.getController().getBlockCount() == before - 1, "Break counter decremented");
        event.getBlock().setType(Material.AIR, false);
        phase = "drops";
        Bukkit.getScheduler().runTaskLater(this, () -> guard(this::finish), 5);
    }

    private static Object fallback(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        return null;
    }

    private void finish() throws Exception {
        var items = world.getNearbyEntities(new Location(world, 20.5, 100.5, 20.5), 3, 5, 3).stream()
                .filter(entity -> entity instanceof Item).map(entity -> ((Item) entity).getItemStack()).toList();
        check(items.size() == 1 && items.getFirst().getType() == Material.GOLD_NUGGET, "Only custom coin drops in the actual world; observed=" + items);
        check("currency".equals(items.getFirst().getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey("smoke", "coin"), PersistentDataType.STRING)), "Currency metadata preserved");
        phase = "done";
        var provider = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(candidate -> candidate.getName().equals("WorldEdit") || candidate.getName().equals("FastAsyncWorldEdit")).findFirst().orElseThrow();
        String result = "PASS " + Bukkit.getVersion() + " | " + provider.getName() + " " + provider.getPluginMeta().getVersion()
                + " | shapes=5 schematic-offset+air timer combined-percentage custom-coin sentinels failure-isolation persistence";
        Files.writeString(getServer().getPluginsFolder().toPath().toAbsolutePath().getParent().resolve("smoke-result.txt"), result + "\n");
        getLogger().info(result); Bukkit.shutdown();
    }

    @FunctionalInterface private interface Step { void run() throws Exception; }
    private void next(Step step) { Bukkit.getScheduler().runTaskLater(this, () -> guard(step), 1); }
    private void guard(Step step) { try { step.run(); } catch (Throwable e) { fail(e); } }
    private void fail(Throwable e) {
        phase = "done"; getLogger().log(java.util.logging.Level.SEVERE, "SMOKE TEST FAILED", e);
        try { Files.writeString(getServer().getPluginsFolder().toPath().toAbsolutePath().getParent().resolve("smoke-result.txt"), "FAIL " + e + "\n"); }
        catch (Exception ignored) {}
        Bukkit.shutdown();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
