package me.catalysmrl.catamines.managers.blockmanagers;

import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.mine.components.composition.CataMineComposition;
import me.catalysmrl.catamines.mine.components.region.CataMineRegion;
import me.catalysmrl.catamines.utils.worldedit.BoundedReset;
import me.catalysmrl.catamines.utils.worldedit.WorldEditUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;

/** Server-thread-only, bounded, failure-isolated regeneration queue. */
public abstract class BlockApplicator {
    protected final JavaPlugin plugin;
    private record Request(CataMine mine, CataMineRegion region, CataMineComposition composition, BoundedReset edit) {}
    private final Deque<Request> resetQueue = new ArrayDeque<>();

    public BlockApplicator(JavaPlugin plugin) { this.plugin = plugin; }
    public abstract void start();
    public abstract void cancel();

    public void queueForReset(CataMineRegion region) {
        requireServerThread();
        if (!(region.getParent() instanceof CataMine mine)) throw new IllegalStateException("Region has no owning mine");
        if (resetQueue.stream().anyMatch(request -> request.mine == mine)) return;
        try {
            long maximum = plugin.getConfig().getLong("regeneration.maximum-mine-blocks", 10_000_000);
            BoundedReset edit = region.prepareReset(maximum);
            resetQueue.offer(new Request(mine, region, region.getCompositionManager().getUpcoming().orElse(null), edit));
            mine.getController().setResetting(true);
        } catch (RuntimeException e) {
            mine.getController().resetFailed();
            plugin.getLogger().log(Level.WARNING, "Refused reset of " + mine.getName() + ": " + e.getMessage());
        }
    }

    protected void handleQueue() {
        requireServerThread();
        Request request = resetQueue.poll();
        if (request == null) return;
        try {
            if (!request.mine.getRegionManager().getChoices().contains(request.region)
                    || (request.composition != null && !request.region.getCompositionManager().getChoices().contains(request.composition))) {
                request.edit.cancel();
            }
            if (request.edit.isCancelled()) {
                request.mine.getController().resetFailed();
                return;
            }
            int budget = Math.clamp(plugin.getConfig().getInt("regeneration.blocks-per-tick", 2000), 1, 50_000);
            if (request.edit.getVisitedBlocks() == 0) evacuatePlayers(request);
            WorldEditUtils.applyBatch(request.edit, budget);
            if (!request.edit.isComplete()) {
                resetQueue.offer(request);
                return;
            }
            if (request.composition != null) {
                request.region.getCompositionManager().setUpcoming(request.composition);
                request.region.getCompositionManager().next();
            }
            request.mine.getRegionManager().setUpcoming(request.region);
            request.mine.getRegionManager().next();
            request.mine.getController().resetCompleted(request.edit.getNonAirBlocks());
        } catch (Exception | LinkageError e) {
            request.edit.cancel();
            request.mine.getController().resetFailed();
            plugin.getLogger().log(Level.SEVERE, "Reset failed for " + request.mine.getName()
                    + "; other mines will continue. Check your WorldEdit/FAWE version.", e);
        }
    }

    public void cancelMine(CataMine mine) {
        requireServerThread();
        resetQueue.removeIf(request -> {
            if (request.mine != mine) return false;
            request.edit.cancel();
            request.mine.getController().resetFailed();
            return true;
        });
    }

    private void evacuatePlayers(Request request) {
        if (!Boolean.TRUE.equals(request.region.getFlagRecursive(me.catalysmrl.catamines.api.mine.Flag.TELEPORT_PLAYERS))) return;
        var bounds = request.edit.getBounds();
        var world = Bukkit.getWorld(bounds.getWorld().getName());
        if (world == null) throw new IllegalStateException("Mine world is unloaded");
        for (var player : world.getPlayers()) {
            boolean inside = request.region.contains(player.getLocation())
                    || request.mine.getRegionManager().getCurrent().filter(region -> region.contains(player.getLocation())).isPresent();
            if (!inside) continue;
            var destination = request.region.getFlagRecursive(me.catalysmrl.catamines.api.mine.Flag.RESET_TELEPORT_LOCATION);
            if (destination == null) {
                destination = player.getLocation().clone();
                int y = Math.max(bounds.getMaximumPoint().y() + 2, world.getHighestBlockYAt(destination) + 1);
                if (y + 1 >= world.getMaxHeight()) throw new IllegalStateException("No safe space above mine; set a reset teleport location");
                destination.setY(y);
            }
            if (destination.getWorld() == null || request.region.contains(destination)
                    || !destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()
                    || !player.teleport(destination)) throw new IllegalStateException("Reset teleport location is blocked or inside the mine");
        }
    }

    protected void clearQueue() {
        resetQueue.forEach(request -> {
            request.edit.cancel();
            request.mine.getController().resetFailed();
        });
        resetQueue.clear();
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Mine resets must run on the server thread");
    }
}
