package normalmanv2.normalDiscGolf.impl.course.library;

import normalmanv2.normalDiscGolf.impl.competition.FlightGhost;
import normalmanv2.normalDiscGolf.impl.competition.FlightGhostFile;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stroke-play bests shared by all copies of a saved layout. Ghost files are private to each player. */
public final class CourseRecords {
    private final Path directory;

    public CourseRecords(Path directory) { this.directory = directory; }

    public record Update(Map<UUID, StrokeStandings.Entry> best, Set<UUID> improved) {}

    public Map<UUID, StrokeStandings.Entry> scores(SavedCourse course) throws IOException {
        Path path = boardPath(course);
        if (Files.notExists(path)) return Map.of();
        try {
            var yaml = new YamlConfiguration();
            yaml.load(path.toFile());
            if (yaml.getInt("version") != 1 || !course.layoutId().toString().equals(yaml.getString("layout")))
                throw new IOException("Records do not match the saved layout");
            var scores = yaml.getConfigurationSection("scores");
            var entries = new HashMap<UUID, StrokeStandings.Entry>();
            if (scores != null) for (String key : scores.getKeys(false)) {
                UUID player = UUID.fromString(key);
                String name = scores.getString(key + ".name", "");
                int strokes = scores.getInt(key + ".strokes");
                if (name.isBlank() || strokes < course.holes()) throw new IOException("Invalid course record");
                entries.put(player, new StrokeStandings.Entry(player, name, strokes, strokes - course.par()));
            }
            return Map.copyOf(entries);
        } catch (InvalidConfigurationException | IllegalArgumentException invalid) {
            throw new IOException("Invalid course records file", invalid);
        }
    }

    public synchronized Update submit(SavedCourse course, Collection<StrokeStandings.Entry> attempts) throws IOException {
        var best = new HashMap<>(scores(course));
        var improved = new HashSet<UUID>();
        for (var attempt : attempts) {
            if (attempt.strokes() < course.holes() || attempt.relativeToPar() != attempt.strokes() - course.par()
                    || attempt.name().isBlank()) throw new IOException("Score does not match the saved layout");
            var previous = best.get(attempt.player());
            if (previous != null && previous.strokes() <= attempt.strokes()) continue;
            best.put(attempt.player(), attempt);
            improved.add(attempt.player());
        }
        if (!improved.isEmpty()) {
            var yaml = new YamlConfiguration();
            yaml.set("version", 1);
            yaml.set("layout", course.layoutId().toString());
            for (var entry : best.values()) {
                String key = "scores." + entry.player();
                yaml.set(key + ".strokes", entry.strokes());
                yaml.set(key + ".name", entry.name());
            }
            FileCourseLibraryStore.writeAtomically(boardPath(course), yaml.saveToString());
        }
        return new Update(Map.copyOf(best), Set.copyOf(improved));
    }

    public FlightGhost ghost(SavedCourse course, StrokeStandings.Entry best) throws IOException {
        return FlightGhostFile.load(ghostPath(course, best), course.seed(), course.holes(), best.strokes(), best.strokes());
    }

    public boolean saveGhost(SavedCourse course, StrokeStandings.Entry best, FlightGhost ghost) throws IOException {
        return FlightGhostFile.saveIfMissing(ghostPath(course, best), course.seed(), course.holes(),
                best.strokes(), best.strokes(), ghost);
    }

    private Path boardPath(SavedCourse course) {
        return directory.resolve(course.layoutId() + ".yml");
    }

    private Path ghostPath(SavedCourse course, StrokeStandings.Entry best) {
        return directory.resolve(course.layoutId().toString()).resolve(best.player() + "-" + best.strokes() + ".ndg");
    }
}
