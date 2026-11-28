package normalmanv2.normalDiscGolf.impl.admin;

import normalmanv2.normalDiscGolf.impl.course.event.CourseEvent;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationProfile;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.grid.HoleArchetype;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Typed destinations for the shipped configuration and user-defined terrain profiles. */
public final class SettingsSchema {
    private static final String COURSE = "course.";
    private static final String TERRAIN_SETTINGS = "course.terrain-settings.";
    private static final Set<String> PROFILE_KEYS = Set.of("terrain", "seed-mode", "seed", "holes",
            "enabled-terrains", "archetypes", "lobby-selectable");
    private static final Set<String> TERRAIN_KEYS = Set.of("elevation", "fairway-width", "obstacle-density",
            "hazard-density", "archetypes");
    private final ConfigurationSection defaults;

    public record Check(List<String> errors, List<String> warnings) {
        public boolean valid() { return errors.isEmpty(); }
    }

    public SettingsSchema(ConfigurationSection defaults) { this.defaults = defaults; }

    public Set<String> keys(ConfigurationSection config) {
        var keys = new TreeSet<String>();
        for (var section : List.of(defaults, config)) for (String key : section.getKeys(true))
            if (sample(key) != null) keys.add(key);
        return keys;
    }

    public boolean known(String key) { return sample(key) != null; }
    public boolean restartOnly(String key) {
        return key.equals("resource-pack") || key.startsWith("resource-pack.") || key.equals("clubhouse.enabled");
    }

    private Object sample(String key) {
        if (key.equals("spectating.long-putt-blocks")) return 10.0;
        Object bundled = defaults.get(key);
        if (bundled != null && !(bundled instanceof ConfigurationSection)) return bundled;
        String canonical = canonical(key);
        if (canonical == null) return null;
        if (canonical.equals("course.lobby-selectable")) return true;
        if (canonical.startsWith(TERRAIN_SETTINGS)) return switch (canonical.substring(TERRAIN_SETTINGS.length())) {
            case "elevation", "fairway-width" -> 0;
            case "obstacle-density", "hazard-density" -> 0.0;
            case "archetypes" -> List.of("");
            default -> null;
        };
        return defaults.get(canonical);
    }

    private static String canonical(String key) {
        if (!key.startsWith(COURSE)) return null;
        String suffix = key.substring(COURSE.length());
        if (suffix.startsWith("profiles.")) {
            String[] parts = suffix.split("\\.", 3);
            if (parts.length != 3 || !parts[1].matches("[A-Za-z0-9_-]+")) return null;
            suffix = parts[2];
            if (PROFILE_KEYS.contains(suffix)) return COURSE + suffix;
        }
        if (suffix.startsWith("terrains.")) {
            String[] parts = suffix.split("\\.", -1);
            var terrain = parts.length == 3 ? CourseTerrain.parse(parts[1]) : null;
            if (terrain != null && terrain.id().equals(parts[1]) && TERRAIN_KEYS.contains(parts[2]))
                return TERRAIN_SETTINGS + parts[2];
        }
        return null;
    }

    public Object parse(String key, String input) {
        Object type = sample(key);
        if (type == null) throw new IllegalArgumentException("Unknown setting " + key + ".");
        String value = input.strip();
        Object parsed;
        try {
            if (type instanceof Boolean) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
                    throw new IllegalArgumentException("Use true or false.");
                parsed = Boolean.parseBoolean(value);
            } else if (type instanceof List<?>) {
                String list = value.startsWith("[") && value.endsWith("]") ? value.substring(1, value.length() - 1).strip() : value;
                var items = list.isEmpty() ? List.<String>of() : List.of(list.split(",", -1)).stream().map(String::strip).toList();
                parsed = key.endsWith(".prizes") ? items.stream().map(Integer::parseInt).toList() : items;
            } else if (key.endsWith(".seed")) parsed = Long.parseLong(value);
            else if (type instanceof Integer) parsed = Integer.parseInt(value);
            else if (type instanceof Number) parsed = Double.parseDouble(value);
            else parsed = value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Enter a valid number or comma-separated list of numbers.");
        }
        String issue = problem(key, parsed);
        if (issue != null) throw new IllegalArgumentException(issue);
        return parsed;
    }

    public Check check(ConfigurationSection config) {
        var errors = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (String key : config.getKeys(true)) {
            if (!config.contains(key, true)) continue;
            Object value = config.get(key);
            if (sample(key) != null) {
                String problem = problem(key, value);
                if (problem != null) errors.add(key + " — " + problem);
            } else if (!(value instanceof ConfigurationSection)) {
                if (defaults.isConfigurationSection(key) || key.matches("course\\.(?:profiles|terrains)\\.[A-Za-z0-9_-]+(?:\\.terrains(?:\\.[A-Za-z0-9_-]+)?)?"))
                    errors.add(key + " — Expected a YAML section.");
                else warnings.add("Unknown setting " + key + ".");
            }
        }
        String profile = config.getString("course.profile", "").strip();
        if (!profile.isEmpty() && CourseGenerationProfile.activeSection(config) == null)
            errors.add("course.profile — Select a configured generation profile.");
        if (config.getInt("weekly-tournament.min-players", 2) > config.getInt("weekly-tournament.max-players", 8))
            errors.add("weekly-tournament.min-players — Must be at most max-players.");

        if (config.getBoolean("resource-pack.enabled", true) && config.getString("resource-pack.url", "").isBlank()) {
            if (config.getBoolean("resource-pack.required", false))
                errors.add("resource-pack.url — Supply a delivery URL before requiring the pack for remote players.");
            else if (config.getBoolean("resource-pack.serve", true) && config.getBoolean("resource-pack.local-auto", true))
                warnings.add("The pack is offered to local clients. Set resource-pack.url to offer it to remote players.");
            else warnings.add("A pack ZIP is exported. Set resource-pack.url to offer it to players.");
        }
        return new Check(List.copyOf(errors), List.copyOf(warnings));
    }

    private String problem(String key, Object value) {
        Object expected = sample(key);
        String canonical = canonical(key);
        if (canonical == null) canonical = key;
        if (expected instanceof Boolean) return value instanceof Boolean ? null : "Use true or false.";
        if (expected instanceof List<?>) return listProblem(canonical, value);
        if (expected instanceof Number) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
                return "Enter a finite number.";
            if (expected instanceof Integer && !(value instanceof Integer) && !(key.endsWith(".seed") && value instanceof Long))
                return "Enter a whole number in range.";
            return rangeProblem(canonical, number.doubleValue());
        }
        if (!(value instanceof String name)) return "Enter text.";
        if ((canonical.equals("course.seed-mode") || canonical.equals("weekly-tournament.time-utc")) && !name.equals(name.strip()))
            return "Remove the surrounding spaces.";
        return textProblem(canonical, name.strip());
    }

    private static String rangeProblem(String key, double value) {
        if (key.endsWith(".holes")) return range(value, 1, 18);
        if (key.startsWith("course-events.weights.")) return range(value, 0, 100);
        return switch (key) {
            case "turn-timeout-seconds" -> range(value, 10, Integer.MAX_VALUE);
            case "best-shot-choice-seconds" -> range(value, 5, Integer.MAX_VALUE);
            case "max-strokes-per-hole" -> range(value, 3, Integer.MAX_VALUE);
            case "queue-countdown-seconds", "disc-shop.rotation-hours", "disc-shop.credits-per-basket" -> range(value, 0, Integer.MAX_VALUE);
            case "ranked-seasons.minimum-rounds" -> range(value, 1, Integer.MAX_VALUE);
            case "backups.interval-seconds", "backups.keep" -> range(value, 1, Integer.MAX_VALUE);
            case "ranked-seasons.length-days" -> range(value, 1, 365);
            case "weekly-tournament.signup-hours" -> range(value, 1, 168);
            case "weekly-tournament.min-players", "weekly-tournament.max-players" -> range(value, 2, 8);
            case "course.terrain-settings.elevation" -> range(value, 0, 32);
            case "course.terrain-settings.fairway-width" -> range(value, 8, 28);
            case "course.terrain-settings.obstacle-density", "course.terrain-settings.hazard-density" -> range(value, 0, 1);
            case "course-events.chance-percent" -> range(value, 0, 100);
            case "course-events.thunderstorm.wind-scale" -> range(value, 0, 3);
            case "arcade.charges-per-disc" -> range(value, 0, 9);
            case "preview.seconds" -> range(value, 2, 10);
            case "spectating.long-putt-blocks" -> range(value, 1, 15);
            case "resource-pack.port" -> range(value, 1, 65535);
            default -> null;
        };
    }

    private static String range(double value, int low, int high) {
        return value >= low && value <= high ? null : "Use a value from " + low + " to " + high + ".";
    }

    private static String listProblem(String key, Object value) {
        if (!(value instanceof List<?> items)) return "Use a list.";
        for (Object item : items) {
            boolean valid = key.endsWith(".prizes") ? item instanceof Integer credits && credits >= 0
                    : item instanceof String text && switch (key) {
                        case "course.enabled-terrains" -> CourseTerrain.parse(text) != null;
                        case "course.archetypes", "course.terrain-settings.archetypes" -> HoleArchetype.parse(text) != null;
                        case "course-events.sequence" -> CourseEvent.parse(text) != null;
                        default -> false;
                    };
            if (!valid) return "List contains an unsupported value.";
        }
        return null;
    }

    private static String textProblem(String key, String value) {
        return switch (key) {
            case "course.terrain" -> value.equalsIgnoreCase("random") || CourseTerrain.parse(value) != null
                    ? null : "Use random or a course terrain ID.";
            case "course.seed-mode" -> value.equalsIgnoreCase("random") || value.equalsIgnoreCase("fixed")
                    ? null : "Use random or fixed.";
            case "course.profile" -> value.isEmpty() || value.matches("[A-Za-z0-9_-]+") ? null : "Use a profile ID.";
            case "weekly-tournament.day" -> {
                try { DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT)); yield null; }
                catch (IllegalArgumentException invalid) { yield "Enter a weekday."; }
            }
            case "weekly-tournament.time-utc" -> {
                try { LocalTime.parse(value); yield null; }
                catch (java.time.format.DateTimeParseException invalid) { yield "Use a UTC time such as 20:00."; }
            }
            case "resource-pack.url" -> {
                if (value.isEmpty()) yield null;
                try {
                    URI uri = URI.create(value);
                    if (uri.getHost() != null && uri.getUserInfo() == null
                            && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) yield null;
                } catch (IllegalArgumentException ignored) { }
                yield "Use an absolute HTTP(S) URL.";
            }
            case "backups.offsite.url" -> {
                try { OffsiteBackups.endpoint(value); yield null; }
                catch (IllegalArgumentException invalid) { yield invalid.getMessage(); }
            }
            case "backups.offsite.authorization-env" -> value.matches("[A-Za-z_][A-Za-z0-9_]*")
                    ? null : "Enter an environment variable name.";
            default -> null;
        };
    }
}
