package me.catalysmrl.catamines.utils.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.mask.RegionMask;
import com.sk89q.worldedit.function.pattern.AbstractPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import me.catalysmrl.catamines.CataMines;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class WorldEditUtils {
    private WorldEditUtils() {}

    public static RegionSelector getSelector(Player player) {
        return WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player))
                .getRegionSelector(BukkitAdapter.adapt(player.getWorld()));
    }

    public static int applyBatch(BoundedReset reset, int budget) throws Exception {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Asynchronous world writes are not allowed");
        Region bounds = reset.getBounds();
        if (Bukkit.getWorld(bounds.getWorld().getName()) == null) throw new IllegalStateException("Mine world is unloaded");
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder()
                .world(bounds.getWorld()).maxBlocks(budget).build()) {
            // No neighbor physics, entity AI, events or clipboard entities can expand this edit.
            session.setSideEffectApplier(SideEffectSet.none()
                    .with(SideEffect.LIGHTING, SideEffect.State.ON)
                    .with(SideEffect.NETWORK, SideEffect.State.ON));
            session.setTrackingHistory(false);
            session.setMask(new RegionMask(bounds));
            return reset.applyBatch(budget, session::setBlock);
        }
    }

    public static BoundedReset schematicReset(Clipboard clipboard, World world, BlockVector3 destination, long maximum) {
        Objects.requireNonNull(clipboard, "Missing schematic");
        BlockVector3 offset = Objects.requireNonNull(destination).subtract(clipboard.getOrigin());
        Region bounds = clipboard.getRegion().clone();
        try {
            bounds.shift(offset);
        } catch (com.sk89q.worldedit.regions.RegionOperationException e) {
            throw new IllegalArgumentException("Cannot translate schematic bounds", e);
        }
        bounds.setWorld(Objects.requireNonNull(world));
        return new BoundedReset(bounds, new AbstractPattern() {
            @Override
            public BaseBlock applyBlock(BlockVector3 position) {
                BlockVector3 source = position.subtract(offset);
                if (!clipboard.getRegion().contains(source)) throw new IllegalStateException("Schematic source is out of bounds");
                return clipboard.getFullBlock(source);
            }
        }, maximum);
    }

    public static Clipboard loadSchematic(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Missing schematic name");
        Path folder = CataMines.getInstance().getDataPath().resolve("schematics").toAbsolutePath().normalize();
        Path file = folder.resolve(name).normalize();
        if (!file.startsWith(folder) || !Files.isRegularFile(file)) throw new IllegalArgumentException("Invalid schematic path");
        try {
            if (!file.toRealPath().startsWith(folder.toRealPath())) throw new IllegalArgumentException("Schematic escapes its folder");
            ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
            if (format == null) throw new IllegalArgumentException("Unknown schematic format: " + name);
            try (var input = Files.newInputStream(file); ClipboardReader reader = format.getReader(input)) {
                return reader.read();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read schematic: " + name, e);
        }
    }
}
