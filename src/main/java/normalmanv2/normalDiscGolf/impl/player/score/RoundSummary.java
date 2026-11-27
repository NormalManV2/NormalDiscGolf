package normalmanv2.normalDiscGolf.impl.player.score;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

public record RoundSummary(String roundLabel, String scoreLine, RoundAnalytics.Stats stats, boolean markedFairways) {
    public void sendTo(Player player) {
        player.sendMessage(ChatColor.GOLD + header());
        for (String line : detailLines()) player.sendMessage(ChatColor.GRAY + line);
    }

    public String header() { return "Round summary - " + roundLabel; }

    public List<String> detailLines() {
        return List.of(scoreLine,
                "Longest drive: " + distance(stats.longestDrive()),
                "Longest putt: " + distance(stats.longestPutt()),
                "Fairway hits: " + (markedFairways ? rate(stats.fairwayHits(), stats.drives(), "no drives") : "— (no marked fairways)"),
                "Putting accuracy: " + rate(stats.puttsMade(), stats.puttAttempts(), "no putts"),
                "OB penalties: " + stats.obPenalties());
    }

    private static String distance(OptionalDouble distance) {
        return distance.isPresent() ? String.format(Locale.ROOT, "%.1f blocks", distance.getAsDouble()) : "—";
    }

    private static String rate(int successes, int attempts, String noAttempts) {
        return attempts == 0 ? "— (" + noAttempts + ")"
                : successes + "/" + attempts + " (" + Math.round(100.0 * successes / attempts) + "%)";
    }
}
