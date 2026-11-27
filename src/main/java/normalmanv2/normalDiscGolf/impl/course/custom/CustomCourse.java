package normalmanv2.normalDiscGolf.impl.course.custom;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid.GridPoint;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A named course in an existing world. Saved coordinates are the base of each tee or basket. */
public final class CustomCourse extends CourseImpl {
    public static final int MAX_HOLES = 18;
    public record Hole(GridPoint tee, GridPoint basket, Integer par) {
        public Hole withTee(GridPoint point) { return new Hole(point, basket, par); }
        public Hole withBasket(GridPoint point) { return new Hole(tee, point, par); }
        public Hole withPar(int value) { return new Hole(tee, basket, value); }
    }

    private final String id;
    private final UUID owner;
    private final UUID worldId;
    private final String worldName;
    private final Map<Integer, Hole> layout;

    public CustomCourse(String id, String name, UUID owner, UUID worldId, String worldName, Map<Integer, Hole> layout) {
        super(Division.JUNIOR, null, name);
        this.id = id;
        this.owner = owner;
        this.worldId = worldId;
        this.worldName = worldName;
        this.layout = Map.copyOf(layout);
    }

    public String id() { return id; }
    public UUID owner() { return owner; }
    public UUID worldId() { return worldId; }
    public String worldName() { return worldName; }
    public Map<Integer, Hole> layout() { return layout; }
    @Override public boolean isCustom() { return true; }
    @Override public World world(String roundId) { return Bukkit.getWorld(worldId); }
    @Override public int holes() { return layout.size(); }
    @Override public String holeName(int hole) { return "Custom hole " + (hole + 1); }
    @Override public long seed() { return worldId.getMostSignificantBits() ^ id.hashCode(); }
    @Override public GridPoint startingLocation() { return layout.get(0).tee(); }
    @Override public String generationInfo() { return name() + " (custom course: " + id + ")"; }

    @Override public Map<Integer, GridPoint> teeLocations() {
        Map<Integer, GridPoint> tees = new LinkedHashMap<>();
        layout.forEach((number, hole) -> tees.put(number, hole.tee()));
        return Map.copyOf(tees);
    }

    @Override public Map<Integer, GridPoint> holeLocations() {
        Map<Integer, GridPoint> baskets = new LinkedHashMap<>();
        layout.forEach((number, hole) -> baskets.put(number, hole.basket()));
        return Map.copyOf(baskets);
    }

    @Override public Map<Integer, Integer> holePars() {
        Map<Integer, Integer> pars = new LinkedHashMap<>();
        layout.forEach((number, hole) -> pars.put(number, hole.par()));
        return Map.copyOf(pars);
    }

    @Override public Location tee(World world, int hole) {
        Hole anchors = layout.get(hole);
        var start = centered(world, anchors.tee());
        start.setDirection(centered(world, anchors.basket()).toVector().subtract(start.toVector()).setY(0));
        return start;
    }

    @Override public Location basket(World world, int hole) {
        return centered(world, layout.get(hole).basket()).add(0, .9, 0);
    }

    public static Location centered(World world, GridPoint point) {
        return new Location(world, point.x() + .5, point.y(), point.z() + .5);
    }

    public List<String> validate() {
        var errors = new ArrayList<String>();
        if (layout.isEmpty()) {
            errors.add("Add at least one hole.");
            return errors;
        }
        World world = world(null);
        if (world == null) errors.add("World " + worldName + " must be loaded.");
        int lastHole = layout.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int index = 0; index <= lastHole; index++) {
            String prefix = "Hole " + (index + 1) + ": ";
            Hole hole = layout.get(index);
            if (hole == null) {
                errors.add(prefix + "missing. Use consecutive hole numbers.");
                continue;
            }
            if (hole.par() == null || hole.par() < 2 || hole.par() > 6) errors.add(prefix + "set a par from 2 to 6.");
            validateAnchor(world, hole.tee(), prefix + "tee", false, errors);
            validateAnchor(world, hole.basket(), prefix + "basket", true, errors);
            if (hole.tee() != null && hole.basket() != null
                    && Math.hypot((double)hole.tee().x() - hole.basket().x(), (double)hole.tee().z() - hole.basket().z()) < 5)
                errors.add(prefix + "leave at least 5 horizontal blocks between the tee and basket.");
        }
        return errors;
    }

    private static void validateAnchor(World world, GridPoint point, String label, boolean basket, List<String> errors) {
        if (point == null) {
            errors.add(label + " is missing.");
            return;
        }
        if (world == null) return;
        if (!world.getWorldBorder().isInside(centered(world, point))
                || !safeStandingBlock(world, point.x(), point.y(), point.z())) {
            errors.add(label + " needs solid ground and two clear blocks within the world border.");
            return;
        }
        if (basket && (point.y() >= world.getMaxHeight() - 2
                || !world.getBlockAt(point.x(), point.y() + 2, point.z()).isPassable()
                || world.getBlockAt(point.x(), point.y() + 2, point.z()).isLiquid()))
            errors.add(label + " needs a third clear block above its base.");
    }

    private static boolean safeStandingBlock(World world, int x, int y, int z) {
        if (!canStandHere(world, x, y, z)) return false;
        var support = world.getBlockAt(x, y - 1, z);
        Material feet = world.getBlockAt(x, y, z).getType();
        Material head = world.getBlockAt(x, y + 1, z).getType();
        return support.getBoundingBox().getMaxY() >= y - .001 && !unsafe(support.getType())
                && !unsafe(feet) && !unsafe(head);
    }

    private static boolean unsafe(Material material) {
        return switch (material) {
            case FIRE, SOUL_FIRE, POWDER_SNOW, CACTUS, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, SWEET_BERRY_BUSH -> true;
            default -> false;
        };
    }

    @Override public TileTypes tileAt(Location location) {
        if (!location.getWorld().getWorldBorder().isInside(location)) return TileTypes.OUT_OF_BOUNDS;
        return switch (location.getBlock().getType()) {
            case WATER -> TileTypes.WATER;
            case LAVA -> TileTypes.LAVA;
            case SAND, RED_SAND -> TileTypes.SAND;
            default -> TileTypes.FAIRWAY;
        };
    }

    @Override public Location safeLie(Location landing, Location fallback) {
        World world = landing.getWorld();
        int x = landing.getBlockX(), y = landing.getBlockY(), z = landing.getBlockZ();
        // Search around the impact elevation so cave and elevated holes use their actual floor.
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                for (int offset = 0; offset <= 8; offset++) {
                    int below = y - offset, above = y + offset;
                    if (safeStandingBlock(world, x + dx, below, z + dz))
                        return new Location(world, x + dx + .5, below, z + dz + .5, landing.getYaw(), 0);
                    if (offset != 0 && safeStandingBlock(world, x + dx, above, z + dz))
                        return new Location(world, x + dx + .5, above, z + dz + .5, landing.getYaw(), 0);
                }
            }
        }
        return fallback.clone();
    }
}
