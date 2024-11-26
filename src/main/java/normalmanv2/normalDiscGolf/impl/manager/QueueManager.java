package normalmanv2.normalDiscGolf.impl.manager;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.queue.RoundQueue;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.service.QueueService;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;

import java.util.List;
import java.util.UUID;

public class QueueManager {

    private final QueueService queueService;

    public QueueManager(QueueService queueService) {
        this.queueService = queueService;
    }

    public void queueRound(GameRound round) {
        this.queueService.queueRound(new RoundQueue(round.getId(), round));
    }

    public void removeRound(String roundId) {
        this.queueService.removeQueue(roundId);
    }

    public List<RoundQueue> getQueuedRounds() {
        return List.copyOf(this.queueService.getQueuedRounds().values());
    }

    public void addPlayerToRound(UUID playerId, String roundId) {
        GameRound round = this.queueService.getQueue(roundId).round();

        if (round instanceof FFARound) {
            round.addTeam(new TeamImpl(playerId, 1));
        } else if (round instanceof DoublesRound) {
            for (Team team : round.getTeams()) {
                if (team.getTeamMembers().size() >= 2) {
                    continue;
                }
                team.addPlayer(playerId);
            }

        }
    }

}
