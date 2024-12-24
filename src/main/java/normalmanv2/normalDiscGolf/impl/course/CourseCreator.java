package normalmanv2.normalDiscGolf.impl.course;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CourseCreator {

    private CourseDifficulty difficulty;
    private String name;
    private int holes;
    private Location startingLocation;
    private Map<Integer, Location> teeLocations;
    private Set<Location> holeLocations;
    private Map<Integer, Integer> holePars;

    public CourseCreator setDifficulty(CourseDifficulty difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public CourseCreator setName(String name) {
        this.name = name;
        return this;
    }

    public CourseCreator setHoles(int holes) {
        this.holes = holes;
        return this;
    }

    public CourseCreator setStartingLocation(Location startingLocation) {
        this.startingLocation = startingLocation;
        return this;
    }

    public CourseCreator setTeeLocations(Map<Integer, Location> teeLocations) {
        this.teeLocations = teeLocations;
        return this;
    }

    public CourseCreator setHoleLocations(Set<Location> holeLocations) {
        this.holeLocations = holeLocations;
        return this;
    }

    public CourseCreator setHolePars(Map<Integer, Integer> holePars) {
        this.holePars = holePars;
        return this;
    }

    public CourseGrid createGrid(int width, int depth, List<TileTypes> tileTypes, World world) {
        return new CourseGrid(width, depth, tileTypes, world);
    }

    public CourseImpl build(CourseGrid courseGrid) {
        return new CourseImpl(difficulty, name, holes, courseGrid, startingLocation, teeLocations, holeLocations, holePars);
    }

}
