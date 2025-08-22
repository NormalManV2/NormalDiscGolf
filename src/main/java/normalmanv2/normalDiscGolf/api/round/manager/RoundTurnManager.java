package normalmanv2.normalDiscGolf.api.round.manager;

import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Location;

public interface RoundTurnManager {

    void nextTurn();

    void advanceHole();

    boolean isTurn(Team team);

    void selectRandomTeamTurn();

    void setTeamScored(int holeNumber, Team team);

    Location getNextTeeLocation();

    int getCurrentHole();
}
