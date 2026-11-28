package normalmanv2.normalDiscGolf.impl.player.profile;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.DiscCatalog;
import normalmanv2.normalDiscGolf.impl.player.DecalLocker;
import normalmanv2.normalDiscGolf.impl.player.DiscBag;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseMedal;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseProgress;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.skill.Skill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerProfileStore {

    private static final int SCHEMA_VERSION = 8;

    private final NormalDiscGolfPlugin plugin;
    private final Path profileFolder;
    private final DiscCatalog catalog;

    public PlayerProfileStore(NormalDiscGolfPlugin plugin, DiscCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.profileFolder = plugin.getDataFolder().toPath().resolve("player-profiles");
        this.ensureProfileFolderExists();
    }

    public PlayerData load(UUID playerId) {
        Path profilePath = this.getProfilePath(playerId);
        if (Files.notExists(profilePath)) {
            return new PlayerData();
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(profilePath.toFile());
        return readProfile(configuration);
    }

    public PlayerData reload(UUID playerId) throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(this.getProfilePath(playerId).toFile());
        return readProfile(configuration);
    }

    public PlayerData loadForCompetitionPrize(UUID playerId) throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        return Files.notExists(getProfilePath(playerId)) ? new PlayerData() : reload(playerId);
    }

    private PlayerData readProfile(YamlConfiguration configuration) {
        PlayerData data = new PlayerData();
        this.loadRating(configuration, data.getRating());
        this.loadSkills(configuration, data.getSkills());
        var unlocked = new HashSet<DiscModel>();
        var unavailable = new HashSet<String>();
        for (String id : configuration.getStringList("disc-shop.unlocked")) {
            DiscModel model = catalog.find(id);
            if (model != null) unlocked.add(model);
            else unavailable.add(id);
        }
        data.setUnavailableDiscIds(unavailable);
        var loadout = new EnumMap<DiscType, DiscModel>(DiscType.class);
        for (DiscType type : DiscType.values()) {
            DiscModel model = catalog.find(configuration.getString("disc-shop.loadout." + type.name()));
            if (model != null && model.type() == type && (model.price() == 0 || unlocked.contains(model))) {
                loadout.put(type, model);
            }
        }
        long credits = configuration.getLong("disc-shop.credits");
        if (configuration.isList("disc-shop.bag.discs")) {
            var packed = configuration.getStringList("disc-shop.bag.discs").stream().map(catalog::find)
                    .filter(java.util.Objects::nonNull).toList();
            data.setDiscBag(new DiscBag(credits, unlocked, packed,
                    configuration.getInt("disc-shop.bag.capacity", DiscBag.STARTING_CAPACITY)));
        } else {
            data.setDiscBag(DiscBag.migrate(credits, unlocked, loadout));
        }
        var decals = EnumSet.noneOf(ScoreDecal.class);
        for (String id : configuration.getStringList("score-decals.unlocked")) {
            ScoreDecal decal = ScoreDecal.find(id);
            if (decal != null && decal != ScoreDecal.NONE) decals.add(decal);
        }
        ScoreDecal equipped = ScoreDecal.find(configuration.getString("score-decals.equipped", "none"));
        data.setDecalLocker(new DecalLocker(decals, equipped));
        data.setClubhouse(loadClubhouse(configuration));
        var receipts = new HashSet<>(configuration.getStringList("competition-rewards"));
        receipts.addAll(configuration.getStringList("tournament-rewards"));
        data.setCompetitionRewards(receipts);
        return data;
    }

    public boolean save(UUID playerId, String playerName, PlayerData playerData) {
        this.ensureProfileFolderExists();

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("schema-version", SCHEMA_VERSION);
        configuration.set("player.uuid", playerId.toString());
        configuration.set("player.name", playerName);
        this.saveRating(configuration, playerData.getRating());
        this.saveSkills(configuration, playerData.getSkills());
        var bag = playerData.getDiscBag();
        configuration.set("disc-shop.credits", bag.credits());
        configuration.set("disc-shop.unlocked", java.util.stream.Stream.concat(
                bag.unlocked().stream().map(DiscModel::id), playerData.getUnavailableDiscIds().stream()).distinct().sorted().toList());
        configuration.set("disc-shop.bag.capacity", bag.capacity());
        configuration.set("disc-shop.bag.discs", bag.packed().stream().map(DiscModel::id).toList());
        var decals = playerData.getDecalLocker();
        configuration.set("score-decals.unlocked", decals.unlocked().stream().map(ScoreDecal::id).sorted().toList());
        configuration.set("score-decals.equipped", decals.equipped().id());
        saveClubhouse(configuration, playerData.getClubhouse());
        configuration.set("competition-rewards", playerData.getCompetitionRewards().stream().sorted().toList());

        try {
            Path target = getProfilePath(playerId);
            Path temporary = Files.createTempFile(profileFolder, playerId.toString(), ".tmp");
            try {
                configuration.save(temporary.toFile());
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return true;
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save player profile " + playerId, exception);
            return false;
        }
    }

    private static ClubhouseProgress loadClubhouse(YamlConfiguration configuration) {
        var section = configuration.getConfigurationSection("clubhouse");
        if (section == null) return new ClubhouseProgress();
        var medals = EnumSet.noneOf(ClubhouseMedal.class);
        for (String name : section.getStringList("medals")) {
            try { medals.add(ClubhouseMedal.valueOf(name)); }
            catch (IllegalArgumentException ignored) { /* Ignore unavailable medals from old profiles. */ }
        }
        var records = new LinkedHashMap<String, Integer>();
        var cards = section.getConfigurationSection("best-cards");
        if (cards != null) for (String key : cards.getKeys(false)) {
            if (key.matches("(ffa|doubles|best-shot|worst-shot|one-disc|disc-roulette)-([1-9]|1[0-8])") && cards.isInt(key)) records.put(key, cards.getInt(key));
        }
        LocalDate date = null;
        String dateText = section.getString("daily.date");
        if (dateText != null) {
            try { date = LocalDate.parse(dateText); }
            catch (DateTimeParseException ignored) { /* An unknown date has no current daily progress. */ }
        }
        double longest = section.getDouble("longest-basket");
        return new ClubhouseProgress(Math.max(0, section.getLong("baskets")), Math.max(0, section.getLong("under-par")),
                Math.max(0, section.getLong("aces")), Double.isFinite(longest) ? Math.max(0, longest) : 0,
                Math.max(0, section.getInt("best-streak")), Math.max(0, section.getLong("qualifying-rounds")),
                records, medals, date, section.getInt("daily.count"));
    }

    private static void saveClubhouse(YamlConfiguration configuration, ClubhouseProgress progress) {
        configuration.set("clubhouse.baskets", progress.baskets());
        configuration.set("clubhouse.under-par", progress.underPar());
        configuration.set("clubhouse.aces", progress.aces());
        configuration.set("clubhouse.longest-basket", progress.longestBasket());
        configuration.set("clubhouse.best-streak", progress.bestStreak());
        configuration.set("clubhouse.qualifying-rounds", progress.qualifyingRounds());
        progress.bestCards().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> configuration.set("clubhouse.best-cards." + entry.getKey(), entry.getValue()));
        configuration.set("clubhouse.medals", progress.medals().stream().map(Enum::name).sorted().toList());
        if (progress.dailyDate() != null) configuration.set("clubhouse.daily.date", progress.dailyDate().toString());
        configuration.set("clubhouse.daily.count", progress.dailyCount());
    }

    private void loadRating(YamlConfiguration configuration, PDGARating rating) {
        String divisionName = configuration.getString("rating.division", Division.JUNIOR.name());
        Division division = this.parseDivision(divisionName);
        rating.restore(
                configuration.getInt("rating.total-rounds", 0),
                configuration.getDouble("rating.total-score", 0),
                configuration.getDouble("rating.average-score", 0),
                configuration.getInt("rating.rating", 0),
                division,
                configuration.getInt("rating.peak", 0),
                configuration.getInt("rating.last-round", 0));
    }

    private void saveRating(YamlConfiguration configuration, PDGARating rating) {
        configuration.set("rating.total-rounds", rating.getTotalRounds());
        configuration.set("rating.total-score", rating.getTotalScore());
        configuration.set("rating.average-score", rating.getAverageScore());
        configuration.set("rating.rating", rating.getRating());
        configuration.set("rating.division", rating.getDivision().name());
        configuration.set("rating.peak", rating.getPeakRating());
        configuration.set("rating.last-round", rating.getLastRoundRating());
    }

    private void loadSkills(YamlConfiguration configuration, PlayerSkills playerSkills) {
        for (Map.Entry<String, Skill> entry : playerSkills.asMap().entrySet()) {
            ConfigurationSection section = configuration.getConfigurationSection("skills." + entry.getKey());
            if (section == null) {
                continue;
            }

            entry.getValue().restoreProgress(
                    section.getInt("level", entry.getValue().getLevel()),
                    section.getDouble("xp", entry.getValue().getXp()));
        }
    }

    private void saveSkills(YamlConfiguration configuration, PlayerSkills playerSkills) {
        for (Map.Entry<String, Skill> entry : new java.util.TreeMap<>(playerSkills.asMap()).entrySet()) {
            Skill skill = entry.getValue();
            String path = "skills." + entry.getKey();
            configuration.set(path + ".level", skill.getLevel());
            configuration.set(path + ".max-level", skill.getMaxLevel());
            configuration.set(path + ".xp", skill.getXp());
            configuration.set(path + ".xp-to-next", skill.getXpToNext());
        }
    }

    private Division parseDivision(String divisionName) {
        if (divisionName == null) {
            return Division.JUNIOR;
        }

        try {
            return Division.valueOf(divisionName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Division.JUNIOR;
        }
    }

    private Path getProfilePath(UUID playerId) {
        return this.profileFolder.resolve(playerId + ".yml");
    }

    private void ensureProfileFolderExists() {
        try {
            Files.createDirectories(this.profileFolder);
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not create player profile folder " + this.profileFolder, exception);
        }
    }
}
