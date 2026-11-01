package me.catalysmrl.catamines.managers;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.api.serialization.DeserializationException;
import me.catalysmrl.catamines.managers.blockmanagers.BlockApplicator;
import me.catalysmrl.catamines.managers.blockmanagers.BukkitBlockApplicationManager;
import me.catalysmrl.catamines.managers.blockmanagers.FastAsyncBlockApplicationManager;
import me.catalysmrl.catamines.mine.components.region.CataMineRegion;
import me.catalysmrl.catamines.mine.mines.AdvancedCataMine;
import me.catalysmrl.catamines.mine.components.composition.CataMineBlock;
import me.catalysmrl.catamines.mine.components.composition.CataMineComposition;
import me.catalysmrl.catamines.mine.components.composition.drop.DropTable;
import me.catalysmrl.catamines.api.events.CataMineBlockBreakEvent;
import me.catalysmrl.catamines.utils.helper.CompatibilityProvider;
import me.catalysmrl.catamines.utils.message.Message;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MineManager {

    private final Path minesPath;
    private final Path schematicsPath;

    private final CataMines plugin;
    private BukkitTask minesTask;
    private BlockApplicator blockApplicator;

    private final List<CataMine> mines = new ArrayList<>();
    private record PreparedBreak(CataMine mine, CataMineBlock block) {}
    private final java.util.Map<BlockBreakEvent, PreparedBreak> pendingBreaks = new java.util.WeakHashMap<>();

    public MineManager(CataMines plugin) {
        this.plugin = plugin;
        minesPath = plugin.getDataFolder().toPath().resolve("mines");
        schematicsPath = plugin.getDataFolder().toPath().resolve("schematics");
        try {
            createDirectoriesIfNotExists(minesPath);
            createDirectoriesIfNotExists(schematicsPath);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create mines and/or schematics directory");
            plugin.getLogger().severe(e.getMessage());
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> loadMinesFromFolder(minesPath), 2L);
        start();
    }

    public void start() {
        initBlockApplicator();
        initMineTask();
    }

    public void shutDown() {
        if (blockApplicator != null) blockApplicator.cancel();
        if (minesTask != null) minesTask.cancel();
        pendingBreaks.clear();

        for (CataMine mine : mines) {
            try {
                saveMine(mine);
            } catch (IOException e) {
                plugin.getLogger().severe(
                        Message.MINE_SAVE_EXCEPTION.format(plugin.getServer().getConsoleSender(), mine.getName()));
                plugin.getLogger().severe(e.getMessage());
            }
        }
    }

    /**
     * Initializes the BlockApplicationManager. It's responsible for Block efficient
     * Block manipulation as well as balancing workload.
     */
    private void initBlockApplicator() {
        Logger logger = plugin.getLogger();

        logger.info("Starting BlockApplicationManager...");

        if (blockApplicator != null) {
            logger.warning("BlockApplicationManager already running. Cancelling and running new manager.");
            blockApplicator.cancel();
        }

        if (CompatibilityProvider.isFaweEnabled()) {
            logger.info("Initializing FastAsyncBlockApplicationManager...");
            blockApplicator = new FastAsyncBlockApplicationManager(plugin);
        } else {
            logger.info("Initializing BukkitBlockApplicationManager...");
            blockApplicator = new BukkitBlockApplicationManager(plugin);
        }

        logger.info("Done initializing BlockApplicationManager.");

        logger.info("Starting BlockApplicationManager...");
        blockApplicator.start();
        logger.info("BlockApplicationManager started.");
    }

    /**
     * The mine task that ticks every mine once per second.
     */
    private void initMineTask() {
        Logger logger = plugin.getLogger();

        logger.info("Starting mine task...");

        if (minesTask != null) {
            logger.warning("Mine task already running. Overriding old mine task.");
            minesTask.cancel();
        }

        this.minesTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            for (CataMine cataMine : List.copyOf(mines)) {
                try { cataMine.tick(); }
                catch (Exception | LinkageError e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Mine tick failed: " + cataMine.getName(), e);
                    cataMine.getController().resetFailed();
                }
            }

        }, 0L, 20L);
    }

    /**
     * Queues this region for reset. Resetting means filling the region with blocks
     * configured by the region.
     *
     * @param region the mine to reset
     */
    public void resetRegion(CataMineRegion region) {
        blockApplicator.queueForReset(region);
    }

    public void cancelReset(CataMine mine) {
        if (blockApplicator != null) blockApplicator.cancelMine(mine);
    }

    /**
     * Attempts to load all mines from a directory and returns it as a
     * List of CataMines. If the directory does not exist, is not a folder or
     * does not contain any files ending with '.yml', then an empty
     * ArrayList is returned. Otherwise, attempts to load mines from all files
     * ending with '.yml'. Note that only direct children files of the folder are
     * affected. Another folder inside the folder will be ignored.
     *
     * @param folder the path to load the mines from
     * @return A list of successfully loaded mines of direct children paths
     */
    public List<CataMine> getMinesFromFolder(Path folder) {
        Objects.requireNonNull(folder);
        List<CataMine> cataMines = new ArrayList<>();

        if (!Files.isDirectory(folder)) {
            plugin.getLogger().severe("Path is not a directory: " + folder);
            return cataMines;
        }

        try (Stream<Path> stream = Files.list(folder)) {
            cataMines = stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".yml"))
                    .map(this::deserializeCataMineFromYaml)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed loading directory: " + folder);
        }

        plugin.getLogger().info("Loaded " + cataMines.size() + " mines");
        return cataMines;
    }

    private Optional<CataMine> deserializeCataMineFromYaml(Path path) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(path.toFile());
        return deserializeCataMine(cfg);
    }

    private Optional<CataMine> deserializeCataMine(ConfigurationSection section) {
        CataMine mine = null;
        try {
            mine = AdvancedCataMine.deserialize(plugin, section);
        } catch (DeserializationException | RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Skipped invalid mine configuration: " + e.getMessage(), e);
        }
        return Optional.ofNullable(mine);
    }

    /**
     * Loads all mines of folder into this MineManager
     * {@link #getMinesFromFolder(Path)}
     *
     * @param folder the folder to load the mines from
     */
    public void loadMinesFromFolder(Path folder) {
        mines.forEach(this::cancelReset);
        mines.clear();
        getMinesFromFolder(folder).forEach(mine -> {
            if (!containsMine(mine)) registerMine(mine);
            else plugin.getLogger().warning("Skipped duplicate mine: " + mine.getName());
        });
    }

    public void callBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        if (isResettingAt(event.getBlock().getLocation())) { event.setCancelled(true); return; }
        CataMine mine = getMineAtLocation(event.getBlock().getLocation()).orElse(null);
        if (mine == null) return;
        if (mine.getController().isResetting()) { event.setCancelled(true); return; }
        CataMineRegion region = getRegionAtLocation(mine, event.getBlock().getLocation()).orElse(null);
        if (region == null) return;
        CataMineComposition composition = region.getCompositionManager().getCurrent().orElse(null);
        CataMineBlock matched = composition == null ? null : composition.getBlocks().stream()
                .filter(block -> block.matches(event.getBlock())).findFirst().orElse(null);
        CataMineBlockBreakEvent apiEvent = new CataMineBlockBreakEvent(mine, region, composition, matched, event);
        Bukkit.getPluginManager().callEvent(apiEvent);
        if (apiEvent.isCancelled()) { event.setCancelled(true); return; }
        if (matched != null && matched.hasCustomDrops()) event.setDropItems(false);
        pendingBreaks.put(event, new PreparedBreak(mine, matched));
    }

    public void finishBlockBreak(BlockBreakEvent event) {
        PreparedBreak prepared = pendingBreaks.remove(event);
        if (prepared == null || event.isCancelled()) return;
        prepared.mine.getController().blockBroken();
        if (prepared.block == null || !prepared.block.hasCustomDrops()
                || event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        // A failed custom roll produces nothing: never fall back to the original block.
        org.bukkit.inventory.ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        int fortune = hand == null || hand.getType().isAir() ? 0
                : hand.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
        var drops = DropTable.roll(prepared.block, fortune, java.util.concurrent.ThreadLocalRandom.current());
        var location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.isCancelled()) drops.forEach(item -> location.getWorld().dropItemNaturally(location, item));
        });
    }

    public void callBlockPlace(BlockPlaceEvent event) {
        if (!event.isCancelled() && event.getBlockReplacedState().getType().isAir()) {
            getMineAtLocation(event.getBlock().getLocation()).ifPresent(mine -> mine.getController().blockPlaced());
        }
    }

    public boolean isResettingAt(org.bukkit.Location location) {
        return mines.stream().anyMatch(mine -> mine.getController().isResetting()
                && mine.getRegionManager().getChoices().stream().anyMatch(region -> region.contains(location)));
    }

    /**
     * Returns the mine with matching ID (name). Returns null if not present
     *
     * @param id ID or name of the mine
     * @return the mine if found, otherwise null
     */
    public Optional<CataMine> getMine(String id) {
        return mines.stream()
                .filter(cataMine -> cataMine.getName().equals(id))
                .findFirst();
    }

    public Optional<CataMine> getMineAtLocation(org.bukkit.Location location) {
        for (CataMine mine : mines) {
            for (CataMineRegion region : mine.getRegionManager().getCurrent().stream().toList()) {
                if (region.contains(location)) {
                    return Optional.of(mine);
                }
            }
        }
        return Optional.empty();
    }
    
    public Optional<CataMineRegion> getRegionAtLocation(CataMine mine, org.bukkit.Location location) {
        for (CataMineRegion region : mine.getRegionManager().getCurrent().stream().toList()) {
            if (region.contains(location)) return Optional.of(region);
        }
        return Optional.empty();
    }

    /**
     * Returns the list containing all registered mines.
     *
     * @return the registered mines
     */
    public List<CataMine> getMines() {
        return mines;
    }

    /**
     * Returns a list of every mine name that is registered.
     *
     * @return a list of mine IDs
     */
    public List<String> getMineList() {
        return mines.stream().map(CataMine::getName).collect(Collectors.toList());
    }

    /**
     * Returns true if a mine with matching ID (name) is registered.
     *
     * @param id ID or name of the mine
     * @return true if mine with matching ID is registered
     */
    public boolean containsMine(String id) {
        return mines.stream().anyMatch(mine -> mine.getName().equals(id));
    }

    /**
     * {@link #containsMine(String)}
     *
     * @param mine the cata mine
     * @return true if a mine with matching ID is registered
     */
    public boolean containsMine(CataMine mine) {
        return containsMine(mine.getName());
    }

    /**
     * Registers a mine
     *
     * @param mine the mine to register
     * @throws IllegalArgumentException if the mine is already registered
     */
    public void registerMine(CataMine mine) {
        if (containsMine(mine))
            throw new IllegalArgumentException();
        mines.add(mine);
    }

    public void deleteMine(CataMine cataMine) throws IOException {
        cancelReset(cataMine);
        mines.remove(cataMine);

        Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("mines").resolve(cataMine.getName() + ".yml"));
    }

    public void saveMine(CataMine mine) throws IOException {
        me.catalysmrl.catamines.mine.abstraction.AbstractCataMine.validateName(mine.getName());
        Path file = plugin.getDataFolder().toPath().resolve("mines").resolve(mine.getName() + ".yml");
        FileConfiguration fileCfg = new YamlConfiguration();

        mine.serialize(fileCfg);

        Path temporary = Files.createTempFile(minesPath, mine.getName() + "-", ".tmp");
        try {
            fileCfg.save(temporary.toFile());
            try { Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    public Path getMinesPath() {
        return minesPath;
    }

    private static void createDirectoriesIfNotExists(Path path) throws IOException {
        if (Files.exists(path) && (Files.isDirectory(path) || Files.isSymbolicLink(path))) {
            return;
        }

        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException e) {
            // ignore
        }
    }
}
