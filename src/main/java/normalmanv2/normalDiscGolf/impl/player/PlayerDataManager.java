package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.impl.player.profile.PlayerProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {
    public enum PrizeResult { PAID, ALREADY_PAID, SAVE_FAILED }

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

    public PrizeResult creditCompetitionPrize(UUID id, String competitionId, int credits) {
        PlayerData data = getDataByPlayer(id);
        if (data == null) {
            try {
                data = profileStore.loadForCompetitionPrize(id);
            } catch (IOException | InvalidConfigurationException failure) {
                return PrizeResult.SAVE_FAILED;
            }
        }
        var receipts = data.getCompetitionRewards();
        if (receipts.contains(competitionId)) return PrizeResult.ALREADY_PAID;
        var bag = data.getDiscBag();
        var updated = new HashSet<>(receipts);
        updated.add(competitionId);
        data.setDiscBag(bag.earn(credits));
        data.setCompetitionRewards(updated);
        boolean saved = false;
        try {
            saved = profileStore.save(id, this.getPlayerName(id), data);
            return saved ? PrizeResult.PAID : PrizeResult.SAVE_FAILED;
        } finally {
            if (!saved) {
                data.setDiscBag(bag);
                data.setCompetitionRewards(receipts);
            }
        }
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
                rating.getAverageScore(), rating.getRating(), rating.getDivision(), rating.getPeakRating(), rating.getLastRoundRating());
        loaded.getSkills().asMap().forEach((name, skill) ->
                current.getSkills().asMap().get(name).restoreProgress(skill.getLevel(), skill.getXp()));
        current.setDiscBag(loaded.getDiscBag());
        current.setUnavailableDiscIds(loaded.getUnavailableDiscIds());
        current.setClubhouse(loaded.getClubhouse());
        current.setDecalLocker(loaded.getDecalLocker());
        current.setCompetitionRewards(loaded.getCompetitionRewards());
    }

    public boolean saveAll() {
        boolean saved = true;
        for (UUID playerId : this.playerData.keySet()) {
            saved &= this.savePlayerData(playerId);
        }
        return saved;
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
