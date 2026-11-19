package normalmanv2.normalDiscGolf.impl.player.session;

import org.bukkit.entity.Player;

import java.util.UUID;

public final class PlayerRoundSession {

    private final UUID playerId;
    private final String roundId;
    private final PlayerStateSnapshot stateSnapshot;
    private boolean restored;

    private PlayerRoundSession(UUID playerId, String roundId, PlayerStateSnapshot stateSnapshot) {
        this.playerId = playerId;
        this.roundId = roundId;
        this.stateSnapshot = stateSnapshot;
    }

    public static PlayerRoundSession capture(Player player, String roundId) {
        return new PlayerRoundSession(
                player.getUniqueId(),
                roundId,
                PlayerStateSnapshot.capture(player));
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public String getRoundId() {
        return this.roundId;
    }

    public boolean restore(Player player) {
        if (this.restored) {
            return false;
        }

        this.stateSnapshot.restore(player);
        this.restored = true;
        return true;
    }
}
