package normalmanv2.normalDiscGolf.impl.competition;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

public final class RankedSeasonStore {
    private final Path file;

    public RankedSeasonStore(Path file) { this.file = file; }

    public NavigableMap<Integer, RankedSeason> load() throws IOException {
        var seasons = new TreeMap<Integer, RankedSeason>();
        if (Files.notExists(file)) return seasons;
        var yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            if (yaml.getInt("version") != 1) throw new IllegalArgumentException("Unknown ranked season file version");
            var section = yaml.getConfigurationSection("seasons");
            if (section == null) throw new IllegalArgumentException("Missing seasons");
            for (String key : section.getKeys(false)) {
                var saved = section.getConfigurationSection(key);
                if (saved == null) throw new IllegalArgumentException("Invalid season " + key);
                var entries = new HashMap<UUID, RankedSeason.Entry>();
                var scores = saved.getConfigurationSection("entries");
                if (scores != null) for (String player : scores.getKeys(false)) {
                    String path = player + ".";
                    entries.put(UUID.fromString(player), new RankedSeason.Entry(scores.getString(path + "name", player),
                            scores.getInt(path + "rounds"), scores.getLong(path + "holes"),
                            scores.getLong(path + "rating-points")));
                }
                int number = Integer.parseInt(key);
                seasons.put(number, new RankedSeason(number, Instant.parse(saved.getString("starts", "")),
                        Instant.parse(saved.getString("ends", "")), saved.getInt("minimum-rounds"),
                        saved.getIntegerList("prizes"), entries, saved.getBoolean("prizes-paid")));
            }
            RankedSeason previous = null;
            for (var season : seasons.values()) {
                if (previous != null && season.starts().isBefore(previous.ends()))
                    throw new IllegalArgumentException("Overlapping ranked seasons");
                previous = season;
            }
        } catch (InvalidConfigurationException | IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new IOException("Could not read ranked seasons", invalid);
        }
        return seasons;
    }

    public void save(NavigableMap<Integer, RankedSeason> seasons) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.createSection("seasons");
        for (var season : seasons.values()) {
            var section = yaml.createSection("seasons." + season.number());
            section.set("starts", season.starts().toString());
            section.set("ends", season.ends().toString());
            section.set("minimum-rounds", season.minimumRounds());
            section.set("prizes", season.prizes());
            section.set("prizes-paid", season.prizesPaid());
            season.entries().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                var saved = section.createSection("entries." + entry.getKey());
                saved.set("name", entry.getValue().name());
                saved.set("rounds", entry.getValue().rounds());
                saved.set("holes", entry.getValue().holes());
                saved.set("rating-points", entry.getValue().ratingPoints());
            });
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "ranked-seasons-", ".tmp");
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
