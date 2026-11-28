package normalmanv2.normalDiscGolf.impl.course.event;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** A round uses one snapshot of the event schedule and settings. */
public record CourseEventSettings(boolean enabled, boolean includeRated, int chance,
                                  Map<CourseEvent, Integer> weights, List<CourseEvent> sequence,
                                  double stormWindScale, boolean lightningFlashes) {

    public static CourseEventSettings read(ConfigurationSection config) {
        String root = "course-events.";
        var weights = new EnumMap<CourseEvent, Integer>(CourseEvent.class);
        for (CourseEvent event : CourseEvent.values()) {
            if (event != CourseEvent.CLEAR)
                weights.put(event, Bounds.clamp(config.getInt(root + "weights." + event.id(), 1), 0, 100));
        }
        var sequence = config.getStringList(root + "sequence").stream().map(CourseEvent::parse)
                .filter(Objects::nonNull).toList();
        double windScale = config.getDouble(root + "thunderstorm.wind-scale", 1);
        return new CourseEventSettings(config.getBoolean(root + "enabled", true),
                config.getBoolean(root + "include-rated", false),
                Bounds.clamp(config.getInt(root + "chance-percent", 25), 0, 100), Map.copyOf(weights), sequence,
                Double.isFinite(windScale) ? Bounds.clamp(windScale, 0, 3) : 1,
                config.getBoolean(root + "thunderstorm.lightning-flashes", true));
    }

    public CourseEvent forHole(long courseSeed, int hole, boolean rated) {
        if (!enabled || rated && !includeRated) return CourseEvent.CLEAR;
        if (!sequence.isEmpty()) return sequence.get(hole % sequence.size());
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        var random = new SplittableRandom(courseSeed ^ (0xD1B54A32D192ED03L * (hole + 1L)));
        if (total == 0 || random.nextInt(100) >= chance) return CourseEvent.CLEAR;
        int selection = random.nextInt(total);
        for (CourseEvent event : CourseEvent.values()) {
            selection -= weights.getOrDefault(event, 0);
            if (selection < 0) return event;
        }
        return CourseEvent.CLEAR;
    }
}
