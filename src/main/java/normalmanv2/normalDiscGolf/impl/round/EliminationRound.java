package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EliminationRound extends FFARound {
    private static final int MAX_SHOOTOUTS = 2;
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Integer> baskets = new HashMap<>();
    private final List<UUID> knockouts = new ArrayList<>();
    private Set<Team> shootout = Set.of();
    private int replay;

    public EliminationRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }

    public boolean hasOpponents() {
        return getTeams().stream().filter(team -> team.isFull() && Bukkit.getPlayer(team.getOwner()) != null)
                .limit(2).count() == 2;
    }

    public void open() {
        for (Team team : getTeams()) names.put(team.getOwner(), Bukkit.getPlayer(team.getOwner()).getName());
        play().message(ChatColor.GREEN + "Elimination: The highest stroke count goes out after each hole.");
        play().message(ChatColor.GRAY + "Tied players replay the hole. If two shootouts stay tied, a draw breaks the tie.");
    }

    public boolean isSafe(Team team) { return replay > 0 && !shootout.contains(team); }
    public void recordBasket(UUID player) { baskets.merge(player, 1, Integer::sum); }
    public int rewardBaskets(UUID player) { return baskets.getOrDefault(player, 0); }
    public Set<UUID> entrants() { return names.keySet(); }

    public int place(UUID entrant) {
        int eliminated = knockouts.reversed().indexOf(entrant);
        return eliminated < 0 ? 1 : eliminated + 2;
    }

    /** Resolves the completed stage and returns whether the next stage replays this hole. */
    public boolean finishStage() {
        var contenders = getTeams().stream().filter(team -> !isSafe(team)).toList();
        int hole = getCurrentHole();
        if (contenders.size() > 1) {
            int highest = contenders.stream().mapToInt(team -> getScoreCardByTeam(team).getHoleScores().get(hole))
                    .max().orElseThrow();
            var worst = contenders.stream().filter(team -> getScoreCardByTeam(team).getHoleScores().get(hole) == highest).toList();
            if (worst.size() > 1 && replay < MAX_SHOOTOUTS) {
                shootout = Set.copyOf(worst);
                replay++;
                String tied = String.join(", ", worst.stream().map(team -> names.get(team.getOwner())).toList());
                play().message(ChatColor.GOLD + "Hole " + (hole + 1) + " tied at " + highest + " strokes. Shootout "
                        + replay + ": " + tied + " replay this hole.");
                resetCards(worst);
                return true;
            }

            Team eliminated = worst.get(ThreadLocalRandom.current().nextInt(worst.size()));
            UUID id = eliminated.getOwner();
            String reason = worst.size() == 1 ? highest + " strokes" : "draw among players tied at " + highest + " strokes";
            play().message(ChatColor.RED + names.get(id) + " was eliminated on hole " + (hole + 1) + " (" + reason + ").");
            knockouts.add(id);
            play().removeEliminated(eliminated);
            Player player = Bukkit.getPlayer(id);
            if (player != null) NDGManager.getInstance().getPlayerSessionManager().restorePlayer(player);
        }
        shootout = Set.of();
        replay = 0;
        resetCards(getTeams());
        return false;
    }

    private void resetCards(List<Team> teams) {
        for (Team team : teams) getRoundScoreCardManager().addScoreCard(team, new ScoreCard());
    }

    public void forfeit(UUID player) {
        knockouts.add(player);
        baskets.remove(player);
        play().message(ChatColor.YELLOW + names.get(player) + " forfeited Elimination.");
    }

    public String stageLine() {
        return "Players remaining: " + getTeams().size() + (replay == 0 ? "" : " • Shootout " + replay);
    }

    public List<String> results() {
        UUID winner = getTeams().getFirst().getOwner();
        var lines = new ArrayList<String>();
        lines.add("Elimination winner: " + names.get(winner) + "!");
        lines.add("Final standings:");
        lines.add("1. " + names.get(winner));
        for (UUID player : knockouts.reversed()) lines.add((lines.size() - 1) + ". " + names.get(player));
        return lines;
    }
}
