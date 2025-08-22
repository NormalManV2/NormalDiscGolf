package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTurnManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class DefaultRoundTurnManager implements DelegateRoundTurnManager {
    private final GameRound round;
    private final List<Team> turnOrder;
    private final Map<Integer, Map<Team, Boolean>> scoredGoals;
    private final Random random = new Random();
    private int currentIndex = 0;
    private int hole = 1;

    public DefaultRoundTurnManager(Collection<Team> teams, GameRound round) {
        this.turnOrder = new ArrayList<>(teams);
        this.round = round;
        this.scoredGoals = new HashMap<>();
    }

    @Override
    public RoundTurnManager getRoundTurnManager() {
        return this;
    }

    @Override
    public void setTeamScored(int holeNumber, Team team) {
        this.scoredGoals.put(holeNumber, Map.of(team, true));
    }

    @Override
    public void nextTurn() {

        List<Team> teams = this.round.getTeams();

        if (teams.isEmpty()) {
            throw new IllegalStateException("No Teams in Round");
        }

        this.currentIndex = (this.currentIndex + 1) % teams.size();

        boolean allTeamsScored = this.scoredGoals.get(this.hole).values().stream().allMatch(Boolean::booleanValue);

        if (allTeamsScored) {
            this.advanceHole();

            Location tee = this.getNextTeeLocation();

            for (Team team : teams) {
                for (UUID playerId : team.getTeamMembers()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null) {
                        this.round.getRoundTeamManager().tick();
                        continue;
                    }
                    player.teleport(tee);
                }
            }
        }
    }

    @Override
    public void selectRandomTeamTurn() {
        this.currentIndex = (this.random.nextInt(this.turnOrder.size()));
    }

    @Override
    public void advanceHole() {
        this.hole++;
        this.currentIndex = 0;
    }

    @Override
    public boolean isTurn(Team team) {
        return turnOrder.get(this.currentIndex).equals(team);
    }

    @Override
    public int getCurrentHole() {
        return this.hole;
    }

    @Override
    public Location getNextTeeLocation() {
        return this.round.getCourse().teeLocations().get(this.hole);
    }

    public void dispose() {
        this.turnOrder.clear();
        this.scoredGoals.clear();
    }
}
