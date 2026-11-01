package me.catalysmrl.catamines;

import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.*;
import com.sk89q.worldedit.regions.*;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import me.catalysmrl.catamines.utils.worldedit.BoundedReset;
import me.catalysmrl.catamines.utils.worldedit.RegionCodec;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeometrySafetyTest {
    private World world() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("safety-test");
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(319);
        when(world.getMinimumPoint()).thenReturn(BlockVector3.at(-30_000_000, -64, -30_000_000));
        when(world.getMaximumPoint()).thenReturn(BlockVector3.at(30_000_000, 319, 30_000_000));
        return world;
    }
    private Pattern stone() {
        Pattern pattern = mock(Pattern.class);
        BaseBlock block = mock(BaseBlock.class);
        BlockType type = mock(BlockType.class);
        when(type.id()).thenReturn("minecraft:stone");
        when(block.getBlockType()).thenReturn(type);
        when(pattern.applyBlock(any())).thenReturn(block);
        return pattern;
    }
    private Region shape(String type) {
        World world = world();
        return switch (type) {
            case "CUBOID" -> new CuboidRegion(world, BlockVector3.at(-5, 60, -5), BlockVector3.at(5, 65, 5));
            case "CYLINDER" -> new CylinderRegion(world, BlockVector3.at(0, 60, 0), Vector2.at(5, 3), 60, 65);
            case "ELLIPSOID" -> new EllipsoidRegion(world, BlockVector3.at(0, 64, 0), Vector3.at(4, 3, 2));
            case "POLYGONAL2D" -> new Polygonal2DRegion(world, List.of(BlockVector2.at(0, 0), BlockVector2.at(5, 0),
                    BlockVector2.at(5, 2), BlockVector2.at(2, 2), BlockVector2.at(2, 5), BlockVector2.at(0, 5)), 60, 65);
            case "CONVEXPOLYHEDRAL" -> {
                var region = new ConvexPolyhedralRegion(world);
                List.of(BlockVector3.at(0, 60, 0), BlockVector3.at(5, 60, 0), BlockVector3.at(0, 60, 5),
                        BlockVector3.at(0, 65, 0)).forEach(region::addVertex);
                region.shift(BlockVector3.ZERO);
                yield region;
            }
            default -> throw new IllegalArgumentException(type);
        };
    }

    @ParameterizedTest @ValueSource(strings = {"CUBOID", "CYLINDER", "ELLIPSOID", "POLYGONAL2D", "CONVEXPOLYHEDRAL"})
    void roundTripAndEveryWriteStayInsideExactShape(String type) throws Exception {
        Region original = shape(type);
        YamlConfiguration yaml = new YamlConfiguration();
        RegionCodec.write(original, yaml);
        Region restored = RegionCodec.read(original.getWorld(), type, yaml);
        var reset = new BoundedReset(restored, stone(), 100_000);
        Set<BlockVector3> written = new HashSet<>();
        while (!reset.isComplete()) {
            assertTrue(reset.applyBatch(7, (point, block) -> {
                assertTrue(original.contains(point), () -> "Write outside " + type + " at " + point + " saved=" + yaml.saveToString()
                        + (original instanceof ConvexPolyhedralRegion hull ? " original=" + hull.getTriangles() + " restored=" + ((ConvexPolyhedralRegion) restored).getTriangles() : ""));
                assertTrue(written.add(point), "Duplicate write");
            }) <= 7);
        }
        Set<BlockVector3> expected = new HashSet<>();
        original.forEach(point -> expected.add(BlockVector3.at(point.x(), point.y(), point.z())));
        assertEquals(expected, written);
        assertEquals(written.size(), reset.getNonAirBlocks());
        if (type.equals("POLYGONAL2D")) assertEquals(65, yaml.getInt("maxY"));
    }

    @Test void laterSelectionChangesCannotMoveQueuedWrites() throws Exception {
        Region region = shape("CUBOID");
        Region approved = region.clone();
        var reset = new BoundedReset(region, stone(), 100_000);
        region.shift(BlockVector3.at(100, 0, 100));
        while (!reset.isComplete()) reset.applyBatch(100, (point, block) -> assertTrue(approved.contains(point)));
    }

    @Test void injectedOutOfBoundsIteratorIsRejectedBeforeWrite() {
        Region corrupt = mock(Region.class);
        when(corrupt.clone()).thenReturn(corrupt);
        World world = world();
        when(corrupt.getWorld()).thenReturn(world);
        when(corrupt.getVolume()).thenReturn(1L);
        when(corrupt.getMinimumPoint()).thenReturn(BlockVector3.at(0, 60, 0));
        when(corrupt.getMaximumPoint()).thenReturn(BlockVector3.at(0, 60, 0));
        when(corrupt.iterator()).thenReturn(List.of(BlockVector3.at(9999, 60, 9999)).iterator());
        when(corrupt.contains(any())).thenReturn(false);
        var reset = new BoundedReset(corrupt, stone(), 100);
        assertThrows(IllegalStateException.class, () -> reset.applyBatch(10, (point, block) -> fail("Unsafe write")));
        assertTrue(reset.isCancelled());
    }

    @Test void oversizedAndUnknownShapesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedReset(shape("CUBOID"), stone(), 10));
        assertThrows(IllegalArgumentException.class, () -> RegionCodec.read(world(), "UNKNOWN", new YamlConfiguration()));
        assertThrows(IllegalArgumentException.class, () -> RegionCodec.type(new NullRegion()));
    }

    @Test void cancellationStopsAllFurtherWrites() throws Exception {
        var reset = new BoundedReset(shape("CUBOID"), stone(), 100_000);
        reset.applyBatch(2, (point, block) -> {});
        reset.cancel();
        assertEquals(0, reset.applyBatch(100, (point, block) -> fail("Cancelled write")));
        assertFalse(reset.isComplete());
    }

    @Test void geometryCopyClearsStaleConvexFaceCache() {
        var hull = new ConvexPolyhedralRegion(world());
        List.of(BlockVector3.at(0, 60, 0), BlockVector3.at(5, 60, 0), BlockVector3.at(0, 60, 5),
                BlockVector3.at(0, 65, 0)).forEach(hull::addVertex);
        assertTrue(RegionCodec.snapshot(hull).contains(BlockVector3.at(1, 61, 1)));
    }

    @Test void outOfWorldHeightIsRejectedBeforeWriting() {
        Region corrupt = mock(Region.class); World world = world();
        when(corrupt.clone()).thenReturn(corrupt); when(corrupt.getWorld()).thenReturn(world);
        when(corrupt.getVolume()).thenReturn(2L);
        when(corrupt.getMinimumPoint()).thenReturn(BlockVector3.at(0, -65, 0));
        when(corrupt.getMaximumPoint()).thenReturn(BlockVector3.at(0, -64, 0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedReset(corrupt, stone(), 100));
    }

    @Test void randomizedCuboidsHonorBudgetAndNeverEscape() throws Exception {
        Random random = new Random(17);
        for (int trial = 0; trial < 100; trial++) {
            int x = random.nextInt(1000) - 500, z = random.nextInt(1000) - 500;
            Region region = new CuboidRegion(world(), BlockVector3.at(x, 60, z),
                    BlockVector3.at(x + random.nextInt(8), 60 + random.nextInt(8), z + random.nextInt(8)));
            var reset = new BoundedReset(region, stone(), 100_000);
            while (!reset.isComplete()) assertTrue(reset.applyBatch(3, (point, block) -> assertTrue(region.contains(point))) <= 3);
        }
    }
}
