package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.competition.DailyCompetition;
import normalmanv2.normalDiscGolf.impl.competition.DailyGhost;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** A Daily round compares to the personal best each player brought into it. */
public final class DailyRoundGhosts {
    private static final Particle.DustOptions GHOST = new Particle.DustOptions(Color.fromRGB(168, 139, 255), .85f);
    private static final int DOTS_PER_UPDATE = 64;
    private static final int VIEW_RANGE_SQUARED = 32 * 32;

    private record Baseline(StrokeStandings.Entry score, DailyGhost ghost) {}

    private final DailyRound round;
    private final Map<UUID, Baseline> baselines = new HashMap<>();
    private final Map<UUID, Map<Integer, List<List<DailyGhost.Point>>>> paths = new HashMap<>();
    private final Set<UUID> visible = new HashSet<>();

    DailyRoundGhosts(DailyRound round) { this.round = round; }

    public void loadBaselines(DailyCompetition competition) throws IOException {
        var board = competition.board(round.definition().date());
        if (board == null || !board.course().equals(round.definition())) return;
        for (var team : round.getTeams()) {
            UUID id = team.getOwner();
            var entry = board.entries().get(id);
            if (entry == null) continue;
            DailyGhost ghost = null;
            try {
                ghost = competition.ghost(board, id);
            } catch (IOException failure) {
                NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                        .log(Level.WARNING, "Could not read Daily ghost for " + id, failure);
            }
            baselines.put(id, new Baseline(entry, ghost));
            Player player = Bukkit.getPlayer(id);
            if (player != null && ghost != null)
                player.sendMessage(ChatColor.AQUA + "Your Daily ghost is ready. Use /ndg daily ghost on to show its paths.");
        }
    }

    void record(UUID player, int hole, List<Location> frames) {
        if (frames.size() < 2) return;
        int count = Math.min(frames.size(), DailyGhost.MAX_POINTS_PER_THROW);
        var points = new ArrayList<DailyGhost.Point>(count);
        try {
            for (int i = 0; i < count; i++) {
                Location point = frames.get((int)Math.round(i * (frames.size() - 1.0) / (count - 1)));
                points.add(DailyGhost.Point.at(point.getX(), point.getY(), point.getZ()));
            }
        } catch (IllegalArgumentException outsideCourse) {
            return;
        }
        paths.computeIfAbsent(player, ignored -> new HashMap<>())
                .computeIfAbsent(hole, ignored -> new ArrayList<>()).add(List.copyOf(points));
    }

    public DailyGhost completed(UUID player, ScoreCard card) {
        var played = paths.get(player);
        if (!round.eligible(card, player) || played == null) return null;
        var holes = new ArrayList<DailyGhost.Hole>();
        for (int hole = 0; hole < round.definition().holes(); hole++) {
            Integer score = card.getHoleScores().get(hole);
            var throwsInHole = played.get(hole);
            if (score == null || throwsInHole == null || throwsInHole.size() > score) return null;
            holes.add(new DailyGhost.Hole(score, throwsInHole));
        }
        return new DailyGhost(holes);
    }

    public void setVisible(Player player, boolean enabled) {
        UUID id = player.getUniqueId();
        if (enabled && ghost(id) == null) {
            sendUnavailable(player);
            return;
        }
        if (enabled) visible.add(id); else visible.remove(id);
        player.sendMessage(ChatColor.AQUA + "Daily ghost paths are " + (enabled ? "on." : "off."));
        if (enabled) draw(player);
    }

    public void sendStatus(Player player) {
        if (ghost(player.getUniqueId()) == null) {
            sendUnavailable(player);
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Daily ghost paths are " + (visible.contains(player.getUniqueId()) ? "on." : "off."));
        player.sendMessage(ChatColor.GRAY + "Use /ndg daily ghost on or off. Use /ndg daily ghost compare for the scorecard.");
    }

    public void sendComparison(Player player, boolean explainUnavailable) {
        DailyGhost best = ghost(player.getUniqueId());
        if (best == null) {
            if (explainUnavailable) sendUnavailable(player);
            return;
        }
        ScoreCard card = round.getScoreCardById(player.getUniqueId());
        if (card == null) return;
        player.sendMessage(ChatColor.AQUA + "Daily ghost scorecard • Best " + best.totalStrokes() + " strokes");
        for (int hole = 0; hole < best.holes().size(); hole++) player.sendMessage(ChatColor.GRAY + holeLine(card, best, hole));
        int finished = card.getHoleScores().size();
        if (finished > 0) player.sendMessage(ChatColor.AQUA + "Through " + finished
                + (finished == 1 ? " hole: " : " holes: ") + difference(delta(card, best)));
    }

    void announceHole(UUID id, ScoreCard card, int hole) {
        DailyGhost best = ghost(id);
        Player player = Bukkit.getPlayer(id);
        if (best != null && player != null) player.sendMessage(ChatColor.AQUA + "Daily ghost • " + holeLine(card, best, hole));
    }

    String target(UUID id, int hole) {
        DailyGhost best = ghost(id);
        return best == null ? "" : " §7• §bBest on hole: " + best.holes().get(hole).strokes();
    }

    void tick() {
        for (UUID id : visible) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) draw(player);
        }
    }

    void remove(UUID player) {
        paths.remove(player);
        visible.remove(player);
        baselines.remove(player);
    }

    void clear() {
        paths.clear();
        visible.clear();
        baselines.clear();
    }

    private void draw(Player player) {
        if (player.getGameMode() != GameMode.ADVENTURE || player.getWorld() != round.world()) return;
        DailyGhost best = ghost(player.getUniqueId());
        if (best == null) return;
        var shots = best.holes().get(round.getCurrentHole()).throwsInOrder();
        int count = shots.stream().mapToInt(List::size).sum();
        int step = Math.ceilDiv(count, DOTS_PER_UPDATE);
        Location viewer = player.getLocation();
        int pointIndex = 0;
        for (var shot : shots) for (var point : shot) {
            if (pointIndex++ % step != 0) continue;
            double dx = point.x() - viewer.getX(), dy = point.y() - viewer.getY(), dz = point.z() - viewer.getZ();
            if (dx * dx + dy * dy + dz * dz <= VIEW_RANGE_SQUARED)
                player.spawnParticle(Particle.DUST, point.x(), point.y(), point.z(), 1, 0, 0, 0, 0, GHOST);
        }
    }

    private DailyGhost ghost(UUID player) {
        Baseline baseline = baselines.get(player);
        return baseline == null ? null : baseline.ghost();
    }

    private void sendUnavailable(Player player) {
        Baseline best = baselines.get(player.getUniqueId());
        if (best == null) player.sendMessage(ChatColor.YELLOW + "Hole out on every hole to record your first Daily ghost.");
        else player.sendMessage(ChatColor.YELLOW + "Your Daily best is " + best.score().strokes()
                + " strokes. Match or beat it to record a ghost.");
    }

    private static String holeLine(ScoreCard card, DailyGhost best, int hole) {
        int target = best.holes().get(hole).strokes();
        Integer played = card.getHoleScores().get(hole);
        return "Hole " + (hole + 1) + ": You " + (played == null ? "—" : played) + " | Best " + target
                + (played == null ? "" : " | " + difference(played - target));
    }

    private static long delta(ScoreCard card, DailyGhost best) {
        return card.getHoleScores().entrySet().stream()
                .mapToLong(entry -> (long) entry.getValue() - best.holes().get(entry.getKey()).strokes()).sum();
    }

    private static String difference(long delta) {
        if (delta == 0) return "Tied";
        long difference = Math.abs(delta);
        return difference + (difference == 1 ? " stroke " : " strokes ") + (delta < 0 ? "ahead" : "behind");
    }
}
