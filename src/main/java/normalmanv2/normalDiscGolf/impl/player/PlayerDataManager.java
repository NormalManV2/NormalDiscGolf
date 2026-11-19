package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.impl.player.profile.PlayerProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final Map<UUID, PlayerData> playerData;
    private final PlayerProfileStore profileStore;

    public PlayerDataManager(PlayerProfileStore profileStore) {
        this.playerData = new HashMap<>();
        this.profileStore = profileStore;
    }

    public void registerPlayerData(UUID uuid, PlayerData playerData) {
        this.playerData.put(uuid, playerData);
    }

    public void unregisterPlayerData(UUID uuid) {
        this.playerData.remove(uuid);
    }

    public PlayerData getOrCreatePlayerData(UUID uuid) {
        return this.getOrLoadPlayerData(uuid);
    }

    public PlayerData getOrLoadPlayerData(UUID uuid) {
        return this.playerData.computeIfAbsent(uuid, this.profileStore::load);
    }

    public boolean hasPlayerData(UUID uuid) {
        return this.playerData.containsKey(uuid);
    }

    public PlayerData getDataByPlayer(UUID playerId) {
        return this.playerData.get(playerId);
    }

    public boolean savePlayerData(UUID playerId) {
        PlayerData data = this.playerData.get(playerId);
        if (data == null) {
            return false;
        }

        return this.profileStore.save(playerId, this.getPlayerName(playerId), data);
    }

    public void reloadPlayerData(UUID playerId) throws java.io.IOException, org.bukkit.configuration.InvalidConfigurationException {
        PlayerData loaded = this.profileStore.reload(playerId);
        PlayerData current = this.playerData.get(playerId);
        if (current == null) {
            this.playerData.put(playerId, loaded);
            return;
        }
        var rating = loaded.getRating();
        current.getRating().restore(rating.getTotalRounds(), rating.getTotalScore(),
                rating.getAverageScore(), rating.getRating(), rating.getDivision());
        loaded.getSkills().asMap().forEach((name, skill) ->
                current.getSkills().asMap().get(name).restoreProgress(skill.getLevel(), skill.getXp()));
    }

    public void saveAll() {
        for (UUID playerId : this.playerData.keySet()) {
            this.savePlayerData(playerId);
        }
    }

    public void clear() {
        this.playerData.clear();
    }

    private String getPlayerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        String playerName = player.getName();
        return playerName != null ? playerName : playerId.toString();
    }
}
