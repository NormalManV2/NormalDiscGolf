package normalmanv2.normalDiscGolf.impl.manager.round.lifecycle;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundResult;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGridWorldCreator;
import normalmanv2.normalDiscGolf.impl.course.obstacle.generator.ObstacleGenerator;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Supplier;

public class RoundHandler {

    private static final int OBSTACLE_TILES_PER_TICK = 16;
    private static final long OBSTACLE_START_DELAY_TICKS = 40L;
    private static final long OBSTACLE_BAR_HIDE_DELAY_TICKS = 60L;

    private final Set<GameRound> activeRounds;
    private final NormalDiscGolfPlugin plugin;

    public RoundHandler(NormalDiscGolfPlugin normalDiscGolfPlugin) {
        this.activeRounds = new HashSet<>();
        this.plugin = normalDiscGolfPlugin;
        this.startQueueTask();
    }

    public void startQueueTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            NDGManager.getInstance().getRoundQueueManager().tickPlayerQueue();
            NDGManager.getInstance().getRoundQueueManager().tickRoundQueue();
        }, 0, 20);
    }

    public void startRound(GameRound round) {
        if (!round.isWired()) {
            round.wire(round, this.plugin);
        }

        round.setState(RoundState.START);
        this.activeRounds.add(round);
        RoundResult result = round.start();
        if (result != RoundResult.SUCCESS && result != RoundResult.ALREADY_STARTED) {
            this.activeRounds.remove(round);
        }
    }

    public void endRound(GameRound round) {
        round.setState(RoundState.END);
        this.cleanupEndedRounds();
    }

    public void cancelRound(GameRound round) {
        round.setState(RoundState.CANCEL);
        this.cleanupEndedRounds();
    }

    public GameRound createRound(
            String roundFormat,
            CourseImpl course,
            String courseName,
            DefaultRoundSettings settings) {
        return createRound(roundFormat, course, courseName, settings, true);
    }

    public GameRound createRound(
            String roundFormat,
            CourseImpl course,
            String courseName,
            DefaultRoundSettings settings,
            boolean generateObstacles) {

        World world = Bukkit.createWorld(new CourseGridWorldCreator(courseName, course, settings.division()));

        if (world == null) throw new RuntimeException("World is null!");

        Supplier<GameRound> roundSupplier = switch (roundFormat.toLowerCase()) {
            case "ffa" -> () -> createFFARound(course, courseName, settings);
            case "doubles" -> () -> createDoublesRound(course, courseName, settings);
            default -> throw new IllegalArgumentException("Unknown round type: " + roundFormat);
        };

        GameRound round = roundSupplier.get();
        round.wire(round, this.plugin);

        if (generateObstacles) {
            scheduleObstacleGeneration(course, world, settings, courseName, round);
        }

        return round;
    }

    private void scheduleObstacleGeneration(
            CourseImpl course,
            World world,
            DefaultRoundSettings settings,
            String courseName,
            GameRound round) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            World activeWorld = Bukkit.getWorld(world.getName());
            if (activeWorld == null) {
                this.plugin.getLogger().warning("Skipping obstacle generation for unloaded world " + courseName);
                return;
            }

            BossBar loadingBar = Bukkit.createBossBar("Preparing course: 0%", BarColor.GREEN, BarStyle.SEGMENTED_10);
            loadingBar.setProgress(0.0);
            loadingBar.setVisible(true);
            this.addRoundPlayersToBossBar(round, loadingBar);
            this.sendRoundMessage(round, ChatColor.YELLOW + "Preparing course obstacles...");

            new ObstacleGenerator(course.grid(), settings.division()).generateObstaclesIncrementally(
                    this.plugin,
                    activeWorld,
                    OBSTACLE_TILES_PER_TICK,
                    (completedTiles, totalTiles) -> {
                        this.addRoundPlayersToBossBar(round, loadingBar);
                        double progress = totalTiles <= 0 ? 1.0 : Math.min(1.0, completedTiles / (double) totalTiles);
                        loadingBar.setProgress(progress);
                        loadingBar.setTitle("Preparing course: " + Math.round(progress * 100) + "%");
                    },
                    () -> {
                        this.addRoundPlayersToBossBar(round, loadingBar);
                        loadingBar.setProgress(1.0);
                        loadingBar.setTitle("Course ready");
                        this.sendRoundMessage(round, ChatColor.GREEN + "Course obstacles are ready.");
                        Bukkit.getScheduler().runTaskLater(this.plugin, loadingBar::removeAll, OBSTACLE_BAR_HIDE_DELAY_TICKS);
                        this.plugin.getLogger().info("Obstacle generation completed for " + courseName);
                    }
            );
        }, OBSTACLE_START_DELAY_TICKS);
    }

    private void addRoundPlayersToBossBar(GameRound round, BossBar bossBar) {
        if (round.isOver()) {
            bossBar.removeAll();
            return;
        }

        for (Team team : round.getTeams()) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && !bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
        }
    }

    private void sendRoundMessage(GameRound round, String message) {
        if (round.isOver()) {
            return;
        }

        for (Team team : round.getTeams()) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(message);
                }
            }
        }
    }

    private GameRound createFFARound(
            CourseImpl course,
            String id,
            DefaultRoundSettings settings) {
        return new FFARound(
                course,
                id,
                settings);
    }

    private GameRound createDoublesRound(
            CourseImpl course,
            String id,
            DefaultRoundSettings settings) {
        return new DoublesRound(
                course,
                id,
                settings);
    }

    private void cleanupEndedRounds() {
        final Iterator<GameRound> activeRoundsIterator = activeRounds.iterator();
        while (activeRoundsIterator.hasNext()) {
            final GameRound gameRound = activeRoundsIterator.next();
            final RoundState roundState = gameRound.getState();
            if (roundState != RoundState.END && roundState != RoundState.CANCEL) {
                continue;
            }

            activeRoundsIterator.remove();
            gameRound.end();
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds);
    }
}
