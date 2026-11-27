package normalmanv2.normalDiscGolf.impl.course.grid;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public record CourseGenerationSettings(CourseTerrain terrain, int elevation, int fairwayWidth,
                                       double obstacleDensity, double hazardDensity, List<HoleArchetype> archetypes) {
    private static final ConfigurationSection EMPTY = new MemoryConfiguration();

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
        var course = config.getConfigurationSection("course");
        var profile = activeProfile(config);
        return "fixed".equalsIgnoreCase(setting(course, profile, "seed-mode").getString("seed-mode", "random"))
                ? setting(course, profile, "seed").getLong("seed", 2026) : ThreadLocalRandom.current().nextLong();
    }

    public static int holes(ConfigurationSection config) {
        return Math.clamp(setting(config.getConfigurationSection("course"), activeProfile(config), "holes")
                .getInt("holes", 18), 1, 18);
    }

    public static ConfigurationSection activeProfile(ConfigurationSection config) {
        String id = config.getString("course.profile", "").trim();
        return id.matches("[a-zA-Z0-9_-]+") ? config.getConfigurationSection("course.profiles." + id) : null;
    }

    private static ConfigurationSection setting(ConfigurationSection course, ConfigurationSection profile, String key) {
        if (profile != null && profile.contains(key)) return profile;
        return course != null ? course : EMPTY;
    }

    public static CourseGenerationSettings read(ConfigurationSection config, long seed) {
        var enabled = availableTerrains(config);
        return read(config, enabled.get(new Random(seed ^ 0x6a09e667f3bcc909L).nextInt(enabled.size())));
    }

    public static List<CourseTerrain> availableTerrains(ConfigurationSection config) {
        var course = config.getConfigurationSection("course");
        var profile = activeProfile(config);
        CourseTerrain fixed = CourseTerrain.parse(setting(course, profile, "terrain").getString("terrain", "random"));
        if (fixed != null) return List.of(fixed);
        var enabled = setting(course, profile, "enabled-terrains").getStringList("enabled-terrains").stream().map(CourseTerrain::parse)
                .filter(Objects::nonNull).distinct().toList();
        return enabled.isEmpty() ? CourseTerrain.environments() : enabled;
    }

    public static CourseGenerationSettings read(ConfigurationSection config, CourseTerrain terrain) {
        var profile = activeProfile(config);
        String prefix = "terrains." + terrain.id() + ".";
        var course = config.getConfigurationSection("course");
        var archetypes = profile == null ? List.<String>of() : profile.getStringList(prefix + "archetypes");
        if (archetypes.isEmpty() && profile != null) archetypes = profile.getStringList("archetypes");
        if (archetypes.isEmpty() && course != null) archetypes = course.getStringList(prefix + "archetypes");
        if (archetypes.isEmpty() && course != null) archetypes = course.getStringList("archetypes");
        return new CourseGenerationSettings(terrain,
                setting(course, profile, prefix + "elevation").getInt(prefix + "elevation", terrain.elevation),
                setting(course, profile, prefix + "fairway-width").getInt(prefix + "fairway-width", terrain.fairwayWidth),
                setting(course, profile, prefix + "obstacle-density").getDouble(prefix + "obstacle-density", terrain.obstacles),
                setting(course, profile, prefix + "hazard-density").getDouble(prefix + "hazard-density", terrain.hazards),
                archetypes.stream().map(HoleArchetype::parse).filter(Objects::nonNull).distinct().toList());
    }
}
