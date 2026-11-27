package normalmanv2.normalDiscGolf.impl.competition;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationSettings;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Random;

public final class WeeklyCourse extends CourseImpl {
    private final WeeklyTournament.Definition event;
    private final boolean wind;
    private final int strokeLimit;

    public WeeklyCourse(WeeklyTournament.Definition event, String name, ConfigurationSection config) {
        super(Division.JUNIOR, grid(event, config), name);
        this.event = event;
        this.wind = config.getBoolean("flight.wind", true);
        this.strokeLimit = Math.max(3, config.getInt("max-strokes-per-hole", 12));
    }

    private static CourseGrid grid(WeeklyTournament.Definition event, ConfigurationSection config) {
        long seed = new Random(event.start().getEpochSecond() ^ 0x28d4150384c5d7f3L).nextLong();
        int holes = event.holes();
        return new CourseGrid(23, ((holes + 2) / 3) * 7 + 2, List.of(TileTypes.values()),
                holes, seed, CourseGrid.Layout.DAILY, CourseGenerationSettings.read(config, seed));
    }

    public WeeklyTournament.Definition event() { return event; }
    public boolean wind() { return wind; }
    public int strokeLimit() { return strokeLimit; }
}
