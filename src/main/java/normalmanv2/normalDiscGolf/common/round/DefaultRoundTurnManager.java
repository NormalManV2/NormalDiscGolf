package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.delegate.Wirable;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTurnManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.round.RoundPresentation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DefaultRoundTurnManager implements DelegateRoundTurnManager, Wirable {
    private GameRound round;
    private Plugin plugin;
    private boolean isWired = false;
    private List<Team> turnOrder;

    private final Map<Integer, Set<Team>> scoredGoals;
    private final Random random = new Random();
    private int currentIndex = 0;
    private int hole = 0;

    public DefaultRoundTurnManager() {
        this.scoredGoals = new HashMap<>();
    }

    @Override
    public void wire(@NotNull GameRound round, @Nullable Plugin plugin) {
        if (this.isWired) {
            throw new IllegalStateException("This turn manager is already wired!");
        }

        this.round = round;
        this.plugin = plugin;
        this.isWired = true;
        this.init();
    }

    @Override
    public void unwire() {
        if (!this.isWired) {
            throw new IllegalStateException("This turn manager is not wired!");
        }

        this.round = null;
        this.plugin = null;
        this.turnOrder = null;
        this.isWired = false;
    }

    private void init() {
        this.turnOrder = new ArrayList<>(this.round.getTeams());
        this.scoredGoals.putIfAbsent(this.hole, new HashSet<>());
    }

    public boolean isWired() {
        return this.isWired;
    }

    @Override
    public RoundTurnManager getRoundTurnManager() {
        return this;
    }

    @Override
    public void setTeamScored(int holeNumber, Team team) {
        this.scoredGoals.computeIfAbsent(holeNumber, ignored -> new HashSet<>()).add(team);
    }

    @Override
    public void nextTurn() {
        List<Team> teams = this.getTurnOrder();

        if (teams.isEmpty()) {
            throw new IllegalStateException("No Teams in Round");
        }

        if (this.allTeamsScoredCurrentHole(teams)) {
            this.advanceHole();

            if (this.hole >= this.round.getCourse().holes()) {
                NDGManager.getInstance().getRoundHandler().endRound(this.round);
                return;
            }

            this.teleportTeamsToCurrentTee(teams);
            return;
        }

        this.currentIndex = (this.currentIndex + 1) % teams.size();
    }

    @Override
    public void selectRandomTeamTurn() {
        List<Team> teams = this.getTurnOrder();
        this.currentIndex = teams.isEmpty() ? 0 : this.random.nextInt(teams.size());
    }

    @Override
    public void advanceHole() {
        this.hole++;
        this.currentIndex = 0;
        this.scoredGoals.putIfAbsent(this.hole, new HashSet<>());
    }

    @Override
    public boolean isTurn(Team team) {
        List<Team> teams = this.getTurnOrder();
        return !teams.isEmpty() && teams.get(this.currentIndex).equals(team);
    }

    @Override
    public int getCurrentHole() {
        return this.hole;
    }

    @Override
    public Location getNextTeeLocation(World world) {
        CourseGrid.GridPoint teePoint = this.round.getCourse().teeLocations().get(this.hole);
        if (teePoint == null) {
            throw new IllegalStateException("No tee location found for hole " + this.hole);
        }
        return this.round.getCourse().toLocation(world, teePoint);
    }

    public DefaultRoundTurnManager setRound(GameRound round) {
        this.round = round;
        return this;
    }

    public void dispose() {
        if (this.turnOrder != null) {
            this.turnOrder.clear();
        }
        this.scoredGoals.clear();
    }

    private List<Team> getTurnOrder() {
        List<Team> teams = this.round.getTeams();
        if (this.turnOrder == null || this.turnOrder.size() != teams.size() || !this.turnOrder.containsAll(teams)) {
            this.turnOrder = new ArrayList<>(teams);
            if (this.currentIndex >= this.turnOrder.size()) {
                this.currentIndex = 0;
            }
        }
        return this.turnOrder;
    }

    private boolean allTeamsScoredCurrentHole(List<Team> teams) {
        Set<Team> scoredTeams = this.scoredGoals.getOrDefault(this.hole, Collections.emptySet());
        return !teams.isEmpty() && teams.stream().allMatch(scoredTeams::contains);
    }

    private void teleportTeamsToCurrentTee(List<Team> teams) {
        for (Team team : teams) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    this.round.getRoundTeamManager().tick();
                    continue;
                }

                RoundPresentation.teleportToCurrentTee(this.plugin, player, this.round, true);
            }
        }
    }
}
