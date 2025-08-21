package normalmanv2.normalDiscGolf.api.round;

import normalmanv2.normalDiscGolf.api.team.Team;

import java.util.List;
import java.util.UUID;

public interface RoundTeamManager {

    boolean addTeam(Team team);

    void removeTeam(Team team);

    List<Team> getTeams();

    boolean containsTeam(Team team);

    boolean containsPlayer(UUID playerId);

    boolean isFull();
}
