package normalmanv2.normalDiscGolf.impl.service;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QueueService {

    private final Map<String, GameRound> queuedRounds;

    public QueueService() {
        this.queuedRounds = new HashMap<>();
    }

    public void queueRound(GameRound round) {
        this.queuedRounds.put(round.getId(), round);
    }

    public void removeRound(String roundId) {
        this.queuedRounds.remove(roundId);
    }

    public List<GameRound> getQueuedRounds() {
        return List.copyOf(this.queuedRounds.values());
    }

    public void addPlayerToRound(UUID playerId, String roundId) {
        GameRound round = this.queuedRounds.get(roundId);

        if (round instanceof FFARound) {
            round.addTeam(new TeamImpl(playerId));
        } else if (round instanceof DoublesRound) {

        }
    }

}
