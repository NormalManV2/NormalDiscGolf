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

        // 1) seed macro masks if you want (elevation, water basins, veg) - not shown here.

        // 2) Place tees & pins using sampling + pairing
        placeTeesAndPins();

        // 3) Route holes & carve corridors
        for (int holeId = 0; holeId < numHoles; holeId++) {
            GridPoint teeLoc = teeLocations.get(holeId);
            GridPoint pinLoc = holeLocations.get(holeId);
            Tile teeTile = tileAtPoint(teeLoc);
            Tile pinTile = tileAtPoint(pinLoc);
            List<Tile> path = route(teeTile, pinTile);
            if (path == null || path.isEmpty()) {
                // fallback: straight line carve
                path = straightLine(teeTile, pinTile);
            }
            carveCorridor(path);
            assignPar(holeId, path);
            // propagate constraints from carved fairway tiles
            propagateFairwayConstraints();
        }

        // 4) Finalize remaining tiles (mark as light/heavy rough, water, out_of_bounds, etc.)
        finalizeUnresolvedTiles();

        // 5) Initialize obstacle generator and place obstacles respecting masks & safety.
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
        // Simple Poisson-like sampling: dart-throwing with min distance between anchors
        List<int[]> anchors = sampleAnchors(numHoles * 2, Math.max(8, Math.min(width, depth) / 8));
        // Greedy pair: split anchors into tees and pins in two halves and pair by distance constraints
        List<int[]> tees = new ArrayList<>();
        List<int[]> pins = new ArrayList<>();

        // assign tees roughly on left half, pins roughly on right half to encourage variety
        for (int[] a : anchors) {
            if (a[0] < width / 2) tees.add(a);
            else pins.add(a);
        }

        // Ensure we have enough anchors; if not, fallback to random placements (edge bias removed)
        while (tees.size() < numHoles) tees.add(new int[]{random.nextInt(width), random.nextInt(depth)});
        while (pins.size() < numHoles) pins.add(new int[]{random.nextInt(width), random.nextInt(depth)});

        // pair greedily by closest distance within practical bounds
        int MIN_D = Math.max(10, Math.min(width, depth) / 6);
        int MAX_D = Math.max(MIN_D + 10, Math.max(width, depth) / 2);

        for (int i = 0; i < numHoles; i++) {
            int[] t = tees.get(i % tees.size());
            int[] p = pins.get(i % pins.size());

            // if too close or too far, jitter pin
            int dx = t[0] - p[0], dz = t[1] - p[1];
            int dist = (int) Math.hypot(dx, dz);
            if (dist < MIN_D || dist > MAX_D) {
                // attempt to find a pin candidate that fits
                boolean found = false;
                for (int[] cand : pins) {
                    dx = t[0] - cand[0];
                    dz = t[1] - cand[1];
                    dist = (int) Math.hypot(dx, dz);
                    if (dist >= MIN_D && dist <= MAX_D) {
                        p = cand;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // jitter
                    p = new int[]{Math.clamp(t[0] + (random.nextInt(2 * MAX_D) - MAX_D), 0, width - 1),
                            Math.clamp(t[1] + (random.nextInt(2 * MAX_D) - MAX_D), 0, depth - 1)};
                }
            }

            GridPoint teeLoc = new GridPoint((int) (t[0] * TILE_SIZE + TILE_SIZE / 2.0), 64, (int) (t[1] * TILE_SIZE + TILE_SIZE / 2.0));
            GridPoint pinLoc = new GridPoint((int) (p[0] * TILE_SIZE + TILE_SIZE / 2.0), 64, (int) (p[1] * TILE_SIZE + TILE_SIZE / 2.0));

            this.teeLocations.put(i, teeLoc);
            this.holeLocations.put(i, pinLoc);

            // collapse tiles to TEEs / PINS now
            tileAtPoint(teeLoc).collapseTo(TileTypes.TEE);
            tileAtPoint(pinLoc).collapseTo(TileTypes.PIN);
        }
    }

    private List<int[]> sampleAnchors(int count, int minDist) {
        List<int[]> out = new ArrayList<>();
        int attempts = 0, maxAttempts = count * 200;
        while (out.size() < count && attempts++ < maxAttempts) {
            int x = random.nextInt(width);
            int z = random.nextInt(depth);
            boolean ok = true;
            for (int[] h : out) {
                int dx = x - h[0], dz = z - h[1];
                if (dx * dx + dz * dz < minDist * minDist) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(new int[]{x, z});
        }
        return out;
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

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        boolean[][][] closed = new boolean[width][depth][4];

        // seed all four directions from start
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
                Node n = cur;
                while (n != null) {
                    path.add(grid[n.x][n.z]);
                    n = n.parent;
                }
                Collections.reverse(path);
                return path;
            }

            for (int nd = 0; nd < 4; nd++) {
                int nx = cur.x + DIRS[nd][0];
                int nz = cur.z + DIRS[nd][1];
                if (!isInBounds(nx, nz)) continue;

                double turnCost = (nd == cur.dir) ? 0.0 : 0.6; // penalize turns
                double slopeCost = 0.0; // hook for elevation field
                double hazardCost = tileHazardCost(nx, nz);

                double g = cur.g + 1.0 + turnCost + slopeCost + hazardCost;
                double h = Math.abs(nx - gx) + Math.abs(nz - gz);
                double f = g + h;
                if (!closed[nx][nz][nd]) open.add(new Node(nx, nz, nd, g, f, cur));
            }
        }
        return null;
    }

    private double tileHazardCost(int x, int z) {
        TileTypes t = grid[x][z].getCollapsedState();
        if (t == TileTypes.WATER) return 6.0;
        if (t == TileTypes.OUT_OF_BOUNDS) return 10.0;
        return 0.0;
    }

    private List<Tile> straightLine(Tile a, Tile b) {
        int ax = (a.getGridPoint().x / TILE_SIZE);
        int az = (a.getGridPoint().z / TILE_SIZE);
        int bx = (b.getGridPoint().x / TILE_SIZE);
        int bz = (b.getGridPoint().z / TILE_SIZE);
        List<Tile> path = new ArrayList<>();
        int dx = Integer.signum(bx - ax), dz = Integer.signum(bz - az);
        int x = ax, z = az;
        while (x != bx || z != bz) {
            path.add(grid[x][z]);
            if (x != bx) x += dx;
            if (z != bz) z += dz;
        }
        path.add(grid[bx][bz]);
        return path;
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
            double widen = landingZoneWeight(t); // 0..1
            int fairwayR = (int) Math.round(lerp(1.0, 3.0, widen)); // tiles
            int lightR = fairwayR + 1;
            int heavyR = fairwayR + 2;

            paintDisk(cx, cz, heavyR, TileTypes.HEAVY_ROUGH);
            paintDisk(cx, cz, lightR, TileTypes.LIGHT_ROUGH);
            paintDisk(cx, cz, fairwayR, TileTypes.FAIRWAY);
        }
    }

    private double landingZoneWeight(double t) {
        double m1 = Math.exp(-Math.pow((t - 0.45) / 0.18, 2));
        return Math.min(1.0, m1);
    }

    private void paintDisk(int cx, int cz, int r, TileTypes setTo) {
        int r2 = r * r;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!isInBounds(x, z)) continue;
                int dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= r2) {
                    Tile t = grid[x][z];
                    // Prefer not to overwrite TEE/PIN
                    if (t.isCollapsed() && (t.getCollapsedState() == TileTypes.TEE || t.getCollapsedState() == TileTypes.PIN))
                        continue;
                    t.collapseTo(setTo);
                }
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
        double meters = path.size() * (TILE_SIZE * 0.25); // rough mapping: 1 tile -> ~4m (adjust as you want)
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
            if (dx1 * dz2 != dz1 * dx2) { // direction changed
                count++;
            }
        }
        return count;
    }

    private void finalizeUnresolvedTiles() {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Tile t = grid[x][z];
                if (t.isCollapsed()) continue;
                // basic mask: random water with low frequency; else heavy/light rough or obstacle
                double r = random.nextDouble();
                if (r < 0.06) t.collapseTo(TileTypes.WATER);
                else if (r < 0.15) t.collapseTo(TileTypes.HEAVY_ROUGH);
                else if (r < 0.2) t.collapseTo(TileTypes.LIGHT_ROUGH);
                else t.collapseTo(TileTypes.OBSTACLE);
            }
        }
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

    /* Propagate but don't prematurely return as original bug did */
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

    private void propagateConstraints(Tile tile) {
        Queue<Tile> queue = new LinkedList<>();
        queue.add(tile);

        while (!queue.isEmpty()) {
            Tile current = queue.poll();
            int x = (current.getGridPoint().x / TILE_SIZE);
            int z = (current.getGridPoint().z / TILE_SIZE);

            for (int[] direction : DIRS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (isInBounds(nx, nz)) {
                    Tile neighbor = grid[nx][nz];
                    if (!neighbor.isCollapsed()) continue; // NOTE: previously had 'return' here - fixed
                    boolean updated = neighbor.updatePossibleStatesBasedOn(current);
                    if (updated) queue.add(neighbor);
                }
            }
        }
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
