package normalmanv2.normalDiscGolf.impl.player.score;

import normalmanv2.normalDiscGolf.impl.course.library.SavedCourse;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

public record RoundSummary(String roundLabel, String scoreLine, RoundAnalytics.Stats stats, boolean markedFairways,
                           SavedCourse generatedCourse) {
    public RoundSummary(String roundLabel, String scoreLine, RoundAnalytics.Stats stats, boolean markedFairways) {
        this(roundLabel, scoreLine, stats, markedFairways, null);
    }
    public record Metric(String name, String value) {}

    public void sendTo(Player player) {
        player.sendMessage(ChatColor.GOLD + header());
        for (String line : detailLines()) player.sendMessage(ChatColor.GRAY + line);
    }

    public String header() { return "Round summary - " + roundLabel; }

    public List<String> detailLines() {
        var lines = new ArrayList<String>();
        lines.add(scoreLine);
        for (Metric metric : metrics()) lines.add(metric.name() + ": " + metric.value());
        return List.copyOf(lines);
    }

    public List<Metric> metrics() {
        return List.of(
                new Metric("Longest drive", distance(stats.longestDrive())),
                new Metric("Longest putt", distance(stats.longestPutt())),
                new Metric("Fairway hits", markedFairways ? rate(stats.fairwayHits(), stats.drives(), "no drives") : "— (no marked fairways)"),
                new Metric("Putting accuracy", rate(stats.puttsMade(), stats.puttAttempts(), "no putts")),
                new Metric("OB penalties", Integer.toString(stats.obPenalties())));
    }

    private static String distance(OptionalDouble distance) {
        return distance.isPresent() ? String.format(Locale.ROOT, "%.1f blocks", distance.getAsDouble()) : "—";
    }

    private static String rate(int successes, int attempts, String noAttempts) {
        return attempts == 0 ? "— (" + noAttempts + ")"
                : successes + "/" + attempts + " (" + Math.round(100.0 * successes / attempts) + "%)";
    }
}
