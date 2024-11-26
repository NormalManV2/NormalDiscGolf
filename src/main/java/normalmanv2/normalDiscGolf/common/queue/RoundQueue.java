package normalmanv2.normalDiscGolf.common.queue;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;

public record RoundQueue(String id, GameRound round) {

    public boolean isFull() {

        if (this.round instanceof FFARound) {
            return round.getTeams().size() >= round.getMaximumTeams();
        } else if (this.round instanceof DoublesRound) {
            return round.getTeams().stream().allMatch(Team::isFull);
        }

        return false;
    }

}
