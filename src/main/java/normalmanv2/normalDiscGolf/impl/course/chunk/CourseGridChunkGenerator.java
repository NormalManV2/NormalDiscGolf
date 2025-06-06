package normalmanv2.normalDiscGolf.impl.course.chunk;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.Tile;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public class CourseGridChunkGenerator extends ChunkGenerator {

    private final CourseGrid grid;
    private final Division division;

    public CourseGridChunkGenerator(CourseGrid grid, Division division) {
        this.grid = grid;
        this.division = division;
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int baseY = Constants.DEFAULT_TILE_Y;
        int tileSize = Constants.DEFAULT_TILE_SIZE;

        int minTileX = 0;
        int minTileZ = 0;
        int maxTileX = grid.getWidth() - 1;
        int maxTileZ = grid.getDepth() - 1;

        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;

        int tileX = Math.floorDiv(worldX, tileSize);
        int tileZ = Math.floorDiv(worldZ, tileSize);

        if (tileX < minTileX || tileX > maxTileX || tileZ < minTileZ || tileZ > maxTileZ) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 256; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }
            }
            return;
        }

        this.grid.generate(this.division);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int blockX = (chunkX << 4) + x;
                int blockZ = (chunkZ << 4) + z;

                int tx = Math.floorDiv(blockX, tileSize);
                int tz = Math.floorDiv(blockZ, tileSize);

                Tile tile = grid.getTile(tx, tz);
                if (tile == null) continue;

                TileTypes type = tile.getCollapsedState();
                Material blockType = switch (type) {
                    case FAIRWAY -> Material.GRASS_BLOCK;
                    case OBSTACLE -> Material.COBBLESTONE;
                    case WATER -> Material.WATER;
                    case TEE -> Material.LIME_CONCRETE;
                    case PIN -> Material.RED_CONCRETE;
                    case OUT_OF_BOUNDS -> Material.BEDROCK;
                };

                for (int y = 0; y <= baseY; y++) {
                    chunkData.setBlock(x, y, z, Material.DIRT);
                }
                chunkData.setBlock(x, baseY + 1, z, blockType);
            }
        }
    }
}
