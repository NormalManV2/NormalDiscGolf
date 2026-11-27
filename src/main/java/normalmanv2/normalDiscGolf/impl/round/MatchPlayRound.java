package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class MatchPlayRound extends RoundImpl {
    public static final int PLAYERS = 2;
    private MatchPlayScore score;

    public MatchPlayRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }

    public boolean hasOpponents() {
        return getTeams().size() == PLAYERS && getTeams().stream()
                .allMatch(team -> team.getTeamMembers().size() == 1 && Bukkit.getPlayer(team.getOwner()) != null);
    }

    public void openMatch() {
        score = new MatchPlayScore(getTeams().get(0).getOwner(), getTeams().get(1).getOwner(), getCourse().holes());
        play().message(ChatColor.GREEN + "Match Play: " + playerName(score.first()) + " vs " + playerName(score.second())
                + " over " + getCourse().holes() + " holes.");
    }

    public MatchPlayScore score() { return score; }

    public void finishMatchHole() {
        int hole = getCurrentHole();
        if (score.holes().size() > hole) return; // A concession already awarded this hole.
        int first = getScoreCardById(score.first()).getHoleScores().get(hole);
        int second = getScoreCardById(score.second()).getHoleScores().get(hole);
        announce(score.recordStrokes(hole, first, second));
    }

    public void awardMatchHole(Team conceding, String reason) {
        announce(score.award(getCurrentHole(), conceding.getOwner(), reason));
    }

    private void announce(MatchPlayScore.Hole hole) {
        play().message(ChatColor.GOLD + describe(hole));
        play().message(ChatColor.GRAY + scoreLine());
    }

    public String describe(MatchPlayScore.Hole hole) {
        String prefix = "Hole " + (hole.index() + 1) + ": ";
        if (hole.winner() == null) return prefix + "Halved (" + strokeCount(hole.firstStrokes()) + " each).";
        String ending = hole.awardReason() == null
                ? Math.min(hole.firstStrokes(), hole.secondStrokes()) + "–" + Math.max(hole.firstStrokes(), hole.secondStrokes()) + " strokes"
                : hole.awardReason();
        return prefix + playerName(hole.winner()) + " wins (" + ending + ").";
    }

    public String scoreLine() {
        UUID leader = score.leader();
        return "Match score: " + (leader == null ? "All square" : playerName(leader) + " " + score.margin() + " up");
    }

    public String resultLine() {
        UUID winner = score.winner();
        if (winner == null) return "Match result: Draw (all square).";
        if (score.forfeiter() != null) return "Match result: " + playerName(winner) + " wins by forfeit.";
        String margin = score.holesRemaining() == 0 ? score.margin() + " up" : score.margin() + " and " + score.holesRemaining();
        return "Match result: " + playerName(winner) + " wins " + margin + ".";
    }

    public String resultsLine() {
        return "Hole wins: " + playerName(score.first()) + " " + score.wins(score.first()) + " • "
                + playerName(score.second()) + " " + score.wins(score.second()) + " • Halved " + score.halved();
    }

    public static String strokeCount(int strokes) { return strokes + (strokes == 1 ? " stroke" : " strokes"); }

    public String playerName(UUID id) {
        Player player = Bukkit.getPlayer(id);
        String name = player != null ? player.getName() : Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString() : name;
    }
}
