package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RoundImpl implements GameRound {

    private final String id;
    private final int maximumTeams;
    private final List<Team> teams;
    private final Map<Team, ScoreCard> scoreCards;
    private final boolean isTournamentRound;
    private final Plugin plugin;
    private final PlayerDataManager playerDataManager;
    private final CourseImpl courseImpl;
    private final Random random = new Random();
    private final Map<Integer, Map<Team, Boolean>> scoredGoals;
    private final boolean isPrivate;

    private RoundState roundState;
    private boolean roundOver;
    private int holeIndex;
    private BukkitTask gameTask;
    private Team currentTeamTurn;
    private Division division;


    public RoundImpl(Plugin plugin, CourseImpl courseImpl, boolean isTournamentRound, String id, int maximumTeams, boolean isPrivate) {
        this.plugin = plugin;
        this.playerDataManager = NDGManager.getInstance().getPlayerDataManager();
        this.courseImpl = courseImpl;
        this.teams = new ArrayList<>();
        this.scoreCards = new HashMap<>();
        this.isTournamentRound = isTournamentRound;
        this.id = id;
        this.maximumTeams = maximumTeams;
        this.scoredGoals = new HashMap<>();
        this.isPrivate = isPrivate;
    }

    @Override
    public Division getDivision() {
        return this.division;
    }

    @Override
    public void setDivision(Division division) {
        this.division = division;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public RoundState getRoundState() {
        return this.roundState;
    }

    @Override
    public void setRoundState(RoundState roundState) {
        this.roundState = roundState;
    }

    @Override
    public void start() {
        this.roundOver = false;
        this.holeIndex = 0;

        for (Team teamImpl : teams) {
            this.scoreCards.put(teamImpl, new ScoreCard());

            for (UUID playerId : teamImpl.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    teamImpl.removePlayer(playerId);
                    if (teamImpl.getTeamMembers().isEmpty()) {
                        this.teams.remove(teamImpl);
                        this.scoreCards.remove(teamImpl);
                    }
                    continue;
                }


                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {

                    if (Bukkit.getWorld(this.id) == null) {
                        throw new RuntimeException("World " + this.id + " does not exist");
                    }

                    Location startingLocation = Bukkit.getWorld(this.id).getSpawnLocation();


                    player.teleport(startingLocation);
                }, 20);
            }

            this.currentTeamTurn = this.teams.get(random.nextInt(this.teams.size()));
        }

        this.gameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            this.tick();
            System.out.println(this.id + " Round Currently Running");
        }, 0, 100);
    }

    protected void tick() {
        for (Team team : teams) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    team.removePlayer(playerId);
                    if (team.getTeamMembers().isEmpty()) {
                        this.teams.remove(team);
                        this.scoreCards.remove(team);
                    }
                }
            }
        }
    }

    @Override
    public void endRound() {
        this.handleRoundEnd();
    }

    @Override
    public void cancelRound() {
        this.dispose();
    }

    @Override
    public void addTeam(Team team) {

        if (this.gameTask != null) {
            throw new IllegalStateException("Round has already been started");
        }

        if (this.isFull()) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    continue;
                }
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4This round is full!"));
            }
            return;
        }
        this.teams.add(team);
    }

    @Override
    public void removeTeam(Team team) {
        this.teams.remove(team);
    }

    @Override
    public boolean isFull() {
        return this.teams.size() >= this.maximumTeams;
    }

    @Override
    public void handleStroke(ThrowMechanic throwMechanic) {
        Player player = Bukkit.getPlayer(throwMechanic.getPlayerId());
        if (player == null) {
            return;
        }

        PlayerData playerData = playerDataManager.getDataByPlayer(throwMechanic.getPlayerId());
        PlayerSkills skills = playerData.getSkills();


        if (this instanceof FFARound) {
            ScoreCard card = this.getScoreCardById(player.getUniqueId());
            if (card == null)
                throw new RuntimeException("No score card found for player " + player.getDisplayName() + ". This is a major issue, please report!");
            card.trackStroke();
        }

        DiscImpl disc = (DiscImpl) throwMechanic.getDisc();

        disc.handleThrow(player, skills, throwMechanic.getThrowTechnique(), player.getFacing(), throwMechanic);
    }

    @Override
    public boolean isTurn(Team team) {
        return team.equals(this.currentTeamTurn);
    }

    @Override
    public void nextTurn() {
        if (this.currentTeamTurn == null || this.teams.isEmpty())
            throw new RuntimeException("No team is currently selected for a turn, this is a very unexpected error please reach out for support!");

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
        if (allTeamsScored) this.nextHole();
    }

    @Override
    public void nextHole() {
        this.holeIndex++;
    }

    @Override
    public boolean isRoundOver() {
        return this.roundOver;
    }

    @Override
    public List<Team> getTeams() {
        return Collections.unmodifiableList(this.teams);
    }

    @Override
    public CourseImpl getCourse() {
        return this.courseImpl;
    }

    @Override
    public boolean isTournamentRound() {
        return this.isTournamentRound;
    }

    @Override
    public int getCurrentHoleNumber() {
        return this.holeIndex;
    }

    @Override
    public Location getNextTeeLocation() {
        return this.courseImpl.teeLocations().get(this.holeIndex + 1);
    }

    @Override
    public int getMaximumTeams() {
        return this.maximumTeams;
    }

    @Override
    public boolean isPrivate() {
        return this.isPrivate;
    }

    @Override
    public boolean containsPlayer(UUID uuid) {
        return this.teams.stream().anyMatch(team -> team.getTeamMembers().contains(uuid));
    }

    @Override
    public boolean containsTeam(Team team) {
        return this.teams.contains(team);
    }

    @Override
    public boolean isTeamMember(Team team, UUID uuid) {
        if (!this.teams.contains(team)) {
            throw new RuntimeException("Team " + team + " is not in this round " + this.id);
        }
        return team.contains(uuid);
    }

    private void handleRoundEnd() {
        this.roundOver = true;
        for (Team team : this.teams) {
            ScoreCard scoreCard = this.scoreCards.get(team);
            if (scoreCard == null) {
                continue;
            }

            for (UUID playerId : team.getTeamMembers()) {
                PlayerData playerData = playerDataManager.getDataByPlayer(playerId);
                PDGARating rating = playerData.getRating();
                rating.handleRoundEnd(scoreCard.getTotalScore());
                rating.updateRating(courseImpl.difficulty());
                System.out.println(scoreCard.getTotalStrokes());
                System.out.println(scoreCard.getTotalScore());
                System.out.println(rating.getRating());
                System.out.println(rating.getAverageScore());
                System.out.println(rating.getDivision());
                System.out.println(rating.getTotalRounds());
            }
        }
        this.dispose();
    }

    public Map<Team, ScoreCard> getScoreCards() {
        return this.scoreCards;
    }

    public ScoreCard getScoreCardByTeam(Team team) {
        return this.scoreCards.get(team);
    }

    protected ScoreCard getScoreCardById(UUID playerId) {
        return teams.stream()
                .filter(team -> team.getTeamMembers().contains(playerId))
                .findFirst()
                .map(scoreCards::get)
                .orElse(null);
    }

    public Map<Integer, Map<Team, Boolean>> getScoredGoals() {
        return Collections.unmodifiableMap(this.scoredGoals);
    }

    public void setTeamScored(int holeNumber, Team team) {
        this.scoredGoals.put(holeNumber, Map.of(team, true));
    }

    private void dispose() {
        this.teams.clear();
        this.scoreCards.clear();
        this.gameTask.cancel();
        this.gameTask = null;
        this.currentTeamTurn = null;
        this.roundOver = true;
    }

}
