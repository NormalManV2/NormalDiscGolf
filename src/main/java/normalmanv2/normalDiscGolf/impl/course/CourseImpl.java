package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.api.course.Course;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.library.SavedCourse;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;


public class CourseImpl implements Course {
    private final Division division;
    private final String courseName;
    private final CourseGrid grid;
    private final String generationPreset;
    private final SavedCourse libraryCourse;

    public CourseImpl(Division division, CourseGrid grid, String courseName) {
        this(division, grid, courseName, null);
    }

    public CourseImpl(Division division, CourseGrid grid, String courseName, String generationPreset) {
        this(division, grid, courseName, generationPreset, null);
    }

    public CourseImpl(Division division, CourseGrid grid, String courseName, String generationPreset, SavedCourse libraryCourse) {
        this.division = division;
        this.courseName = courseName;
        this.grid = grid;
        this.generationPreset = generationPreset;
        this.libraryCourse = libraryCourse;
    }

    @Override
    public String name() {
        if (this.courseName == null) throw new IllegalStateException("Course name not set!");
        return this.courseName;
    }

    @Override
    public int holes() {
        return this.grid.getNumHoles();
    }

    @Override
    public CourseGrid.GridPoint startingLocation() {
        return this.grid.getTeePoints().get(0);
    }

    @Override
    public Location toLocation(World world, CourseGrid.GridPoint gridPoint) {
        return new Location(world, gridPoint.x(), gridPoint.y(), gridPoint.z());
    }

    @Override
    public Map<Integer, CourseGrid.GridPoint> teeLocations() {
        return this.grid.getTeePoints();
    }

    @Override
    public Map<Integer, CourseGrid.GridPoint> holeLocations() {
        return this.grid.getHolePoints();
    }

    @Override
    public Map<Integer, Integer> holePars() {
        return this.grid.getHolePars();
    }

    public Division division() {
        return this.division;
    }

    public Location tee(World world, int hole) {
        var point = teeLocations().get(hole);
        Location location = new Location(world, point.x() + .5, grid.surfaceHeight(point.x(), point.z()) + 1, point.z() + .5);
        var opening = grid.openingTarget(hole);
        location.setDirection(new org.bukkit.util.Vector(opening.x() - point.x(), 0, opening.z() - point.z()));
        return location;
    }
    public Location basket(World world, int hole) {
        var point = holeLocations().get(hole);
        return new Location(world, point.x() + .5, grid.surfaceHeight(point.x(), point.z()) + 1.9, point.z() + .5);
    }
    public org.bukkit.util.BoundingBox basketBounds(World world, int hole) {
        Location center = basket(world, hole);
        return org.bukkit.util.BoundingBox.of(center, 1.3, 1.0, 1.3);
    }

    public CourseGrid grid() {
        return this.grid;
    }

    public World world(String roundId) { return Bukkit.getWorld(roundId); }
    public boolean isCustom() { return false; }
    public boolean isLibraryReplay() { return libraryCourse != null; }
    public SavedCourse libraryCourse() { return libraryCourse; }
    public String holeName(int hole) { return grid.holeName(hole); }
    public long seed() { return grid.seed(); }
    public String generationPreset() { return generationPreset; }
    public TileTypes tileAt(Location location) { return grid.getTileTypeAtLocation(location); }

    public Location safeLie(Location landing, Location fallback) {
        World world = landing.getWorld();
        int x = landing.getBlockX(), z = landing.getBlockZ();
        // Use the generated surface, including on holes with obstacles above it.
        for (int radius = 0; radius <= 3; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (grid.surfaceType(x + dx, z + dz).isHazard()) continue;
            int y = grid.surfaceHeight(x + dx, z + dz) + 1;
            if (canStandHere(world, x + dx, y, z + dz))
                return new Location(world, x + dx + .5, y, z + dz + .5, landing.getYaw(), 0);
        }
        return fallback.clone();
    }

    protected static boolean canStandHere(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) return false;
        var feet = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
                && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid();
    }

    public String generationInfo() {
        int difficulty = grid.difficultyRating();
        return grid.terrain().label() + " • Seed " + grid.seed()
                + (difficulty == 0 ? "" : " • Difficulty " + difficulty + "/100")
                + (generationPreset == null ? "" : " • Preset " + generationPreset)
                + (isLibraryReplay() ? " • Course Library" : "");
    }

}
