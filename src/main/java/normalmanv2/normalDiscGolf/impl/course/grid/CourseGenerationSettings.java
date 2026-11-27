package normalmanv2.normalDiscGolf.impl.course.grid;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public record CourseGenerationSettings(CourseTerrain terrain, int elevation, int fairwayWidth,
                                       double obstacleDensity, double hazardDensity, List<HoleArchetype> archetypes) {
    public CourseGenerationSettings {
        Objects.requireNonNull(terrain);
        elevation = Math.clamp(elevation, 0, 32);
        fairwayWidth = Math.clamp(fairwayWidth, 8, 28);
        obstacleDensity = density(obstacleDensity, terrain.obstacles);
        hazardDensity = density(hazardDensity, terrain.hazards);
        archetypes = archetypes.isEmpty() ? List.of(HoleArchetype.values()) : List.copyOf(archetypes);
    }

    private static double density(double value, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, 0, 1) : fallback;
    }

    public static CourseGenerationSettings defaults(CourseTerrain terrain) {
        return new CourseGenerationSettings(terrain, terrain.elevation, terrain.fairwayWidth,
                terrain.obstacles, terrain.hazards, List.of());
    }

    public static long seed(ConfigurationSection config) {
        return "fixed".equalsIgnoreCase(config.getString("course.seed-mode", "random"))
                ? config.getLong("course.seed", 2026) : ThreadLocalRandom.current().nextLong();
    }

    public static CourseGenerationSettings read(ConfigurationSection config, long seed) {
        var enabled = availableTerrains(config);
        return read(config, enabled.get(new Random(seed ^ 0x6a09e667f3bcc909L).nextInt(enabled.size())));
    }

    public static List<CourseTerrain> availableTerrains(ConfigurationSection config) {
        CourseTerrain fixed = CourseTerrain.parse(config.getString("course.terrain", "random"));
        if (fixed != null) return List.of(fixed);
        var enabled = config.getStringList("course.enabled-terrains").stream().map(CourseTerrain::parse)
                .filter(Objects::nonNull).distinct().toList();
        return enabled.isEmpty() ? CourseTerrain.environments() : enabled;
    }

    public static CourseGenerationSettings read(ConfigurationSection config, CourseTerrain terrain) {
        String prefix = "course.terrains." + terrain.id() + ".";
        var archetypes = config.getStringList(prefix + "archetypes");
        if (archetypes.isEmpty()) archetypes = config.getStringList("course.archetypes");
        return new CourseGenerationSettings(terrain,
                config.getInt(prefix + "elevation", terrain.elevation),
                config.getInt(prefix + "fairway-width", terrain.fairwayWidth),
                config.getDouble(prefix + "obstacle-density", terrain.obstacles),
                config.getDouble(prefix + "hazard-density", terrain.hazards),
                archetypes.stream().map(HoleArchetype::parse).filter(Objects::nonNull).distinct().toList());
    }
}
