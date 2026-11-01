package me.catalysmrl.catamines.mine.components.region.impl;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.function.pattern.AbstractPattern;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.World;
import me.catalysmrl.catamines.api.serialization.DeserializationException;
import me.catalysmrl.catamines.mine.components.region.AbstractCataMineRegion;
import me.catalysmrl.catamines.utils.worldedit.VectorParser;
import me.catalysmrl.catamines.utils.worldedit.WorldEditUtils;
import me.catalysmrl.catamines.utils.worldedit.BoundedReset;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

public class SchematicRegion extends AbstractCataMineRegion implements Cloneable {

    private String schematicName;
    private World world;
    private BlockVector3 location;
    private Clipboard clipboard;

    public SchematicRegion(String name, String schematicName, RegionSelector selector) {
        super(name);
        define(schematicName, selector);
    }

    public SchematicRegion(String name, String schematicName, World world, BlockVector3 location) {
        super(name);
        define(schematicName, world, location);
    }

    public void define(String schematicName, World world, BlockVector3 location) {
        Clipboard loaded = WorldEditUtils.loadSchematic(schematicName);
        cancelPendingReset();
        this.schematicName = schematicName;
        this.world = world;
        this.location = location;
        this.clipboard = loaded;
    }

    public void define(String schematicName, RegionSelector selector) {
        try {
            define(schematicName, selector.getWorld(), selector.getPrimaryPosition());
        } catch (com.sk89q.worldedit.IncompleteRegionException e) {
            throw new IllegalArgumentException("Incomplete schematic destination", e);
        }
    }

    @Override
    public void redefineRegion(RegionSelector selector) {
        try {
            BlockVector3 destination = selector.getPrimaryPosition();
            cancelPendingReset();
            this.world = selector.getWorld();
            this.location = destination;
        } catch (com.sk89q.worldedit.IncompleteRegionException e) {
            throw new IllegalArgumentException("Incomplete schematic destination", e);
        }
    }

    @Override
    public BoundedReset prepareReset(long maximumBlocks) {
        return WorldEditUtils.schematicReset(clipboard, world, location, maximumBlocks);
    }

    @Override
    public boolean contains(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null || world == null || !world.getName().equals(loc.getWorld().getName())) return false;
        if (clipboard == null || clipboard.getRegion() == null) return false;
        
        Region r = clipboard.getRegion();
        BlockVector3 offset = location.subtract(clipboard.getOrigin());
        return r.contains(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).subtract(offset));
    }

    @Override
    public RegionType getType() {
        return RegionType.SCHEMATIC;
    }

    @Override
    public long getVolume() {
        return clipboard.getRegion().getVolume();
    }

    @Override
    public void serialize(ConfigurationSection section) {
        super.serialize(section);
        section.set("schematic-name", schematicName);
        section.set("world", world.getName());
        section.set("location", location.toParserString());
        serializeCompositions(section.createSection("compositions"));
    }

    public static SchematicRegion deserialize(ConfigurationSection section) throws DeserializationException {

        String name = section.getString("name");
        if (name == null)
            throw new DeserializationException("No name specified");

        double chance = section.getDouble("chance", 0d);

        String schematicName = section.getString("schematic-name");
        if (schematicName == null)
            throw new DeserializationException("No schematic specified");

        String worldName = section.getString("world");
        if (worldName == null)
            throw new DeserializationException("No world specified");

        org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null)
            throw new DeserializationException("World " + worldName + " not found");

        World worldEditWorld = BukkitAdapter.adapt(bukkitWorld);

        BlockVector3 location;
        try {
            location = VectorParser.asBlockVector3(section.getString("location"));
        } catch (IllegalArgumentException exception) {
            throw new DeserializationException("Invalid location", exception);
        }

        SchematicRegion schematicRegion = new SchematicRegion(name, schematicName, worldEditWorld, location);
        schematicRegion.setChance(chance);
        schematicRegion.deserializeCommon(section);

        return schematicRegion;
    }

    @Override
    public SchematicRegion clone() {
        SchematicRegion clone = (SchematicRegion) super.clone();
        // clipboard is immutable after load — safe to share
        return clone;
    }

    @Override
    public String toString() {
        return "SchematicRegion{" +
                "schemName='" + schematicName + '\'' +
                ", location=" + location +
                ", clipboard=" + clipboard +
                "} " + super.toString();
    }
}
