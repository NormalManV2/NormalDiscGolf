package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import java.util.Random;

/** Natural parkland terrain with continuous elevation and blended fairway edges. */
public final class CourseGridChunkGenerator extends ChunkGenerator {
    private final CourseGrid grid;
    public CourseGridChunkGenerator(CourseGrid grid) { this.grid = grid; }
    @Override public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
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
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
