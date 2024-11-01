package normalmanv2.normalDiscGolf.api.round;

import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.api.disc.Disc;
import normalmanv2.normalDiscGolf.api.team.Team;

import java.util.List;
import java.util.UUID;

public interface GameRound {

    void startRound();

    void endRound();

    void cancelRound();

    void addTeam(Team team);

    void removeTeam(Team team);

    void handleStroke(UUID playerId, String technique, Disc disc);

    boolean isRoundOver();

    List<Team> getTeams();

    Course getCourse();

    boolean isTournamentRound();

    int getCurrentHoleNumber();
}