package normalmanv2.normalDiscGolf.api.round;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.round.RoundState;

import java.util.List;
import java.util.UUID;

public interface GameRound {

    RoundState getRoundState();

    void setRoundState(RoundState roundState);

    void startRound();

    void endRound();

    void cancelRound();

    void addTeam(Team teamImpl);

    void removeTeam(Team teamImpl);

    void handleStroke(UUID playerId, String technique, DiscImpl discImpl);

    void handleTurn(Team team);

    void handleTurn(UUID playerId);

    boolean isTurn(Team team);

    boolean isTurn(UUID playerId);

    boolean isRoundOver();

    List<Team> getTeams();

    CourseImpl getCourse();

    boolean isTournamentRound();

    int getCurrentHoleNumber();
}