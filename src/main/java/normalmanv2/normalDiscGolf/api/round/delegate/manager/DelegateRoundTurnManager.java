package normalmanv2.normalDiscGolf.api.round.delegate.manager;

import normalmanv2.normalDiscGolf.api.round.manager.RoundTurnManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Location;

public interface DelegateRoundTurnManager extends RoundTurnManager {
    RoundTurnManager getRoundTurnManager();

    @Override
    default void nextTurn() {
        this.getRoundTurnManager().nextTurn();
    }

    @Override
    default void advanceHole() {
        this.getRoundTurnManager().advanceHole();
    }

    @Override
    default boolean isTurn(Team team) {
        return this.getRoundTurnManager().isTurn(team);
    }

    @Override
    default void selectRandomTeamTurn() {
        this.getRoundTurnManager().selectRandomTeamTurn();
    }

    @Override
    default void setTeamScored(int holeNumber, Team team) {
        this.getRoundTurnManager().setTeamScored(holeNumber, team);
    }

    @Override
    default Location getNextTeeLocation() {
        return this.getRoundTurnManager().getNextTeeLocation();
    }

    @Override
    default int getCurrentHole() {
        return this.getRoundTurnManager().getCurrentHole();
    }
}
