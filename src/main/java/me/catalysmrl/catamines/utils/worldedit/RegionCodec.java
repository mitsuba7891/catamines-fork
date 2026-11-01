package me.catalysmrl.catamines.utils.worldedit;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.*;
import com.sk89q.worldedit.world.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Objects;

/** Persists exact shapes, never replacing a polygon/sphere with its bounding cuboid. */
public final class RegionCodec {
    private RegionCodec() {}

    public static Region snapshot(Region original) {
        Region copy = original.clone();
        // WorldEdit can retain a removed hull face in its contains() cache while
        // adding convex vertices. Rebuilding only the private copy clears that cache.
        if (copy instanceof ConvexPolyhedralRegion hull) hull.shift(BlockVector3.ZERO);
        return copy;
    }

    public static String type(Region region) {
        if (region instanceof CuboidRegion) return "CUBOID";
        if (region instanceof CylinderRegion) return "CYLINDER";
        if (region instanceof EllipsoidRegion) return "ELLIPSOID";
        if (region instanceof Polygonal2DRegion) return "POLYGONAL2D";
        if (region instanceof ConvexPolyhedralRegion) return "CONVEXPOLYHEDRAL";
        throw new IllegalArgumentException("Unsupported region shape: " + region.getClass().getSimpleName());
    }

    public static void write(Region region, ConfigurationSection section) {
        String type = type(region);
        section.set("type", type);
        switch (type) {
            case "CUBOID" -> {
                section.set("min", region.getMinimumPoint().toParserString());
                section.set("max", region.getMaximumPoint().toParserString());
            }
            case "CYLINDER" -> {
                var cylinder = (CylinderRegion) region;
                section.set("center", cylinder.getCenter().toParserString());
                section.set("radius", cylinder.getRadius().toParserString());
                section.set("minY", cylinder.getMinimumY());
                section.set("maxY", cylinder.getMaximumY());
            }
            case "ELLIPSOID" -> {
                var ellipsoid = (EllipsoidRegion) region;
                section.set("center", ellipsoid.getCenter().toParserString());
                section.set("radius", ellipsoid.getRadius().toParserString());
            }
            case "POLYGONAL2D" -> {
                var polygon = (Polygonal2DRegion) region;
                section.set("points", polygon.getPoints().stream().map(BlockVector2::toParserString).toList());
                section.set("minY", polygon.getMinimumY());
                section.set("maxY", polygon.getMaximumY());
            }
            case "CONVEXPOLYHEDRAL" -> section.set("vertices", ((ConvexPolyhedralRegion) region).getVertices().stream()
                    .map(BlockVector3::toParserString).toList());
            default -> throw new IllegalArgumentException("Unknown shape");
        }
    }

    public static Region read(World world, String type, ConfigurationSection section) {
        Objects.requireNonNull(world, "Missing world");
        Objects.requireNonNull(section, "Missing region geometry");
        if (type == null) throw new IllegalArgumentException("Missing region type");
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CUBOID" -> new CuboidRegion(world, VectorParser.asBlockVector3(section.getString("min")),
                    VectorParser.asBlockVector3(section.getString("max")));
            case "CYLINDER" -> new CylinderRegion(world, VectorParser.asBlockVector3(section.getString("center")),
                    VectorParser.asVector2(section.getString("radius")), section.getInt("minY"), section.getInt("maxY"));
            case "ELLIPSOID", "SPHERE" -> new EllipsoidRegion(world, VectorParser.asBlockVector3(section.getString("center")),
                    VectorParser.asVector3(section.getString("radius")));
            case "POLYGONAL2D" -> new Polygonal2DRegion(world, section.getStringList("points").stream()
                    .map(VectorParser::asBlockVector2).toList(), section.getInt("minY"), section.getInt("maxY"));
            case "CONVEXPOLYHEDRAL" -> {
                var region = new ConvexPolyhedralRegion(world);
                section.getStringList("vertices").stream().map(VectorParser::asBlockVector3).forEach(region::addVertex);
                if (!region.isDefined()) throw new IllegalArgumentException("Incomplete convex region");
                region.shift(BlockVector3.ZERO);
                yield region;
            }
            default -> throw new IllegalArgumentException("Unsupported region type: " + type);
        };
    }
}
