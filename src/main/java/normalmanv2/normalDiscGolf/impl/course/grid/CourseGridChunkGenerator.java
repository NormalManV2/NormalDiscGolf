package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import java.util.Random;

/** Builds the terrain columns used by both collision rules and the generated world. */
public final class CourseGridChunkGenerator extends ChunkGenerator {
    private final CourseGrid grid;
    public CourseGridChunkGenerator(CourseGrid grid) { this.grid = grid; }
    @Override public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
            if (grid.isThemed()) {
                var column = grid.terrainColumn(wx, wz);
                writeThemedColumn(data, x, z, column);
                if (grid.terrain() == CourseTerrain.DESERT) {
                    var arch = grid.archColumn(wx, wz, column.height());
                    if (arch != null) for (int y = arch.bottom(); y <= arch.top(); y++)
                        data.setBlock(x, y, z, canyonLayer(y));
                }
                continue;
            }
            int y = grid.surfaceHeight(wx, wz);
            var type = grid.surfaceType(wx, wz);
            double route = grid.routeDistance(wx + .5, wz + .5);
            double edge = Math.sin(wx * .37) * .8 + Math.cos(wz * .29) * .8;
            double patch = Math.sin(wx / 5.7) * .55 + Math.cos(wz / 6.3) * .45
                    + Math.sin((wx + wz) / 14.0) * .3;
            Material surface = route > 8 + edge && patch > .65 ? Material.PODZOL
                    : route > 10 + edge && patch < -.8 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            if (type == TileTypes.WATER) {
                // Recess the water below the sand rim so it stays inside the basin.
                y--;
                surface = Material.WATER;
            }
            if (type == TileTypes.SAND) surface = Material.SAND;
            if (type == TileTypes.TEE) surface = Material.GRASS_BLOCK;
            if (type == TileTypes.PIN
                    && Math.hypot(Math.floorMod(wx, 16) - 8, Math.floorMod(wz, 16) - 8) < 5 + edge * .4)
                surface = Material.MOSS_BLOCK;
            data.setBlock(x, y - 6, z, Material.BEDROCK);
            for (int ground = y - 5; ground < y; ground++) {
                Material base = ground < y - 2 ? Material.STONE : Material.DIRT;
                if (type == TileTypes.WATER && ground == y - 1) base = Material.CLAY;
                data.setBlock(x, ground, z, base);
            }
            data.setBlock(x, y, z, surface);
        }
    }

    private void writeThemedColumn(ChunkData data, int x, int z, CourseGrid.TerrainColumn column) {
        if (column.depth() == 0) return;
        var terrain = grid.terrain();
        int top = column.height(), dry = top - column.liquidDepth();
        for (int y = Math.max(data.getMinHeight(), top - column.depth()); y < dry; y++) {
            Material stone = terrain == CourseTerrain.DESERT && y < dry - 1
                    ? canyonLayer(y) : y < dry - 3 ? terrain.rock : terrain.fill;
            data.setBlock(x, y, z, stone);
        }
        data.setBlock(x, dry, z, column.liquidDepth() == 0 ? column.surface() : terrain.fill);
        for (int y = dry + 1; y <= top; y++) data.setBlock(x, y, z, column.surface());
    }

    private static Material canyonLayer(int y) {
        return switch (Math.floorMod(y, 13)) {
            case 0, 1 -> Material.TERRACOTTA;
            case 2, 3, 4 -> Material.RED_SANDSTONE;
            case 8, 9 -> Material.ORANGE_TERRACOTTA;
            default -> Material.SANDSTONE;
        };
    }

    @Override public Location getFixedSpawnLocation(World world, Random random) {
        if (!grid.isThemed()) return super.getFixedSpawnLocation(world, random);
        var tee = grid.getTeePoints().get(0);
        return new Location(world, tee.x() + .5, tee.y(), tee.z() + .5);
    }
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
