package normalmanv2.normalDiscGolf.api.round;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Location;

public interface RoundTurnManager {

    void nextTurn();

    void advanceHole();

    boolean isTurn(Team team);

    void handleStroke(ThrowMechanic throwMechanic);

    Location getNextTeeLocation();

    int getCurrentHole();
}
