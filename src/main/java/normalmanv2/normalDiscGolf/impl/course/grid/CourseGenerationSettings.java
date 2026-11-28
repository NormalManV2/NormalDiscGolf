package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Objects;

public record CourseGenerationSettings(CourseTerrain terrain, int elevation, int fairwayWidth,
                                       double obstacleDensity, double hazardDensity, List<HoleArchetype> archetypes) {
    public CourseGenerationSettings {
        Objects.requireNonNull(terrain);
        elevation = Bounds.clamp(elevation, 0, 32);
        fairwayWidth = Bounds.clamp(fairwayWidth, 8, 28);
        obstacleDensity = density(obstacleDensity, terrain.obstacles);
        hazardDensity = density(hazardDensity, terrain.hazards);
        archetypes = archetypes.isEmpty() ? List.of(HoleArchetype.values()) : List.copyOf(archetypes);
    }

    private static double density(double value, double fallback) {
        return Double.isFinite(value) ? Bounds.clamp(value, 0, 1) : fallback;
    }

    public static CourseGenerationSettings defaults(CourseTerrain terrain) {
        return new CourseGenerationSettings(terrain, terrain.elevation, terrain.fairwayWidth,
                terrain.obstacles, terrain.hazards, List.of());
    }

    public static long seed(ConfigurationSection config) {
        return CourseGenerationProfile.serverDefault(config).nextSeed();
    }

    public static int holes(ConfigurationSection config) {
        return CourseGenerationProfile.serverDefault(config).holes();
    }

    public static ConfigurationSection activeProfile(ConfigurationSection config) {
        return CourseGenerationProfile.activeSection(config);
    }

    public static CourseGenerationSettings read(ConfigurationSection config, long seed) {
        return CourseGenerationProfile.serverDefault(config).settings(seed);
    }

    public static List<CourseTerrain> availableTerrains(ConfigurationSection config) {
        return CourseGenerationProfile.serverDefault(config).terrains();
    }

    public static CourseGenerationSettings read(ConfigurationSection config, CourseTerrain terrain) {
        return CourseGenerationProfile.serverDefault(config).settings(terrain);
    }
}
