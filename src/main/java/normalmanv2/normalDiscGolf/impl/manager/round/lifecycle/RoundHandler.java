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
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.Supplier;

public class RoundHandler {

    private static final int OBSTACLE_TILES_PER_TICK = 16;
    private static final long OBSTACLE_START_DELAY_TICKS = 40L;
    private static final long OBSTACLE_BAR_HIDE_DELAY_TICKS = 60L;

    private final Set<GameRound> activeRounds;
    private final Set<GameRound> preparingRounds;
    private final Set<GameRound> roundsRequiringPreparation;
    private final Set<GameRound> preparedRounds;
    private final Map<GameRound, BukkitTask> preparationTasks;
    private final Map<GameRound, BossBar> preparationBars;
    private final NormalDiscGolfPlugin plugin;
    private BukkitTask queueTask;

    public RoundHandler(NormalDiscGolfPlugin normalDiscGolfPlugin) {
        this.activeRounds = new HashSet<>();
        this.preparingRounds = new HashSet<>();
        this.roundsRequiringPreparation = new HashSet<>();
        this.preparedRounds = new HashSet<>();
        this.preparationTasks = new HashMap<>();
        this.preparationBars = new HashMap<>();
        this.plugin = normalDiscGolfPlugin;
        this.startQueueTask();
    }

    public void startQueueTask() {
        if (this.queueTask != null && !this.queueTask.isCancelled()) {
            return;
        }

        this.queueTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            NDGManager.getInstance().getRoundQueueManager().tickPlayerQueue();
            NDGManager.getInstance().getRoundQueueManager().tickRoundQueue();
        }, 0, 20);
    }

    public void startRound(GameRound round) {
        if (this.activeRounds.contains(round)) {
            return;
        }

        if (!round.isWired()) {
            round.wire(round, this.plugin);
        }

        if (this.preparingRounds.contains(round)) {
            return;
        }

        if (this.needsPreparation(round)) {
            this.prepareRound(round);
            return;
        }

        this.startPreparedRound(round);
    }

    private void startPreparedRound(GameRound round) {
        round.setState(RoundState.START);
        this.activeRounds.add(round);
        RoundResult result = round.start();
        if (result != RoundResult.SUCCESS && result != RoundResult.ALREADY_STARTED) {
            this.activeRounds.remove(round);
        }
    }

    public void endRound(GameRound round) {
        if (this.preparingRounds.contains(round)) {
            this.cancelPreparingRound(round, ChatColor.RED + "Round canceled while the course was preparing.");
            return;
        }

        DiscFlightManager.cleanupRound(round.getId());
        round.setState(RoundState.END);
        this.cleanupEndedRounds();
    }

    public void cancelRound(GameRound round) {
        if (this.preparingRounds.contains(round)) {
            this.cancelPreparingRound(round, ChatColor.RED + "Round canceled while the course was preparing.");
            return;
        }

        DiscFlightManager.cleanupRound(round.getId());
        this.roundsRequiringPreparation.remove(round);
        this.preparedRounds.remove(round);
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
            this.roundsRequiringPreparation.add(round);
        } else {
            this.preparedRounds.add(round);
        }

        return round;
    }

    private boolean needsPreparation(GameRound round) {
        return this.roundsRequiringPreparation.contains(round)
                && !this.preparedRounds.contains(round)
                && !this.preparingRounds.contains(round);
    }

    private void prepareRound(GameRound round) {
        if (this.preparingRounds.contains(round)) {
            return;
        }

        World world = Bukkit.getWorld(round.getId());
        if (world == null) {
            this.failPreparation(round, null, new RuntimeException("World " + round.getId() + " does not exist"));
            return;
        }

        round.setState(RoundState.PREPARING);
        this.preparingRounds.add(round);
        this.sendRoundMessage(round, ChatColor.YELLOW + "Preparing course. The round will start when the pins, tees, and obstacles are ready.");

        this.scheduleObstacleGeneration((CourseImpl) round.getCourse(), world, round.getId(), round);
    }

    private void scheduleObstacleGeneration(
            CourseImpl course,
            World world,
            String courseName,
            GameRound round) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!this.preparingRounds.contains(round)) {
                return;
            }

            World activeWorld = Bukkit.getWorld(world.getName());
            if (activeWorld == null) {
                this.failPreparation(round, null, new RuntimeException("World " + courseName + " unloaded before obstacle generation"));
                return;
            }

            BossBar loadingBar = Bukkit.createBossBar("Preparing course: 0%", BarColor.GREEN, BarStyle.SEGMENTED_10);
            loadingBar.setProgress(0.0);
            loadingBar.setVisible(true);
            this.preparationBars.put(round, loadingBar);
            this.addRoundPlayersToBossBar(round, loadingBar);
            this.sendRoundMessage(round, ChatColor.YELLOW + "Preparing course obstacles...");

            BukkitTask task = new ObstacleGenerator(course.grid(), round.division()).generateObstaclesIncrementally(
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
                        this.finishPreparation(round);
                    },
                    exception -> {
                        this.plugin.getLogger().warning("Course preparation failed for " + courseName + ": " + exception.getMessage());
                        this.failPreparation(round, loadingBar, exception);
                    }
            );
            this.preparationTasks.put(round, task);
        }, OBSTACLE_START_DELAY_TICKS);
    }

    private void finishPreparation(GameRound round) {
        this.preparationTasks.remove(round);
        this.preparationBars.remove(round);
        this.preparingRounds.remove(round);
        this.roundsRequiringPreparation.remove(round);
        this.preparedRounds.add(round);

        if (round.getState() == RoundState.CANCEL) {
            round.dispose();
            return;
        }

        this.sendRoundMessage(round, ChatColor.GREEN + "Course ready. Starting round.");
        this.startPreparedRound(round);
    }

    private void failPreparation(GameRound round, BossBar loadingBar, RuntimeException exception) {
        BossBar bossBar = loadingBar != null ? loadingBar : this.preparationBars.remove(round);
        if (bossBar != null) {
            bossBar.setColor(BarColor.RED);
            bossBar.setTitle("Course preparation failed");
            Bukkit.getScheduler().runTaskLater(this.plugin, bossBar::removeAll, OBSTACLE_BAR_HIDE_DELAY_TICKS);
        }

        this.sendRoundMessage(round, ChatColor.RED + "Course preparation failed. The round was canceled.");
        this.plugin.getLogger().warning("Round " + round.getId() + " canceled during preparation: " + exception.getMessage());
        this.cancelPreparingRound(round, null);
    }

    private void cancelPreparingRound(GameRound round, String message) {
        BukkitTask task = this.preparationTasks.remove(round);
        if (task != null) {
            task.cancel();
        }

        BossBar bossBar = this.preparationBars.remove(round);
        if (bossBar != null) {
            bossBar.removeAll();
        }

        this.preparingRounds.remove(round);
        this.roundsRequiringPreparation.remove(round);
        this.preparedRounds.remove(round);

        if (message != null) {
            this.sendRoundMessage(round, message);
        }

        round.setState(RoundState.CANCEL);
        NDGManager.getInstance().getPlayerSessionManager().restoreRoundSessions(round);
        round.dispose();
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
            DiscFlightManager.cleanupRound(gameRound.getId());
            if (roundState == RoundState.CANCEL) {
                gameRound.cancel();
                continue;
            }

            gameRound.end();
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds);
    }

    public List<GameRound> getPreparingRounds() {
        return new ArrayList<>(this.preparingRounds);
    }

    public void shutdown() {
        if (this.queueTask != null) {
            this.queueTask.cancel();
            this.queueTask = null;
        }

        for (BukkitTask task : this.preparationTasks.values()) {
            task.cancel();
        }
        this.preparationTasks.clear();

        for (BossBar bossBar : this.preparationBars.values()) {
            bossBar.removeAll();
        }
        this.preparationBars.clear();

        Set<GameRound> roundsToDispose = new HashSet<>();
        roundsToDispose.addAll(this.activeRounds);
        roundsToDispose.addAll(this.preparingRounds);

        for (GameRound round : roundsToDispose) {
            DiscFlightManager.cleanupRound(round.getId());
            round.setState(RoundState.CANCEL);
            NDGManager.getInstance().getPlayerSessionManager().restoreRoundSessions(round);
            round.dispose();
        }

        this.activeRounds.clear();
        this.preparingRounds.clear();
        this.roundsRequiringPreparation.clear();
        this.preparedRounds.clear();
    }
}
