package normalmanv2.normalDiscGolf.impl.player.profile;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.player.DecalLocker;
import normalmanv2.normalDiscGolf.impl.player.DiscBag;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.skill.Skill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerProfileStore {

    private static final int SCHEMA_VERSION = 3;

    private final NormalDiscGolfPlugin plugin;
    private final Path profileFolder;

    public PlayerProfileStore(NormalDiscGolfPlugin plugin) {
        this.plugin = plugin;
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

    private PlayerData readProfile(YamlConfiguration configuration) {
        PlayerData data = new PlayerData();
        this.loadRating(configuration, data.getRating());
        this.loadSkills(configuration, data.getSkills());
        var unlocked = EnumSet.noneOf(DiscModel.class);
        for (String id : configuration.getStringList("disc-shop.unlocked")) {
            DiscModel model = DiscModel.find(id);
            if (model != null) unlocked.add(model);
        }
        var loadout = new EnumMap<DiscType, DiscModel>(DiscType.class);
        for (DiscType type : DiscType.values()) {
            DiscModel model = DiscModel.find(configuration.getString("disc-shop.loadout." + type.name()));
            if (model != null && model.type() == type && (model.price() == 0 || unlocked.contains(model))) {
                loadout.put(type, model);
            }
        }
        data.setDiscBag(new DiscBag(configuration.getLong("disc-shop.credits"), unlocked, loadout));
        var decals = EnumSet.noneOf(ScoreDecal.class);
        for (String id : configuration.getStringList("score-decals.unlocked")) {
            ScoreDecal decal = ScoreDecal.find(id);
            if (decal != null && decal != ScoreDecal.NONE) decals.add(decal);
        }
        ScoreDecal equipped = ScoreDecal.find(configuration.getString("score-decals.equipped", "none"));
        data.setDecalLocker(new DecalLocker(decals, equipped));
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
        configuration.set("disc-shop.unlocked", bag.unlocked().stream().map(DiscModel::id).sorted().toList());
        bag.loadout().forEach((type, model) -> configuration.set("disc-shop.loadout." + type.name(), model.id()));
        var decals = playerData.getDecalLocker();
        configuration.set("score-decals.unlocked", decals.unlocked().stream().map(ScoreDecal::id).sorted().toList());
        configuration.set("score-decals.equipped", decals.equipped().id());

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

    private void loadRating(YamlConfiguration configuration, PDGARating rating) {
        String divisionName = configuration.getString("rating.division", Division.JUNIOR.name());
        Division division = this.parseDivision(divisionName);
        rating.restore(
                configuration.getInt("rating.total-rounds", 0),
                configuration.getDouble("rating.total-score", 0),
                configuration.getDouble("rating.average-score", 0),
                configuration.getInt("rating.rating", 0),
                division);
    }

    private void saveRating(YamlConfiguration configuration, PDGARating rating) {
        configuration.set("rating.total-rounds", rating.getTotalRounds());
        configuration.set("rating.total-score", rating.getTotalScore());
        configuration.set("rating.average-score", rating.getAverageScore());
        configuration.set("rating.rating", rating.getRating());
        configuration.set("rating.division", rating.getDivision().name());
    }

    private void loadSkills(YamlConfiguration configuration, PlayerSkills playerSkills) {
        for (Map.Entry<String, Skill> entry : this.getSkills(playerSkills).entrySet()) {
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
        for (Map.Entry<String, Skill> entry : this.getSkills(playerSkills).entrySet()) {
            Skill skill = entry.getValue();
            String path = "skills." + entry.getKey();
            configuration.set(path + ".level", skill.getLevel());
            configuration.set(path + ".max-level", skill.getMaxLevel());
            configuration.set(path + ".xp", skill.getXp());
            configuration.set(path + ".xp-to-next", skill.getXpToNext());
        }
    }

    private Map<String, Skill> getSkills(PlayerSkills playerSkills) {
        Map<String, Skill> skills = new LinkedHashMap<>();
        skills.put("accuracy", playerSkills.getAccuracy());
        skills.put("backhand", playerSkills.getBackhand());
        skills.put("flex", playerSkills.getFlex());
        skills.put("forehand", playerSkills.getForehand());
        skills.put("form", playerSkills.getForm());
        skills.put("power", playerSkills.getPower());
        skills.put("roller", playerSkills.getRoller());
        skills.put("s-curve", playerSkills.getSCurve());
        skills.put("thumber", playerSkills.getThumber());
        skills.put("tomahawk", playerSkills.getTomahawk());
        return skills;
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
