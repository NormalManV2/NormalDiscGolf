package normalmanv2.normalDiscGolf.impl.competition;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationSettings;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.grid.HoleArchetype;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Main-thread access to date-specific layouts and best scores. */
public final class DailyCompetition {
    private final Path directory;
    private final Supplier<? extends ConfigurationSection> config;
    private final Clock utc;
    private final DailyGhostStore ghosts;
    private DailyLeaderboard current;

    public DailyCompetition(Path directory, Supplier<? extends ConfigurationSection> config, Clock clock) {
        this.directory = directory;
        this.config = config;
        this.utc = clock.withZone(ZoneOffset.UTC);
        this.ghosts = new DailyGhostStore(directory.resolve("ghosts"));
    }

    public LocalDate todayDate() { return LocalDate.now(utc); }

    public DailyLeaderboard today() throws IOException { return open(todayDate(), true, true); }

    public DailyLeaderboard publishedToday() throws IOException { return open(todayDate(), true, false); }

    public DailyLeaderboard board(LocalDate date) throws IOException {
        boolean isToday = date.equals(todayDate());
        return open(date, isToday, isToday);
    }

    private DailyLeaderboard open(LocalDate date, boolean isToday, boolean create) throws IOException {
        if (isToday && current != null && current.course().date().equals(date)) return current;
        Path file = file(date);
        DailyLeaderboard board;
        if (Files.notExists(file)) {
            if (!create) return null;
            board = new DailyLeaderboard(DailyCourse.createDefinition(date, config.get()), Map.of());
            save(board);
        } else {
            board = load(date, file);
        }
        if (isToday) current = board;
        return board;
    }

    public DailyLeaderboard submit(DailyCourse.Definition played, Collection<StrokeStandings.Entry> results) throws IOException {
        DailyLeaderboard board = board(played.date());
        if (board == null || !board.course().equals(played))
            throw new IOException("The Daily Course definition changed for " + played.date());
        DailyLeaderboard updated = board.withResults(results);
        if (updated != board) {
            save(updated);
            if (played.date().equals(todayDate())) current = updated;
        }
        return updated;
    }

    public DailyGhost ghost(DailyLeaderboard board, UUID player) throws IOException {
        var entry = board.entries().get(player);
        return entry == null ? null : ghosts.load(board.course(), entry);
    }

    public boolean saveGhost(DailyLeaderboard board, StrokeStandings.Entry attempt, DailyGhost ghost) throws IOException {
        var best = board.entries().get(attempt.player());
        return best != null && best.strokes() == attempt.strokes() && ghosts.saveIfMissing(board.course(), best, ghost);
    }

    private Path file(LocalDate date) { return directory.resolve(date + ".yml"); }

    private DailyLeaderboard load(LocalDate date, Path file) throws IOException {
        try {
            var yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            if (yaml.getInt("version") != 1 || !date.toString().equals(yaml.getString("date")))
                throw new IllegalArgumentException("Invalid version or date");
            CourseTerrain terrain = CourseTerrain.parse(yaml.getString("terrain", ""));
            int holes = yaml.getInt("holes"), strokeLimit = yaml.getInt("stroke-limit");
            if (terrain == null || holes < 1 || holes > 18 || strokeLimit < 3 || !yaml.isLong("seed") && !yaml.isInt("seed"))
                throw new IllegalArgumentException("Missing course settings");
            var archetypeNames = yaml.getStringList("archetypes");
            var archetypes = archetypeNames.stream().map(HoleArchetype::parse).filter(Objects::nonNull).toList();
            if (archetypes.isEmpty() || archetypes.size() != archetypeNames.size())
                throw new IllegalArgumentException("Invalid hole archetypes");
            var generation = new CourseGenerationSettings(terrain, yaml.getInt("elevation"), yaml.getInt("fairway-width"),
                    yaml.getDouble("obstacle-density"), yaml.getDouble("hazard-density"), archetypes);
            var course = new DailyCourse.Definition(date, yaml.getLong("seed"), holes, generation, yaml.getBoolean("wind"), strokeLimit);
            var section = yaml.getConfigurationSection("scores");
            Map<UUID, StrokeStandings.Entry> entries = new HashMap<>();
            if (section != null) for (String key : section.getKeys(false)) {
                UUID player = UUID.fromString(key);
                int strokes = section.getInt(key + ".strokes");
                String name = section.getString(key + ".name", "");
                if (strokes < holes || name.isBlank() || !section.isInt(key + ".relative-to-par"))
                    throw new IllegalArgumentException("Invalid leaderboard entry");
                entries.put(player, new StrokeStandings.Entry(player, name, strokes, section.getInt(key + ".relative-to-par")));
            }
            return new DailyLeaderboard(course, entries);
        } catch (InvalidConfigurationException | IllegalArgumentException failure) {
            throw new IOException("Invalid Daily Course file for " + date, failure);
        }
    }

    private void save(DailyLeaderboard board) throws IOException {
        var yaml = new YamlConfiguration();
        var course = board.course();
        var generation = course.generation();
        yaml.set("version", 1);
        yaml.set("date", course.date().toString());
        yaml.set("seed", course.seed());
        yaml.set("holes", course.holes());
        yaml.set("wind", course.wind());
        yaml.set("stroke-limit", course.strokeLimit());
        yaml.set("terrain", generation.terrain().id());
        yaml.set("elevation", generation.elevation());
        yaml.set("fairway-width", generation.fairwayWidth());
        yaml.set("obstacle-density", generation.obstacleDensity());
        yaml.set("hazard-density", generation.hazardDensity());
        yaml.set("archetypes", generation.archetypes().stream().map(HoleArchetype::name).toList());
        for (var row : board.standings()) {
            var entry = row.entry();
            String key = "scores." + entry.player();
            yaml.set(key + ".name", entry.name());
            yaml.set(key + ".strokes", entry.strokes());
            yaml.set(key + ".relative-to-par", entry.relativeToPar());
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "daily-", ".tmp");
        try {
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, file(course.date()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, file(course.date()), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
