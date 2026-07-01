package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.obstacle.generator.ObstacleGenerator;
import normalmanv2.normalDiscGolf.impl.course.tile.Tile;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class CourseGrid {

    private final Tile[][] grid;
    private final int width, depth;
    private final List<TileTypes> tileTypes;
    private final Random random;
    private final int numHoles;
    private final Map<Integer, GridPoint> teeLocations;
    private final Map<Integer, GridPoint> holeLocations;
    private final Map<Integer, Integer> holePars;
    private final Map<Integer, List<GridPoint>> holeRoutePoints;
    private ObstacleGenerator obstacleGenerator;

    private boolean generated;

    public record GridPoint(int x, int y, int z) {}

    private static final int TILE_SIZE = Constants.DEFAULT_TILE_SIZE;

    public CourseGrid(int width, int depth, List<TileTypes> tileTypes, int numHoles) {
        this(width, depth, tileTypes, numHoles, new Random().nextLong());
    }

    public CourseGrid(int width, int depth, List<TileTypes> tileTypes, int numHoles, long seed) {
        if (width <= 0) throw new IllegalArgumentException("Course grid width must be greater than zero");
        if (depth <= 0) throw new IllegalArgumentException("Course grid depth must be greater than zero");
        if (numHoles <= 0) throw new IllegalArgumentException("Course hole count must be greater than zero");
        if (tileTypes == null || tileTypes.isEmpty()) throw new IllegalArgumentException("Tile types must not be empty");

        this.width = width;
        this.depth = depth;
        this.tileTypes = List.copyOf(tileTypes);
        this.grid = new Tile[width][depth];
        this.random = new Random(seed);
        this.numHoles = numHoles;
        this.teeLocations = new HashMap<>();
        this.holeLocations = new HashMap<>();
        this.holePars = new HashMap<>();
        this.holeRoutePoints = new HashMap<>();
    }

    public Location toLocation(World world, GridPoint point) {
        return new Location(world, point.x, point.y, point.z);
    }

    private void initializeSuperposition() {
        int y = 64;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                GridPoint tileLocation = new GridPoint(x * TILE_SIZE, y, z * TILE_SIZE);
                grid[x][z] = new Tile(tileTypes, tileLocation);
            }
        }
    }

    public void generate(Division division) {
        if (this.generated) return;

        initializeSuperposition();

        placeTeesAndPins();

        for (int holeId = 0; holeId < numHoles; holeId++) {
            GridPoint teeLoc = teeLocations.get(holeId);
            GridPoint pinLoc = holeLocations.get(holeId);
            Tile teeTile = tileAtPoint(teeLoc);
            Tile pinTile = tileAtPoint(pinLoc);
            List<Tile> path = route(teeTile, pinTile);
            if (path == null || path.isEmpty()) {
                path = straightLine(teeTile, pinTile);
            }

            storeRoute(holeId, path);
            carveCorridor(path);
            assignPar(holeId, path);
        }

        reinforceFairwayRoutes();
        finalizeUnresolvedTiles();

        this.generated = true;
        this.obstacleGenerator = new ObstacleGenerator(this, division);
    }

    public void generateObstacles(World world) {
        if (this.obstacleGenerator == null) throw new IllegalStateException("Call generate() first");
        this.obstacleGenerator.generateObstacles(world);
    }

    /* ------------------------
       Tee/pin placement helpers
       ------------------------ */

    private void placeTeesAndPins() {
        int availablePlacementTiles = countPlacementTiles();
        if (numHoles * 2 > availablePlacementTiles) {
            throw new IllegalStateException("Grid is too small for " + numHoles + " unique tee/pin pairs");
        }

        Set<Long> reservedTiles = new HashSet<>();
        int minDistance = Math.max(6, Math.min(width, depth) / 4);
        int maxDistance = Math.max(minDistance + 8, (int) Math.round(Math.hypot(width, depth) * 0.65));

        for (int holeId = 0; holeId < numHoles; holeId++) {
            int[] tee = findTeeTile(holeId, reservedTiles);
            if (tee == null) {
                throw new IllegalStateException("Could not place tee for hole " + (holeId + 1));
            }
            reservedTiles.add(tileKey(tee[0], tee[1]));

            int[] pin = findPinForTee(tee, reservedTiles, minDistance, maxDistance);
            if (pin == null) {
                throw new IllegalStateException("Could not place pin for hole " + (holeId + 1));
            }
            reservedTiles.add(tileKey(pin[0], pin[1]));

            GridPoint teeLoc = centerPoint(tee[0], tee[1]);
            GridPoint pinLoc = centerPoint(pin[0], pin[1]);

            this.teeLocations.put(holeId, teeLoc);
            this.holeLocations.put(holeId, pinLoc);

            tileAtPoint(teeLoc).collapseTo(TileTypes.TEE);
            tileAtPoint(pinLoc).collapseTo(TileTypes.PIN);
        }
    }

    private int[] findTeeTile(int holeId, Set<Long> reservedTiles) {
        List<int[]> candidates = collectOpenPlacementTiles(reservedTiles);
        if (candidates.isEmpty()) return null;

        double lane = (holeId + 0.5) / numHoles;
        double targetZ = (depth - 1) * lane;
        double targetX = holeId % 2 == 0 ? (width - 1) * 0.18 : (width - 1) * 0.82;

        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] candidate : candidates) {
            double score = distance(candidate[0], candidate[1], targetX, targetZ);
            score += edgePenalty(candidate[0], candidate[1]) * 0.35;
            score += random.nextDouble() * 0.5;

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private int[] findPinForTee(int[] tee, Set<Long> reservedTiles, int minDistance, int maxDistance) {
        List<int[]> candidates = collectOpenPlacementTiles(reservedTiles);
        if (candidates.isEmpty()) return null;

        boolean teeOnLeft = tee[0] < width / 2.0;
        double targetX = teeOnLeft ? (width - 1) * 0.82 : (width - 1) * 0.18;
        double targetZ = (depth - 1) - tee[1];
        double idealDistance = (minDistance + maxDistance) / 2.0;

        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] candidate : candidates) {
            double holeDistance = distance(tee[0], tee[1], candidate[0], candidate[1]);
            double distancePenalty;
            if (holeDistance < minDistance) {
                distancePenalty = Math.pow(minDistance - holeDistance, 2) * 4.0;
            } else if (holeDistance > maxDistance) {
                distancePenalty = Math.pow(holeDistance - maxDistance, 2) * 2.0;
            } else {
                distancePenalty = Math.abs(holeDistance - idealDistance) * 0.18;
            }

            double sideScore = distance(candidate[0], candidate[1], targetX, targetZ) * 0.45;
            double diagonalBonus = Math.min(Math.abs(candidate[0] - tee[0]), Math.abs(candidate[1] - tee[1])) * 0.2;
            double score = distancePenalty + sideScore + edgePenalty(candidate[0], candidate[1]) * 0.2 - diagonalBonus;
            score += random.nextDouble() * 0.75;

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private List<int[]> collectOpenPlacementTiles(Set<Long> reservedTiles) {
        List<int[]> candidates = new ArrayList<>();
        for (int x = placementMinX(); x <= placementMaxX(); x++) {
            for (int z = placementMinZ(); z <= placementMaxZ(); z++) {
                if (!reservedTiles.contains(tileKey(x, z))) {
                    candidates.add(new int[]{x, z});
                }
            }
        }
        return candidates;
    }

    private int countPlacementTiles() {
        int count = 0;
        for (int x = placementMinX(); x <= placementMaxX(); x++) {
            for (int z = placementMinZ(); z <= placementMaxZ(); z++) {
                count++;
            }
        }
        return count;
    }

    private int placementMinX() {
        return width > 2 ? 1 : 0;
    }

    private int placementMaxX() {
        return width > 2 ? width - 2 : width - 1;
    }

    private int placementMinZ() {
        return depth > 2 ? 1 : 0;
    }

    private int placementMaxZ() {
        return depth > 2 ? depth - 2 : depth - 1;
    }

    private GridPoint centerPoint(int tileX, int tileZ) {
        return new GridPoint(tileX * TILE_SIZE + TILE_SIZE / 2, 64, tileZ * TILE_SIZE + TILE_SIZE / 2);
    }

    private long tileKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private double edgePenalty(int x, int z) {
        int nearestEdge = Math.min(Math.min(x, width - 1 - x), Math.min(z, depth - 1 - z));
        return Math.max(0, 3 - nearestEdge);
    }

    private double distance(double ax, double az, double bx, double bz) {
        return Math.hypot(ax - bx, az - bz);
    }

    /* ------------------------
       Routing (A* with turn cost)
       ------------------------ */

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    private List<Tile> route(Tile start, Tile goal) {
        int sx = (start.getGridPoint().x / TILE_SIZE);
        int sz = (start.getGridPoint().z / TILE_SIZE);
        int gx = (goal.getGridPoint().x / TILE_SIZE);
        int gz = (goal.getGridPoint().z / TILE_SIZE);

        record Node(int x, int z, int dir, double g, double f, Node parent) {
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));
        boolean[][][] closed = new boolean[width][depth][4];

        for (int d = 0; d < 4; d++) {
            double h = Math.abs(sx - gx) + Math.abs(sz - gz);
            open.add(new Node(sx, sz, d, 0, h, null));
        }

        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (closed[cur.x][cur.z][cur.dir]) continue;
            closed[cur.x][cur.z][cur.dir] = true;

            if (cur.x == gx && cur.z == gz) {
                List<Tile> path = new ArrayList<>();
                Node node = cur;
                while (node != null) {
                    path.add(grid[node.x][node.z]);
                    node = node.parent;
                }
                Collections.reverse(path);
                return path;
            }

            for (int nd = 0; nd < 4; nd++) {
                int nx = cur.x + DIRS[nd][0];
                int nz = cur.z + DIRS[nd][1];
                if (!isInBounds(nx, nz)) continue;

                double turnCost = (nd == cur.dir) ? 0.0 : 0.8;
                double hazardCost = tileHazardCost(nx, nz);
                double g = cur.g + 1.0 + turnCost + hazardCost;
                double h = Math.abs(nx - gx) + Math.abs(nz - gz);
                double f = g + h;
                if (!closed[nx][nz][nd]) open.add(new Node(nx, nz, nd, g, f, cur));
            }
        }
        return null;
    }

    private double tileHazardCost(int x, int z) {
        TileTypes type = grid[x][z].getCollapsedState();
        if (type == TileTypes.WATER) return 6.0;
        if (type == TileTypes.OUT_OF_BOUNDS) return 10.0;
        if (type == TileTypes.PIN || type == TileTypes.TEE) return 8.0;
        return 0.0;
    }

    private List<Tile> straightLine(Tile a, Tile b) {
        int ax = (a.getGridPoint().x / TILE_SIZE);
        int az = (a.getGridPoint().z / TILE_SIZE);
        int bx = (b.getGridPoint().x / TILE_SIZE);
        int bz = (b.getGridPoint().z / TILE_SIZE);
        List<Tile> path = new ArrayList<>();
        int x = ax, z = az;

        path.add(grid[x][z]);
        while (x != bx || z != bz) {
            if (Math.abs(bx - x) >= Math.abs(bz - z) && x != bx) {
                x += Integer.signum(bx - x);
            } else if (z != bz) {
                z += Integer.signum(bz - z);
            }
            path.add(grid[x][z]);
        }
        return path;
    }

    private void storeRoute(int holeId, List<Tile> path) {
        List<GridPoint> route = new ArrayList<>();
        for (Tile tile : path) {
            route.add(tile.getGridPoint());
        }
        this.holeRoutePoints.put(holeId, List.copyOf(route));
    }

    /* ------------------------
       Carving corridor (variable width)
       ------------------------ */

    private void carveCorridor(List<Tile> path) {
        if (path == null || path.isEmpty()) return;
        int n = path.size();
        for (int i = 0; i < n; i++) {
            Tile center = path.get(i);
            int cx = (center.getGridPoint().x / TILE_SIZE);
            int cz = (center.getGridPoint().z / TILE_SIZE);

            double t = (n == 1) ? 0.0 : (i / (double) (n - 1));
            double widen = landingZoneWeight(t);
            int fairwayR = (int) Math.round(lerp(2.0, 4.0, widen));
            int lightR = fairwayR + 2;
            int heavyR = fairwayR + 3;

            paintDisk(cx, cz, heavyR, TileTypes.HEAVY_ROUGH);
            paintDisk(cx, cz, lightR, TileTypes.LIGHT_ROUGH);
            paintDisk(cx, cz, fairwayR, TileTypes.FAIRWAY);
        }

        paintPathCenterline(path);
    }

    private double landingZoneWeight(double t) {
        double primaryLanding = Math.exp(-Math.pow((t - 0.45) / 0.18, 2));
        double approachLanding = Math.exp(-Math.pow((t - 0.78) / 0.16, 2));
        return Math.min(1.0, Math.max(primaryLanding, approachLanding));
    }

    private void paintDisk(int cx, int cz, int r, TileTypes setTo) {
        int r2 = r * r;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!isInBounds(x, z)) continue;
                int dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= r2) {
                    Tile tile = grid[x][z];
                    TileTypes current = tile.getCollapsedState();
                    if (current == TileTypes.TEE || current == TileTypes.PIN) {
                        continue;
                    }
                    if (setTo == TileTypes.HEAVY_ROUGH && (current == TileTypes.FAIRWAY || current == TileTypes.LIGHT_ROUGH)) {
                        continue;
                    }
                    if (setTo == TileTypes.LIGHT_ROUGH && current == TileTypes.FAIRWAY) {
                        continue;
                    }
                    tile.collapseTo(setTo);
                }
            }
        }
    }

    private void paintPathCenterline(List<Tile> path) {
        for (Tile tile : path) {
            TileTypes current = tile.getCollapsedState();
            if (current == TileTypes.TEE || current == TileTypes.PIN) {
                continue;
            }
            tile.collapseTo(TileTypes.FAIRWAY);
        }
    }

    private void reinforceFairwayRoutes() {
        for (List<GridPoint> route : holeRoutePoints.values()) {
            for (GridPoint point : route) {
                Tile tile = tileAtPoint(point);
                TileTypes current = tile.getCollapsedState();
                if (current == TileTypes.TEE || current == TileTypes.PIN) {
                    continue;
                }
                tile.collapseTo(TileTypes.FAIRWAY);
            }
        }
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /* ------------------------
       Pars & finalization
       ------------------------ */

    private void assignPar(int holeId, List<Tile> path) {
        if (path == null) {
            holePars.put(holeId, 3);
            return;
        }
        double meters = path.size() * (TILE_SIZE * 0.25);
        int par;
        if (meters < 95) par = 3;
        else if (meters < 160) par = 4;
        else par = 5;

        int bigTurns = countBigTurns(path);
        if (bigTurns >= 3) par++;
        holePars.put(holeId, Math.min(par, 6));
    }

    private int countBigTurns(List<Tile> path) {
        if (path.size() < 3) return 0;
        int count = 0;
        for (int i = 2; i < path.size(); i++) {
            int ax = (path.get(i - 2).getGridPoint().x / TILE_SIZE);
            int az = (path.get(i - 2).getGridPoint().z / TILE_SIZE);
            int bx = (path.get(i - 1).getGridPoint().x / TILE_SIZE);
            int bz = (path.get(i - 1).getGridPoint().z / TILE_SIZE);
            int cx = (path.get(i).getGridPoint().x / TILE_SIZE);
            int cz = (path.get(i).getGridPoint().z / TILE_SIZE);
            int dx1 = bx - ax, dz1 = bz - az;
            int dx2 = cx - bx, dz2 = cz - bz;
            if (dx1 * dz2 != dz1 * dx2) {
                count++;
            }
        }
        return count;
    }

    private void finalizeUnresolvedTiles() {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Tile tile = grid[x][z];
                if (tile.isCollapsed()) continue;

                if (isEdgeTile(x, z)) {
                    tile.collapseTo(TileTypes.OUT_OF_BOUNDS);
                    continue;
                }

                double r = random.nextDouble();
                if (r < 0.02) tile.collapseTo(TileTypes.WATER);
                else if (r < 0.06) tile.collapseTo(TileTypes.SAND);
                else if (r < 0.68) tile.collapseTo(TileTypes.LIGHT_ROUGH);
                else if (r < 0.86) tile.collapseTo(TileTypes.HEAVY_ROUGH);
                else tile.collapseTo(TileTypes.OBSTACLE);
            }
        }
    }

    private boolean isEdgeTile(int x, int z) {
        return x == 0 || z == 0 || x == width - 1 || z == depth - 1;
    }

    /* ------------------------
       Utilities
       ------------------------ */

    private Tile tileAtPoint(GridPoint point) {
        int x = (point.x / TILE_SIZE);
        int z = (point.z / TILE_SIZE);
        x = Math.max(0, Math.min(width - 1, x));
        z = Math.max(0, Math.min(depth - 1, z));
        return grid[x][z];
    }

    private boolean isInBounds(int x, int z) {
        return x >= 0 && x < width && z >= 0 && z < depth;
    }

    /* ------------------------
       Getters
       ------------------------ */

    public Map<Integer, GridPoint> getTeePoints() {
        return Collections.unmodifiableMap(this.teeLocations);
    }

    public Map<Integer, GridPoint> getHolePoints() {
        return Collections.unmodifiableMap(this.holeLocations);
    }

    public Map<Integer, Integer> getHolePars() {
        return Collections.unmodifiableMap(this.holePars);
    }

    public Map<Integer, List<GridPoint>> getHoleRoutePoints() {
        Map<Integer, List<GridPoint>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<GridPoint>> entry : this.holeRoutePoints.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
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
        if (!isInBounds(x, z)) return null;
        return this.grid[x][z];
    }

    public Tile[][] getGrid() {
        return this.grid;
    }
}
