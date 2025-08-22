package normalmanv2.normalDiscGolf.api.round.delegate.manager;

import normalmanv2.normalDiscGolf.api.round.manager.RoundTeamManager;
import normalmanv2.normalDiscGolf.api.team.Team;

import java.util.List;
import java.util.UUID;

public interface DelegateRoundTeamManager extends RoundTeamManager {
    RoundTeamManager getRoundTeamManager();

    @Override
    default boolean addTeam(Team team) {
        return this.getRoundTeamManager().addTeam(team);
    }

    @Override
    default void removeTeam(Team team) {
        this.getRoundTeamManager().removeTeam(team);
    }

    @Override
    default List<Team> getTeams() {
        return this.getRoundTeamManager().getTeams();
    }

    @Override
    default boolean containsTeam(Team team) {
        return this.getRoundTeamManager().containsTeam(team);
    }

    @Override
    default boolean containsPlayer(UUID playerId) {
        return this.getRoundTeamManager().containsPlayer(playerId);
    }

    @Override
    default boolean isFull() {
        return this.getRoundTeamManager().isFull();
    }

    @Override
    default void tick() {
        this.getRoundTeamManager().tick();
    }
}
