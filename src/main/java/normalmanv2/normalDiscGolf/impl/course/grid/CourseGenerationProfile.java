package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CourseGenerationProfile {
    private static final ConfigurationSection EMPTY = new MemoryConfiguration();
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]+");
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[-_]+");

    private final ConfigurationSection base;
    private final ConfigurationSection overrides;
    private final String id;

    private CourseGenerationProfile(ConfigurationSection config, ConfigurationSection overrides, String id) {
        var course = config.getConfigurationSection("course");
        this.base = course != null ? course : EMPTY;
        this.overrides = overrides != null ? overrides : EMPTY;
        this.id = id;
    }

    public static CourseGenerationProfile serverDefault(ConfigurationSection config) {
        return new CourseGenerationProfile(config, activeSection(config), null);
    }

    public static ConfigurationSection activeSection(ConfigurationSection config) {
        String id = config.getString("course.profile", "").trim();
        return ID.matcher(id).matches() ? config.getConfigurationSection("course.profiles." + id) : null;
    }

    public static List<CourseGenerationProfile> selectable(ConfigurationSection config) {
        // Only offer presets present in the server's file. Defaults bundled in the jar still work as an active profile.
        if (!config.contains("course.profiles", true)) return List.of();
        var profiles = config.getConfigurationSection("course.profiles");
        if (profiles == null) return List.of();
        return profiles.getKeys(false).stream().sorted()
                .map(id -> selectable(config, id)).filter(Objects::nonNull).toList();
    }

    public static CourseGenerationProfile selectable(ConfigurationSection config, String id) {
        if (id == null || !ID.matcher(id).matches()) return null;
        String path = "course.profiles." + id;
        if (!config.contains(path, true)) return null;
        var section = config.getConfigurationSection(path);
        return section != null && section.getBoolean("lobby-selectable", true)
                ? new CourseGenerationProfile(config, section, id) : null;
    }

    public String id() { return id; }

    public String label() {
        if (id == null) return "Server Default";
        String title = WORD_SEPARATOR.splitAsStream(id)
                .filter(part -> !part.isEmpty())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
        return title.isEmpty() ? id : title;
    }

    public int holes() { return Bounds.clamp(source("holes").getInt("holes", 18), 1, 18); }

    public Long fixedSeed() {
        return "fixed".equalsIgnoreCase(source("seed-mode").getString("seed-mode", "random"))
                ? source("seed").getLong("seed", 2026) : null;
    }

    public long nextSeed() {
        Long fixed = fixedSeed();
        return fixed == null ? ThreadLocalRandom.current().nextLong() : fixed;
    }

    public List<CourseTerrain> terrains() {
        CourseTerrain fixed = CourseTerrain.parse(source("terrain").getString("terrain", "random"));
        if (fixed != null) return List.of(fixed);
        var enabled = source("enabled-terrains").getStringList("enabled-terrains").stream().map(CourseTerrain::parse)
                .filter(Objects::nonNull).distinct().toList();
        return enabled.isEmpty() ? CourseTerrain.environments() : enabled;
    }

    public CourseGenerationSettings settings(long seed) {
        var terrains = terrains();
        return settings(terrains.get(new Random(seed ^ 0x6a09e667f3bcc909L).nextInt(terrains.size())));
    }

    public CourseGenerationSettings settings(CourseTerrain terrain) {
        String prefix = "terrains." + terrain.id() + ".";
        var archetypes = overrides.getStringList(prefix + "archetypes");
        if (archetypes.isEmpty()) archetypes = overrides.getStringList("archetypes");
        if (archetypes.isEmpty()) archetypes = base.getStringList(prefix + "archetypes");
        if (archetypes.isEmpty()) archetypes = base.getStringList("archetypes");
        return new CourseGenerationSettings(terrain,
                source(prefix + "elevation").getInt(prefix + "elevation", terrain.elevation),
                source(prefix + "fairway-width").getInt(prefix + "fairway-width", terrain.fairwayWidth),
                source(prefix + "obstacle-density").getDouble(prefix + "obstacle-density", terrain.obstacles),
                source(prefix + "hazard-density").getDouble(prefix + "hazard-density", terrain.hazards),
                archetypes.stream().map(HoleArchetype::parse).filter(Objects::nonNull).distinct().toList());
    }

    private ConfigurationSection source(String key) { return overrides.contains(key) ? overrides : base; }
}
