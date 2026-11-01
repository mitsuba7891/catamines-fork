package me.catalysmrl.catamines.mine.components.region.impl;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import me.catalysmrl.catamines.api.serialization.DeserializationException;
import me.catalysmrl.catamines.mine.components.region.AbstractCataMineRegion;
import me.catalysmrl.catamines.utils.worldedit.BoundedReset;
import me.catalysmrl.catamines.utils.worldedit.RegionCodec;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

public class SelectionRegion extends AbstractCataMineRegion {
    private Region region;

    public SelectionRegion(String name, RegionSelector selector) {
        super(name);
        defineRegion(selector);
    }

    public SelectionRegion(String name, SelectionType selectionType, Region region) {
        super(name);
        RegionCodec.type(region); // Refuse unsupported shapes; never fall back to a bounding cuboid.
        this.region = RegionCodec.snapshot(region);
    }

    public void defineRegion(RegionSelector selector) {
        final Region selected;
        try {
            selected = selector.getRegion();
        } catch (IncompleteRegionException e) {
            throw new IllegalArgumentException("Incomplete WorldEdit selection", e);
        }
        RegionCodec.type(selected);
        cancelPendingReset();
        region = RegionCodec.snapshot(selected);
    }

    @Override
    public void redefineRegion(RegionSelector selector) { defineRegion(selector); }

    @Override
    public BoundedReset prepareReset(long maximumBlocks) {
        var composition = compositionManager.getUpcoming().orElseThrow(() -> new IllegalStateException("No composition"));
        if (!composition.canReset()) throw new IllegalStateException("Composition has no positive-weight blocks");
        return new BoundedReset(region, composition.getRandomPattern(), maximumBlocks);
    }

    @Override
    public boolean contains(org.bukkit.Location location) {
        return location != null && location.getWorld() != null && region.getWorld() != null
                && region.getWorld().getName().equals(location.getWorld().getName())
                && region.contains(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    @Override
    public RegionType getType() { return RegionType.SELECTION; }

    @Override
    public long getVolume() { return region.getVolume(); }

    @Override
    public void serialize(ConfigurationSection section) {
        super.serialize(section);
        section.set("world", region.getWorld().getName());
        section.set("selection-type", RegionCodec.type(region));
        serializeRegion(section.createSection("selection"));
        serializeCompositions(section.createSection("compositions"));
    }

    public void serializeRegion(ConfigurationSection section) { RegionCodec.write(region, section); }

    public static SelectionRegion deserialize(ConfigurationSection section) throws DeserializationException {
        try {
            String name = section.getString("name");
            if (name == null) throw new IllegalArgumentException("Missing region name");
            var world = Bukkit.getWorld(section.getString("world", ""));
            if (world == null) throw new IllegalArgumentException("World is missing or unloaded");
            Region shape = RegionCodec.read(BukkitAdapter.adapt(world), section.getString("selection-type"),
                    section.getConfigurationSection("selection"));
            SelectionRegion result = new SelectionRegion(name, SelectionType.NONE, shape);
            result.setChance(section.getDouble("chance", 100));
            result.deserializeCommon(section);
            return result;
        } catch (RuntimeException e) {
            throw new DeserializationException("Invalid selection region: " + e.getMessage(), e);
        }
    }

    @Override
    public SelectionRegion clone() {
        SelectionRegion clone = (SelectionRegion) super.clone();
        clone.region = RegionCodec.snapshot(region);
        return clone;
    }

    public enum SelectionType { NONE, CUBOID, CYLINDER, ELLIPSOID, SPHERE, POLYGONAL2D, CONVEXPOLYHEDRAL }
}
