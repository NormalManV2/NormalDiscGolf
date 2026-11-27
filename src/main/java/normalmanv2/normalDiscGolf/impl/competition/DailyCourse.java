package normalmanv2.normalDiscGolf.impl.competition;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationSettings;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

public final class DailyCourse extends CourseImpl {
    public record Definition(LocalDate date, long seed, int holes, CourseGenerationSettings generation,
                             boolean wind, int strokeLimit) {
        public String label() { return "Daily Course " + date + " UTC"; }

        public String description() {
            return label() + " • " + generation.terrain().label() + " • Seed " + seed
                    + " • " + holes + (holes == 1 ? " hole" : " holes");
        }
    }

    private final Definition definition;

    public DailyCourse(Definition definition, Division division, String name) {
        super(division, new CourseGrid(23, ((definition.holes() + 2) / 3) * 7 + 2, List.of(TileTypes.values()),
                definition.holes(), definition.seed(), CourseGrid.Layout.DAILY, definition.generation()), name);
        this.definition = definition;
    }

    public Definition definition() { return definition; }

    public static Definition createDefinition(LocalDate date, ConfigurationSection config) {
        long seed = new Random(date.toEpochDay() ^ 0x534336fd727b297aL).nextLong();
        return new Definition(date, seed, Math.clamp(config.getInt("daily-course.holes", 6), 1, 18),
                CourseGenerationSettings.read(config, seed), config.getBoolean("flight.wind", true),
                Math.max(3, config.getInt("max-strokes-per-hole", 12)));
    }
}
