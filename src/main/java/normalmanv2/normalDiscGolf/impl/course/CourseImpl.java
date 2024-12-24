package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.api.course.Course;
import normalmanv2.normalDiscGolf.impl.registry.ObstacleRegistry;
import org.bukkit.Location;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public record CourseImpl(CourseDifficulty difficulty,
                         String name,
                         int holes,
                         CourseGrid grid,
                         Location startingLocation,
                         Map<Integer, Location> teeLocations,
                         Set<Location> holeLocations,
                         Map<Integer, Integer> holePars) implements Course {

    public void clearAndSetHoleLocations(Set<Location> locations) {
        this.holeLocations.clear();
        this.holeLocations.addAll(locations);
    }

    public void generateCourseGrid(ObstacleRegistry registry) {
        this.grid.generate(registry, this.difficulty);
    }

    @Override
    public Map<Integer, Location> teeLocations() {
        return Collections.unmodifiableMap(this.teeLocations);
    }

    @Override
    public Set<Location> holeLocations() {
        return Collections.unmodifiableSet(this.holeLocations);
    }

    @Override
    public Map<Integer, Integer> holePars() {
        return Collections.unmodifiableMap(this.holePars);
    }

}
