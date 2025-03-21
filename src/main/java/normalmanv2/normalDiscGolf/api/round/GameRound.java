package normalmanv2.normalDiscGolf.api.round;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.round.RoundState;

import java.util.List;
import java.util.UUID;

public interface GameRound {

    String getId();

    RoundState getRoundState();

    int getMaximumTeams();

    void setRoundState(RoundState roundState);

    void startRound();

    void endRound();

    void cancelRound();

    void addTeam(Team teamImpl);

    void removeTeam(Team teamImpl);

    void handleStroke(ThrowMechanic mechanic);

    void setTurn(Team team);

    boolean isTurn(Team team);

    void nextTurn();

    void nextHole();

    boolean isRoundOver();

    List<Team> getTeams();

    CourseImpl getCourse();

    boolean isTournamentRound();

    int getCurrentHoleNumber();

    void setDivision(Division division);

    Division getDivision();

    boolean isFull();
}