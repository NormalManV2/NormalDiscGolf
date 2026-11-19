package normalmanv2.normalDiscGolf.impl.player.session;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PlayerSessionManager {

    private final Map<UUID, PlayerRoundSession> roundSessions;

    public PlayerSessionManager() {
        this.roundSessions = new HashMap<>();
    }

    public void beginRound(GameRound round) {
        for (UUID playerId : this.getRoundPlayers(round)) {
            if (this.roundSessions.containsKey(playerId)) {
                continue;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }

            this.roundSessions.put(playerId, PlayerRoundSession.capture(player, round.getId()));
        }
    }

    public boolean restorePlayer(Player player) {
        PlayerRoundSession session = this.roundSessions.remove(player.getUniqueId());
        return session != null && session.restore(player);
    }

    public int restoreRoundSessions(GameRound round) {
        int restoredPlayers = 0;
        for (PlayerRoundSession session : new ArrayList<>(this.roundSessions.values())) {
            if (!session.getRoundId().equals(round.getId())) {
                continue;
            }

            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player == null) {
                continue;
            }

            if (session.restore(player)) {
                restoredPlayers++;
            }
            this.roundSessions.remove(session.getPlayerId());
        }
        return restoredPlayers;
    }

    public int restoreAllOnlinePlayers() {
        int restoredPlayers = 0;
        for (PlayerRoundSession session : new ArrayList<>(this.roundSessions.values())) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player == null) {
                continue;
            }

            if (session.restore(player)) {
                restoredPlayers++;
            }
            this.roundSessions.remove(session.getPlayerId());
        }
        return restoredPlayers;
    }

    public boolean hasActiveSession(UUID playerId) {
        return this.roundSessions.containsKey(playerId);
    }

    public Optional<String> getRoundId(UUID playerId) {
        PlayerRoundSession session = this.roundSessions.get(playerId);
        return session == null ? Optional.empty() : Optional.of(session.getRoundId());
    }

    public void clear() {
        this.roundSessions.clear();
    }

    private Set<UUID> getRoundPlayers(GameRound round) {
        Set<UUID> playerIds = new HashSet<>();
        for (Team team : round.getTeams()) {
            playerIds.addAll(team.getTeamMembers());
        }
        return playerIds;
    }
}
