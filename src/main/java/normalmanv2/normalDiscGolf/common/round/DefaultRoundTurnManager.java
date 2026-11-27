package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.delegate.Wirable;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTurnManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.round.EliminationRound;
import normalmanv2.normalDiscGolf.impl.round.MatchPlayRound;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
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
    private Team teeOpener;

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
            NDGManager.getInstance().getRoundHandler().cancelRound(this.round);
            return;
        }

        if (this.allTeamsScoredCurrentHole(teams)) {
            if (round instanceof EliminationRound elimination) {
                boolean replay = elimination.finishStage();
                teams = getTurnOrder();
                if (teams.size() == 1) {
                    NDGManager.getInstance().getRoundHandler().endRound(round);
                    return;
                }
                if (replay) {
                    Set<Team> safe = new HashSet<>();
                    for (Team team : teams) if (elimination.isSafe(team)) safe.add(team);
                    scoredGoals.put(hole, safe);
                    currentIndex = -1;
                    currentIndex = findNextUnscoredTeamIndex(teams);
                    elimination.play().startHole();
                    teleportTeamsToCurrentTee(teams, false);
                    return;
                }
            }
            if (round instanceof MatchPlayRound match) {
                match.finishMatchHole();
                if (match.score().isComplete()) {
                    NDGManager.getInstance().getRoundHandler().endRound(round);
                    return;
                }
                UUID winner = match.score().holes().getLast().winner();
                if (winner != null) teeOpener = teams.stream().filter(team -> team.contains(winner)).findFirst().orElse(teeOpener);
            }
            this.advanceHole();
            if (round instanceof MatchPlayRound && teeOpener != null)
                this.currentIndex = Math.max(0, teams.indexOf(teeOpener));

            if (this.hole >= this.round.getCourse().holes()) {
                NDGManager.getInstance().getRoundHandler().endRound(this.round);
                return;
            }

            ((RoundImpl) this.round).play().startHole();
            this.teleportTeamsToCurrentTee(teams, true);
            return;
        }

        int nextIndex = this.findNextUnscoredTeamIndex(teams);
        if (nextIndex == -1) {
            return;
        }

        this.currentIndex = nextIndex;
        ((RoundImpl) this.round).play().turnChanged();
    }

    @Override
    public void selectRandomTeamTurn() {
        List<Team> teams = this.getTurnOrder();
        this.currentIndex = teams.isEmpty() ? 0 : this.random.nextInt(teams.size());
        this.teeOpener = teams.isEmpty() ? null : teams.get(this.currentIndex);
    }

    @Override
    public void advanceHole() {
        this.hole++;
        this.currentIndex = 0;
        if (round instanceof EliminationRound) {
            this.hole %= round.getCourse().holes();
            this.scoredGoals.clear();
        }
        this.scoredGoals.putIfAbsent(this.hole, new HashSet<>());
    }

    @Override
    public boolean isTurn(Team team) {
        List<Team> teams = this.getTurnOrder();
        return !teams.isEmpty()
                && this.currentIndex >= 0
                && teams.get(this.currentIndex).equals(team)
                && !this.hasFinishedHole(team);
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
        return ((normalmanv2.normalDiscGolf.impl.course.CourseImpl)this.round.getCourse()).tee(world, this.hole);
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
            Team previous = this.turnOrder == null || this.turnOrder.isEmpty() || this.currentIndex < 0 ? null : this.turnOrder.get(Math.min(this.currentIndex, this.turnOrder.size() - 1));
            this.turnOrder = new ArrayList<>(teams);
            int retained = this.turnOrder.indexOf(previous);
            if (retained >= 0) this.currentIndex = retained;
            else this.currentIndex = Math.max(-1, Math.min(this.currentIndex - 1, this.turnOrder.size() - 1));
            if (this.currentIndex >= this.turnOrder.size()) {
                this.currentIndex = 0;
            }
        }
        return this.turnOrder;
    }

    public void reconcile() {
        List<Team> teams = getTurnOrder();
        if (teams.isEmpty() || currentIndex < 0 || allTeamsScoredCurrentHole(teams) || hasFinishedHole(teams.get(currentIndex))) nextTurn();
    }

    private boolean allTeamsScoredCurrentHole(List<Team> teams) {
        Set<Team> scoredTeams = this.scoredGoals.getOrDefault(this.hole, Collections.emptySet());
        return !teams.isEmpty() && teams.stream().allMatch(scoredTeams::contains);
    }

    private int findNextUnscoredTeamIndex(List<Team> teams) {
        Set<Team> scoredTeams = this.scoredGoals.getOrDefault(this.hole, Collections.emptySet());
        for (int offset = 1; offset <= teams.size(); offset++) {
            int candidateIndex = (this.currentIndex + offset) % teams.size();
            if (!scoredTeams.contains(teams.get(candidateIndex))) {
                return candidateIndex;
            }
        }

        return -1;
    }

    public boolean hasFinishedHole(Team team) {
        return this.scoredGoals.getOrDefault(this.hole, Collections.emptySet()).contains(team);
    }

    private void teleportTeamsToCurrentTee(List<Team> teams, boolean preview) {
        for (Team team : teams) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    continue;
                }

                RoundPresentation.teleportToCurrentTee(this.plugin, player, this.round, preview);
            }
        }
    }
}
