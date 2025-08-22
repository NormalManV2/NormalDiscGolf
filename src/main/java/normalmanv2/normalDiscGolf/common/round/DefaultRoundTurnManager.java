package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.manager.RoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTurnManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class DefaultRoundTurnManager implements DelegateRoundTurnManager {
    private final List<Team> turnOrder;
    private final Map<Integer, Map<Team, Boolean>> scoredGoals;
    private final Random random = new Random();
    private int currentIndex = 0;
    private int hole = 1;

    public DefaultRoundTurnManager(Collection<Team> teams) {
        this.turnOrder = new ArrayList<>(teams);
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
        if (this.currentTeamTurn == null || this.teams.isEmpty()) {
            throw new RuntimeException("No team is currently selected for a turn, this is a very unexpected error please reach out for support!");
        }

        int currentIndex = this.teams.indexOf(this.currentTeamTurn);
        int nextIndex = (currentIndex + 1) % this.teams.size();
        this.currentTeamTurn = this.teams.get(nextIndex);

        boolean allTeamsScored = true;

        for (Map<Team, Boolean> teams : this.scoredGoals.values()) {
            if (!teams.values().stream().allMatch(Boolean::booleanValue)) {
                allTeamsScored = false;
                break;
            }
        }
        if (allTeamsScored) {
            this.advanceHole();
            for (Team team : this.teams) {
                for (UUID playerId : team.getTeamMembers()) {
                    Player player = Bukkit.getPlayer(playerId);

                    if (player == null) {
                        continue;
                    }

                    player.teleport(this.courseImpl.teeLocations().get(this.holeIndex));
                }
            }
        }
    }

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
        return DelegateRoundTurnManager.super.getNextTeeLocation();
    }
}
