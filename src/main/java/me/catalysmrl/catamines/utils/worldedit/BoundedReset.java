package me.catalysmrl.catamines.utils.worldedit;

import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;

import java.util.Iterator;
import java.util.Objects;

/** An immutable geometry snapshot; every write is checked against the approved shape. */
public final class BoundedReset {
    @FunctionalInterface
    public interface BlockWriter {
        void set(BlockVector3 position, BaseBlock block) throws Exception;
    }

    private final Region bounds;
    private final Pattern pattern;
    private final Iterator<BlockVector3> positions;
    private final long maximumBlocks;
    private long visited;
    private long nonAirBlocks;
    private boolean cancelled;

    public BoundedReset(Region region, Pattern pattern, long maximumBlocks) {
        this.bounds = RegionCodec.snapshot(Objects.requireNonNull(region));
        this.pattern = Objects.requireNonNull(pattern);
        this.maximumBlocks = maximumBlocks;
        if (bounds.getWorld() == null) throw new IllegalArgumentException("A regeneration world is required");
        if (maximumBlocks <= 0 || bounds.getVolume() <= 0 || bounds.getVolume() > maximumBlocks) {
            throw new IllegalArgumentException("Mine volume must be between 1 and " + maximumBlocks + " blocks");
        }
        if (bounds.getMinimumPoint().y() < bounds.getWorld().getMinimumPoint().y()
                || bounds.getMaximumPoint().y() > bounds.getWorld().getMaximumPoint().y()) {
            throw new IllegalArgumentException("Mine exceeds the world's build height");
        }
        positions = bounds.iterator();
    }

    public int applyBatch(int budget, BlockWriter writer) throws Exception {
        if (budget <= 0) throw new IllegalArgumentException("A positive batch budget is required");
        int processed = 0;
        while (!cancelled && processed < budget && positions.hasNext()) {
            BlockVector3 next = positions.next();
            BlockVector3 position = BlockVector3.at(next.x(), next.y(), next.z());
            if (++visited > maximumBlocks || !bounds.contains(position)) {
                cancelled = true;
                throw new IllegalStateException("Refused a regeneration write outside the approved mine geometry");
            }
            BaseBlock block = Objects.requireNonNull(pattern.applyBlock(position), "Reset pattern returned no block");
            writer.set(position, block);
            String id = block.getBlockType().id();
            if (!id.equals("minecraft:air") && !id.equals("minecraft:cave_air") && !id.equals("minecraft:void_air")) nonAirBlocks++;
            processed++;
        }
        return processed;
    }

    public Region getBounds() { return bounds.clone(); }
    public boolean isComplete() { return !cancelled && !positions.hasNext(); }
    public long getNonAirBlocks() { return nonAirBlocks; }
    public long getVisitedBlocks() { return visited; }
    public void cancel() { cancelled = true; }
    public boolean isCancelled() { return cancelled; }
}
