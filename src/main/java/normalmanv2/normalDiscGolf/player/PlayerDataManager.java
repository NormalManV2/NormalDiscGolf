package normalmanv2.normalDiscGolf.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final Map<UUID, PlayerData> playerData;

    public PlayerDataManager() {
        this.playerData = new HashMap<>();
    }

    public void registerPlayerData(UUID uuid, PlayerData playerData) {
        this.playerData.put(uuid, playerData);
    }

    public void unregisterPlayerData(UUID uuid) {
        if (!this.playerData.containsKey(uuid)) {
            return;
        }
        this.playerData.remove(uuid);
    }

    public PlayerData getDataByPlayer(UUID playerId) {
        if (!this.playerData.containsKey(playerId)) {
            return null;
        }
        return this.playerData.get(playerId);
    }

}
