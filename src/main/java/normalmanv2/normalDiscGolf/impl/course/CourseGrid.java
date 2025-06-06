package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.obstacle.generator.ObstacleGenerator;
import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.course.tile.Tile;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.Location;
import org.bukkit.World;
import org.normal.impl.registry.RegistryImpl;

import java.util.*;

public class CourseGrid {

    private final Tile[][] grid;
    private final int width, depth;
    private final List<TileTypes> tileTypes;
    private final Random random;
    private final int numHoles;
    private final Map<Integer, Location> teeLocations;
    private final Map<Integer, Location> holeLocations;
    private final Map<Integer, Integer> holePars;

    private ObstacleGenerator obstacleGenerator;

    private static final int TILE_SIZE = 16;

    public CourseGrid(int width, int depth, List<TileTypes> tileTypes, int numHoles) {
        this.width = width;
        this.depth = depth;
        this.tileTypes = tileTypes;
        this.grid = new Tile[width][depth];
        this.random = new Random();
        this.numHoles = numHoles;
        this.teeLocations = new HashMap<>();
        this.holeLocations = new HashMap<>();
        this.holePars = new HashMap<>();
    }

    private void initializeSuperposition() {
        int y = 64;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Location tileLocation = new Location(null, x * TILE_SIZE, y, z * TILE_SIZE);
                grid[x][z] = new Tile(tileTypes, tileLocation);
            }
        }
    }

    public void generate(Division division) {
        this.initializeSuperposition();
        generateFairways();
        generateObstaclesOutsideFairways();

        while (!isFullyCollapsed()) {
            Tile tileToCollapse = selectTileWithLowestEntropy();
            collapseTile(tileToCollapse);
            propagateConstraints(tileToCollapse);
        }

        this.obstacleGenerator = new ObstacleGenerator(this, division);
    }

    public void generateObstacles(World world) {
        this.obstacleGenerator.generateObstacles(world);
    }

    private void generateFairways() {
        int attempts = 0;
        int maxAttempts = 1000;

        for (int holeId = 0; holeId < numHoles; holeId++) {
            boolean success = false;

            while (!success && attempts++ < maxAttempts) {
                int teeX = random.nextInt(width);
                int teeZ = 0;

                int pinX = random.nextInt(width);
                int pinZ = depth - 1;

                if (!isValidStartEnd(teeX, teeZ, pinX, pinZ)) continue;

                Tile teeTile = grid[teeX][teeZ];
                Tile pinTile = grid[pinX][pinZ];

                List<Tile> path = findPath(teeTile, pinTile);
                if (path == null || path.isEmpty()) continue;

                teeTile.collapseTo(TileTypes.TEE);
                teeLocations.put(holeId, teeTile.getLocation());

                pinTile.collapseTo(TileTypes.PIN);
                holeLocations.put(holeId, pinTile.getLocation());

                for (Tile tile : path) {
                    if (!tile.isCollapsed()) {
                        tile.collapseTo(TileTypes.FAIRWAY);
                    }
                }

                success = true;
            }
        }

        propagateFairwayConstraints();
    }

    private List<Tile> findPath(Tile start, Tile goal) {
        class Node {
            final Tile tile;
            final Node parent;
            final int g;
            final int h;

            Node(Tile tile, Node parent, int g, int h) {
                this.tile = tile;
                this.parent = parent;
                this.g = g;
                this.h = h;
            }

            int f() {
                return g + h;
            }
        }

        Comparator<Node> comparator = Comparator.comparingInt(Node::f);
        PriorityQueue<Node> openSet = new PriorityQueue<>(comparator);
        Set<Tile> closedSet = new HashSet<>();

        Node startNode = new Node(start, null, 0, heuristic(start, goal));
        openSet.add(startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.tile == goal) {
                List<Tile> path = new ArrayList<>();
                while (current != null) {
                    path.add(current.tile);
                    current = current.parent;
                }
                Collections.reverse(path);
                return path;
            }

            closedSet.add(current.tile);

            int x = (int) (current.tile.getLocation().getX() / TILE_SIZE);
            int z = (int) (current.tile.getLocation().getZ() / TILE_SIZE);

            for (int[] dir : DIRECTIONS) {
                int nx = x + dir[0];
                int nz = z + dir[1];
                if (!isInBounds(nx, nz)) continue;

                Tile neighbor = grid[nx][nz];
                if (closedSet.contains(neighbor)) continue;

                int cost = current.g + 1;
                int heuristic = heuristic(neighbor, goal);
                Node neighborNode = new Node(neighbor, current, cost, heuristic);
                openSet.add(neighborNode);
            }
        }

        return null;
    }

    private int heuristic(Tile a, Tile b) {
        int ax = (int) (a.getLocation().getX() / TILE_SIZE);
        int az = (int) (a.getLocation().getZ() / TILE_SIZE);
        int bx = (int) (b.getLocation().getX() / TILE_SIZE);
        int bz = (int) (b.getLocation().getZ() / TILE_SIZE);
        return Math.abs(ax - bx) + Math.abs(az - bz);
    }

    private boolean isValidStartEnd(int teeX, int teeZ, int pinX, int pinZ) {
        Tile tee = grid[teeX][teeZ];
        Tile pin = grid[pinX][pinZ];

        return !tee.isCollapsed() && !pin.isCollapsed() && !tee.equals(pin);
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

    private void generateObstaclesOutsideFairways() {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Tile tile = grid[x][z];
                if (tile.isCollapsed()) continue;

                if (random.nextDouble() < 0.25) {
                    TileTypes type = random.nextBoolean() ? TileTypes.OBSTACLE : TileTypes.WATER;
                    tile.collapseTo(type);
                } else {
                    tile.collapseTo(TileTypes.OUT_OF_BOUNDS);
                }
            }
        }
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

    private boolean isInBounds(int x, int z) {
        return x >= 0 && x < width && z >= 0 && z < depth;
    }

    private static final int[][] DIRECTIONS = {
            {0, 1}, {1, 0}, {0, -1}, {-1, 0}
    };

    public Map<Integer, Location> getTeeLocations() {
        return Collections.unmodifiableMap(this.teeLocations);
    }

    public Map<Integer, Location> getHoleLocations() {
        return Collections.unmodifiableMap(this.holeLocations);
    }

    public Map<Integer, Integer> getHolePars() {
        return Collections.unmodifiableMap(this.holePars);
    }

    public int getWidth() {
        return this.width;
    }

    public int getDepth() {
        return this.depth;
    }

    public int getNumHoles() {
        return this.numHoles;
    }

    public Tile getTile(int x, int z) {
        return this.grid[x][z];
    }
}
