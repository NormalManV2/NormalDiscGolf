package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.impl.util.BukkitCompatibility;
import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.competition.FlightGhost;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Private throw playback and comparison against the best card a player brought into the round. */
public final class RoundGhosts {
    public record Controls(String label, boolean recorded, boolean visible, boolean playing) {}

    @FunctionalInterface
    public interface GhostLoader {
        FlightGhost load(StrokeStandings.Entry entry) throws IOException;
    }

    private static final Particle.DustOptions GHOST = new Particle.DustOptions(Color.fromRGB(168, 139, 255), .85f);
    private static final Particle.DustOptions PLAYBACK = new Particle.DustOptions(Color.fromRGB(168, 139, 255), 1.5f);
    private static final int DOTS_PER_UPDATE = 64;
    private static final int VIEW_RANGE_SQUARED = 32 * 32;

    private record Baseline(int strokes, FlightGhost ghost) {}

    private final RoundImpl round;
    private final String label;
    private final String command;
    private final Map<UUID, Baseline> baselines = new HashMap<>();
    private final Map<UUID, Map<Integer, List<List<FlightGhost.Point>>>> paths = new HashMap<>();
    private final Set<UUID> visible = new HashSet<>();
    private final Map<UUID, BukkitTask> playing = new HashMap<>();

    RoundGhosts(RoundImpl round, String label, String command) {
        this.round = round;
        this.label = label;
        this.command = command;
    }

    public void loadBaselines(Map<UUID, StrokeStandings.Entry> scores, GhostLoader loader) {
        for (var team : round.getTeams()) {
            UUID id = team.getOwner();
            var score = scores.get(id);
            if (score == null) continue;
            FlightGhost ghost = null;
            try {
                ghost = loader.load(score);
            } catch (IOException failure) {
                NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                        .log(Level.WARNING, "Could not read " + label + " ghost for " + id, failure);
            }
            setBaseline(id, score.strokes(), ghost);
        }
    }

    private void setBaseline(UUID id, int strokes, FlightGhost ghost) {
        baselines.put(id, new Baseline(strokes, ghost));
        Player player = Bukkit.getPlayer(id);
        if (player != null && ghost != null)
            player.sendMessage(ChatColor.AQUA + "Your " + label + " ghost is ready. Use " + command + " on to show its paths.");
    }

    void record(UUID player, int hole, List<Location> frames) {
        if (frames.size() < 2) return;
        int count = Math.min(frames.size(), FlightGhost.MAX_POINTS_PER_THROW);
        var points = new ArrayList<FlightGhost.Point>(count);
        try {
            for (int i = 0; i < count; i++) {
                Location point = frames.get((int)Math.round(i * (frames.size() - 1.0) / (count - 1)));
                points.add(FlightGhost.Point.at(point.getX(), point.getY(), point.getZ()));
            }
        } catch (IllegalArgumentException outsideCourse) {
            return;
        }
        paths.computeIfAbsent(player, ignored -> new HashMap<>())
                .computeIfAbsent(hole, ignored -> new ArrayList<>()).add(List.copyOf(points));
    }

    public FlightGhost completed(UUID player, ScoreCard card) {
        var played = paths.get(player);
        if (card == null || !card.completedBy(player, round.getCourse().holes()) || played == null) return null;
        var holes = new ArrayList<FlightGhost.Hole>();
        for (int hole = 0; hole < round.getCourse().holes(); hole++) {
            Integer score = card.getHoleScores().get(hole);
            var throwsInHole = played.get(hole);
            if (score == null || throwsInHole == null || throwsInHole.size() > score) return null;
            holes.add(new FlightGhost.Hole(score, throwsInHole));
        }
        return new FlightGhost(holes);
    }

    public void command(Player player, String option) {
        switch (option) {
            case "status" -> sendStatus(player);
            case "on" -> setVisible(player, true);
            case "off" -> setVisible(player, false);
            case "play" -> replay(player);
            case "compare" -> sendComparison(player, true);
            default -> player.sendMessage(ChatColor.YELLOW + "Use " + command + " [on|off|play|compare].");
        }
    }

    public Controls controls(UUID id) {
        return new Controls(label, ghost(id) != null, visible.contains(id), playing.containsKey(id));
    }

    public Integer bestOnHole(UUID id, int hole) {
        var best = ghost(id);
        return best == null ? null : best.holes().get(hole).strokes();
    }

    private void setVisible(Player player, boolean enabled) {
        UUID id = player.getUniqueId();
        if (enabled && ghost(id) == null) {
            sendUnavailable(player);
            return;
        }
        if (enabled) visible.add(id);
        else {
            visible.remove(id);
            stopPlayback(id);
        }
        player.sendMessage(ChatColor.AQUA + label + " ghost paths are " + (enabled ? "on." : "off."));
        if (enabled) draw(player);
    }

    private void sendStatus(Player player) {
        if (ghost(player.getUniqueId()) == null) {
            sendUnavailable(player);
            return;
        }
        player.sendMessage(ChatColor.AQUA + label + " ghost paths are " + (visible.contains(player.getUniqueId()) ? "on." : "off."));
        player.sendMessage(ChatColor.GRAY + "Use " + command + " play to replay this hole or " + command + " compare for the scorecard.");
    }

    public void sendComparison(Player player, boolean explainUnavailable) {
        FlightGhost best = ghost(player.getUniqueId());
        if (best == null) {
            if (explainUnavailable) sendUnavailable(player);
            return;
        }
        ScoreCard card = round.getScoreCardById(player.getUniqueId());
        if (card == null) return;
        player.sendMessage(ChatColor.AQUA + label + " ghost scorecard • Best " + best.totalStrokes() + " strokes");
        for (int hole = 0; hole < best.holes().size(); hole++) player.sendMessage(ChatColor.GRAY + holeLine(card, best, hole));
        int finished = card.getHoleScores().size();
        if (finished > 0) player.sendMessage(ChatColor.AQUA + "Through " + finished
                + (finished == 1 ? " hole: " : " holes: ") + difference(delta(card, best)));
    }

    void announceHole(UUID id, ScoreCard card, int hole) {
        FlightGhost best = ghost(id);
        Player player = Bukkit.getPlayer(id);
        if (best != null && player != null) player.sendMessage(ChatColor.AQUA + label + " ghost • " + holeLine(card, best, hole));
    }

    String target(UUID id, int hole) {
        Integer best = bestOnHole(id, hole);
        return best == null ? "" : " §7• §bBest on hole: " + best;
    }

    void tick() {
        for (UUID id : visible) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) draw(player);
        }
    }

    void remove(UUID player) {
        stopPlayback(player);
        paths.remove(player);
        visible.remove(player);
        baselines.remove(player);
    }

    void clear() {
        playing.values().forEach(BukkitTask::cancel);
        playing.clear();
        paths.clear();
        visible.clear();
        baselines.clear();
    }

    private void draw(Player player) {
        if (!canView(player)) return;
        FlightGhost best = ghost(player.getUniqueId());
        if (best == null) return;
        var shots = best.holes().get(round.getCurrentHole()).throwsInOrder();
        int count = shots.stream().mapToInt(List::size).sum();
        int step = Bounds.ceilDiv(count, DOTS_PER_UPDATE);
        Location viewer = player.getLocation();
        int pointIndex = 0;
        for (var shot : shots) for (var point : shot) {
            if (pointIndex++ % step != 0) continue;
            double dx = point.x() - viewer.getX(), dy = point.y() - viewer.getY(), dz = point.z() - viewer.getZ();
            if (dx * dx + dy * dy + dz * dz <= VIEW_RANGE_SQUARED)
                player.spawnParticle(BukkitCompatibility.DUST, point.x(), point.y(), point.z(), 1, 0, 0, 0, 0, GHOST);
        }
    }

    private void replay(Player player) {
        UUID id = player.getUniqueId();
        FlightGhost best = ghost(id);
        if (best == null) {
            sendUnavailable(player);
            return;
        }
        if (!canView(player)) {
            player.sendMessage(ChatColor.YELLOW + "Replay your ghost from the course after the flyover.");
            return;
        }

        stopPlayback(id);
        int hole = round.getCurrentHole();
        var shots = best.holes().get(hole).throwsInOrder();
        player.sendMessage(ChatColor.AQUA + "Replaying your " + label + " ghost on hole " + (hole + 1) + ".");
        var task = new BukkitRunnable() {
            private int shot;
            private int point;
            private int pause;

            @Override public void run() {
                if (round.isOver() || round.getCurrentHole() != hole || !canView(player) || shot == shots.size()) {
                    stopPlayback(id);
                    return;
                }
                if (pause > 0) {
                    pause--;
                    return;
                }
                var path = shots.get(shot);
                var frame = path.get(point++);
                player.spawnParticle(BukkitCompatibility.DUST, frame.x(), frame.y(), frame.z(), 1, 0, 0, 0, 0, PLAYBACK);
                if (point == path.size()) {
                    point = 0;
                    shot++;
                    pause = 5;
                }
            }
        }.runTaskTimer(NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class), 1, 1);
        playing.put(id, task);
    }

    private boolean canView(Player player) {
        return player.isOnline() && player.getGameMode() == GameMode.ADVENTURE && player.getWorld() == round.world();
    }

    private void stopPlayback(UUID id) {
        var task = playing.remove(id);
        if (task != null) task.cancel();
    }

    private FlightGhost ghost(UUID player) {
        Baseline baseline = baselines.get(player);
        return baseline == null ? null : baseline.ghost();
    }

    private void sendUnavailable(Player player) {
        Baseline best = baselines.get(player.getUniqueId());
        if (best == null) player.sendMessage(ChatColor.YELLOW + "Hole out on every hole to record your first " + label + " ghost.");
        else player.sendMessage(ChatColor.YELLOW + "Your " + label + " best is " + best.strokes()
                + " strokes. Match or beat it to record a ghost.");
    }

    private static String holeLine(ScoreCard card, FlightGhost best, int hole) {
        int target = best.holes().get(hole).strokes();
        Integer played = card.getHoleScores().get(hole);
        return "Hole " + (hole + 1) + ": You " + (played == null ? "—" : played) + " | Best " + target
                + (played == null ? "" : " | " + difference(played - target));
    }

    private static long delta(ScoreCard card, FlightGhost best) {
        return card.getHoleScores().entrySet().stream()
                .mapToLong(entry -> (long) entry.getValue() - best.holes().get(entry.getKey()).strokes()).sum();
    }

    private static String difference(long delta) {
        if (delta == 0) return "Tied";
        long difference = Math.abs(delta);
        return difference + (difference == 1 ? " stroke " : " strokes ") + (delta < 0 ? "ahead" : "behind");
    }
}
