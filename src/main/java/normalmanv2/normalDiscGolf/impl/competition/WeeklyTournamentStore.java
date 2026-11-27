package normalmanv2.normalDiscGolf.impl.competition;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public final class WeeklyTournamentStore {
    private final Path file;

    public WeeklyTournamentStore(Path file) { this.file = file; }

    public TreeMap<Instant, WeeklyTournament> load() throws IOException {
        var loaded = new TreeMap<Instant, WeeklyTournament>();
        if (Files.notExists(file)) return loaded;
        try {
            var yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            if (yaml.getInt("version") != 1) throw new IllegalArgumentException("Unknown tournament file version");
            var events = yaml.getConfigurationSection("events");
            if (events != null) for (String key : events.getKeys(false)) {
                var section = events.getConfigurationSection(key);
                Instant start = Instant.ofEpochSecond(Long.parseLong(key));
                loaded.put(start, readEvent(start, section));
            }
        } catch (InvalidConfigurationException | RuntimeException failure) {
            throw new IOException("Invalid weekly tournaments file", failure);
        }
        return loaded;
    }

    private static WeeklyTournament readEvent(Instant start, ConfigurationSection section) {
        var definition = new WeeklyTournament.Definition(start, Instant.parse(section.getString("opens", "")),
                section.getInt("holes"), section.getInt("minimum"), section.getInt("capacity"), section.getIntegerList("prizes"));
        var status = WeeklyTournament.Status.valueOf(section.getString("status", ""));
        Map<UUID, String> entrants = new HashMap<>();
        var roster = section.getConfigurationSection("entrants");
        if (roster != null) for (String id : roster.getKeys(false)) entrants.put(UUID.fromString(id), roster.getString(id));
        var starters = section.getStringList("starters").stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
        var scores = section.getMapList("results").stream().map(row -> new StrokeStandings.Entry(
                UUID.fromString((String)row.get("player")), (String)row.get("name"),
                (Integer)row.get("strokes"), (Integer)row.get("relative"))).toList();
        if (!entrants.keySet().containsAll(starters) || scores.stream().anyMatch(score ->
                !starters.contains(score.player()) || score.strokes() < definition.holes() || score.name().isBlank())
                || scores.stream().map(StrokeStandings.Entry::player).distinct().count() != scores.size())
            throw new IllegalArgumentException("Invalid tournament roster or scores");
        return new WeeklyTournament(definition, status, entrants, starters, scores,
                section.getString("note", ""), section.getBoolean("prizes-paid"));
    }

    public void save(Map<Instant, WeeklyTournament> events) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("version", 1);
        events.forEach((start, event) -> {
            String path = "events." + start.getEpochSecond() + ".";
            var definition = event.definition();
            yaml.set(path + "opens", definition.opens().toString());
            yaml.set(path + "holes", definition.holes());
            yaml.set(path + "minimum", definition.minimum());
            yaml.set(path + "capacity", definition.capacity());
            yaml.set(path + "prizes", definition.prizes());
            yaml.set(path + "status", event.status().name());
            event.entrants().forEach((id, name) -> yaml.set(path + "entrants." + id, name));
            yaml.set(path + "starters", event.starters().stream().map(UUID::toString).sorted().toList());
            yaml.set(path + "results", event.results().stream().map(score -> Map.of(
                    "player", score.player().toString(), "name", score.name(),
                    "strokes", score.strokes(), "relative", score.relativeToPar())).toList());
            yaml.set(path + "note", event.note());
            yaml.set(path + "prizes-paid", event.prizesPaid());
        });
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "weekly-", ".tmp");
        try {
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
