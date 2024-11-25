package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.event.PlayerRoundQueueEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class PlayerRoundQueueListener implements Listener {

    @EventHandler
    public void onRoundQueue(PlayerRoundQueueEvent event) {
        List<GameRound> roundQueue = event.getRoundQueue();

        if (!roundQueue.isEmpty()) {
            Iterator<GameRound> iterator = roundQueue.iterator();

            if (this.compareRoundType(event.getRoundName(), iterator.next())) {

            }

            while (iterator.hasNext()) {
                GameRound round = iterator.next();
                for (Team team : round.getTeams()) {
                    for (UUID playerId : team.getTeamMembers()) {

                    }
                }
            }
        }
    }

    private boolean compareRoundType(String roundType, GameRound round) {
        return false;
    }

}
