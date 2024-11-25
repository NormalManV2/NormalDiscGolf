package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleGenerator;
import normalmanv2.normalDiscGolf.impl.registry.ObstacleRegistry;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class CourseGrid {

    private final Tile[][] grid;
    private final int width, depth;
    private final List<TileTypes> tileTypes;
    private final Random random;

    private static final int TILE_SIZE = 16;

    public CourseGrid(int width, int depth, List<TileTypes> tileTypes, World world) {
        this.width = width;
        this.depth = depth;
        this.tileTypes = tileTypes;
        this.grid = new Tile[width][depth];
        this.random = new Random();

        initializeSuperposition(world);
    }

    public void printCourse() {
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                System.out.print(grid[x][z].getCollapsedState() == TileTypes.FAIRWAY ? "F" :
                        grid[x][z].getCollapsedState() == TileTypes.OBSTACLE ? "O" :
                                grid[x][z].getCollapsedState() == TileTypes.WATER ? "W" :
                                        grid[x][z].getCollapsedState() == TileTypes.PIN ? "P" :
                                                grid[x][z].getCollapsedState() == TileTypes.TEE ? "T" :
                                                        grid[x][z].getCollapsedState() == TileTypes.OUT_OF_BOUNDS ? "X" :
                                        " ");
            }
            System.out.println();

        }
    }

    private void initializeSuperposition(World world) {
        int y = 64;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Location tileLocation = new Location(world, x * TILE_SIZE, y, z * TILE_SIZE);
                grid[x][z] = new Tile(tileTypes, tileLocation);
            }
        }
    }

    public void generate(ObstacleRegistry obstacleRegistry, CourseDifficulty difficulty) {
        generateFairwayPath();

        while (!isFullyCollapsed()) {
            Tile tileToCollapse = selectTileWithLowestEntropy();
            collapseTile(tileToCollapse);
            propagateConstraints(tileToCollapse);
        }

        ObstacleGenerator obstacleGenerator = new ObstacleGenerator(this, obstacleRegistry, difficulty);
        obstacleGenerator.generateObstacles();
    }

    private void generateFairwayPath() {
        // Start at a random spot on the top row
        int x = random.nextInt(width);
        int z = 0;

        // Set the starting tile to TEE
        Tile teeTile = grid[x][z];
        teeTile.collapseTo(TileTypes.TEE);

        // Randomly select an x coordinate for the PIN tile along the bottom row
        int pinX = random.nextInt(width);
        int pinZ = depth - 1;

        // Set the ending tile to PIN
        Tile pinTile = grid[pinX][pinZ];
        pinTile.collapseTo(TileTypes.PIN);

        // Create the fairway path from TEE to PIN
        int currentX = x;
        int currentZ = z;

        while (currentZ < depth - 1) {
            // Move one step forward in the z direction
            currentZ++;

            // Ensure the PIN tile is not overwritten
            if (currentX == pinX && currentZ == pinZ) {
                break;
            }

            // Randomly change the fairway pathing direction
            int directionOffset = random.nextInt(3) - 1; // Move left (-1), straight (0), or right (+1)

            // Smoother curvature by adding influence from the previous direction
            if (random.nextInt(100) < 40) { // 40% chance to continue in the same direction
                directionOffset += (Integer.compare(pinX, currentX));
            }

            // Clamp directionOffset to -1, 0, or 1
            directionOffset = Math.max(-1, Math.min(1, directionOffset));
            currentX += directionOffset;

            // Clamp x to be within bounds
            currentX = Math.max(0, Math.min(width - 1, currentX));

            // Collapse the tile to fairway if it's not TEE or PIN
            Tile currentTile = grid[currentX][currentZ];
            if (currentTile.getCollapsedState() != TileTypes.TEE && currentTile.getCollapsedState() != TileTypes.PIN) {
                currentTile.collapseTo(TileTypes.FAIRWAY);
            }

            // Random chance to generate water or obstacles near the fairway
            if (random.nextInt(10) < 0.5) { // 5% chance
                setObstacleOrWaterNearby(currentX, currentZ);
            }
        }

        // Propagate constraints along the generated path
        propagateFairwayConstraints();
    }

    private void propagateFairwayConstraints() {
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                Tile tile = grid[x][z];
                if (tile.isCollapsed() && tile.getCollapsedState() == TileTypes.FAIRWAY) {
                    propagateConstraints(tile);
                }
            }
        }
    }

    private void setObstacleOrWaterNearby(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;

                int nx = x + dx;
                int nz = z + dz;

                if (isInBounds(nx, nz)) {
                    Tile tile = grid[nx][nz];
                    if (tile.isCollapsed() && (tile.getCollapsedState() == TileTypes.TEE || tile.getCollapsedState() == TileTypes.PIN || tile.getCollapsedState() == TileTypes.OUT_OF_BOUNDS)) {
                        continue;
                    }

                    if (!tile.isCollapsed()) {
                        TileTypes surroundingType = random.nextBoolean() ? TileTypes.OBSTACLE : TileTypes.WATER;
                        tile.collapseTo(surroundingType);
                    }
                }
            }
        }
    }

    public int getWidth() {
        return this.width;
    }

    public int getDepth() {
        return this.depth;
    }

    public Tile getTile(int x, int z) {
        return this.grid[x][z];
    }

    private boolean isFullyCollapsed() {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (!grid[x][z].isCollapsed()) {
                    return false;
                }
            }
        }
        return true;
    }

    private Tile selectTileWithLowestEntropy() {
        Tile lowestEntropyTile = null;
        int lowestEntropy = Integer.MAX_VALUE;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Tile tile = grid[x][z];
                if (!tile.isCollapsed() && tile.getEntropy() < lowestEntropy) {
                    lowestEntropy = tile.getEntropy();
                    lowestEntropyTile = tile;
                }
            }
        }

        return lowestEntropyTile;
    }

    private void collapseTile(Tile tile) {
        if (!tile.isCollapsed()) {
            tile.collapse(random);
        }
    }

    private void propagateConstraints(Tile tile) {
        Queue<Tile> queue = new LinkedList<>();
        queue.add(tile);

        while (!queue.isEmpty()) {
            Tile current = queue.poll();
            int x = (int) (current.getLocation().getX() / TILE_SIZE);
            int z = (int) (current.getLocation().getZ() / TILE_SIZE);

            for (int[] direction : DIRECTIONS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (isInBounds(nx, nz)) {
                    Tile neighbor = grid[nx][nz];
                    if (!neighbor.isCollapsed()) {
                        return;
                    }
                    boolean updated = neighbor.updatePossibleStatesBasedOn(current);
                    if (updated) {
                        queue.add(neighbor);
                    }
                }
            }
        }
    }

    public boolean isInBounds(int x, int z) {
        return x >= 0 && x < width && z >= 0 && z < depth;
    }

    private static final int[][] DIRECTIONS = {
            {0, 1}, {1, 0}, {0, -1}, {-1, 0}
    };
}
