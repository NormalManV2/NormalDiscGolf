package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundTurnManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

public abstract class SingleThrowRound extends FFARound {
    protected SingleThrowRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }

    public boolean hasFinishedHole(Team team) {
        return ((DefaultRoundTurnManager) getRoundTurnManager()).hasFinishedHole(team);
    }

    public final String holeResult(Team team) { return holeResult(team, getCurrentHole()); }

    public abstract String holeResult(Team team, int hole);

    public void finishThrow(Team team, Location landing, boolean hazard) {
        miss(team, hazard ? "hazard or out of bounds" : null);
    }

    public abstract void miss(Team team, String reason);

    protected void completeWithoutBasket(Team team) {
        var card = getScoreCardByTeam(team);
        card.resetCurrentStrokes();
        card.breakBasketStreak();
        setTeamScored(getCurrentHole(), team);
    }

    protected String name(UUID id) {
        var player = Bukkit.getPlayer(id);
        String name = player == null ? Bukkit.getOfflinePlayer(id).getName() : player.getName();
        return name == null ? id.toString() : name;
    }
}
