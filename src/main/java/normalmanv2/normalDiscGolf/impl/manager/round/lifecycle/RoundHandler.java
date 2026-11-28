package normalmanv2.normalDiscGolf.impl.manager.round.lifecycle;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundResult;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.custom.CustomCourse;
import normalmanv2.normalDiscGolf.impl.competition.DailyCourse;
import normalmanv2.normalDiscGolf.impl.competition.WeeklyCourse;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGridWorldCreator;
import normalmanv2.normalDiscGolf.impl.course.obstacle.generator.ObstacleGenerator;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.BestShotRound;
import normalmanv2.normalDiscGolf.impl.round.WorstShotRound;
import normalmanv2.normalDiscGolf.impl.round.OneDiscRound;
import normalmanv2.normalDiscGolf.impl.round.DiscRouletteRound;
import normalmanv2.normalDiscGolf.impl.round.EliminationRound;
import normalmanv2.normalDiscGolf.impl.round.AceRaceRound;
import normalmanv2.normalDiscGolf.impl.round.ClosestToPinRound;
import normalmanv2.normalDiscGolf.impl.round.ArcadeRound;
import normalmanv2.normalDiscGolf.impl.round.DailyRound;
import normalmanv2.normalDiscGolf.impl.round.WeeklyTournamentRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.MatchPlayRound;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import normalmanv2.normalDiscGolf.impl.util.BukkitCompatibility;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class RoundHandler {

    private static final int OBSTACLE_TILES_PER_TICK = 16;
    private static final long OBSTACLE_BAR_HIDE_DELAY_TICKS = 60L;

    private final Set<GameRound> activeRounds;
    private final Set<GameRound> preparingRounds;
    private final Set<GameRound> roundsRequiringPreparation;
    private final Set<GameRound> preparedRounds;
    private final Set<GameRound> startWhenPrepared = new LinkedHashSet<>();
    private final Map<GameRound, BukkitTask> preparationTasks;
    private final Map<GameRound, BossBar> preparationBars;
    private final NormalDiscGolfPlugin plugin;
    private BukkitTask queueTask;
    private record GeneratedWorld(UUID id, Path directory) {}
    private final Map<String, GeneratedWorld> ownedWorlds = new HashMap<>();

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
            var queue = NDGManager.getInstance().getRoundQueueManager();
            queue.tickPlayerQueue();
            queue.tickRoundQueue();
            prepareNext(queue.getQueuedRounds());
        }, 0, 20);
    }

    public void startRound(GameRound round) {
        if (this.activeRounds.contains(round)) {
            return;
        }

        if (!round.isWired()) {
            round.wire(round, this.plugin);
        }

        if (!this.courseReady(round)) {
            this.startWhenPrepared.add(round);
            this.prepareNext(List.of());
            return;
        }

        this.startPreparedRound(round);
    }

    private void startPreparedRound(GameRound round) {
        if (round.getCourse() instanceof CustomCourse course) {
            List<String> errors = course.validate();
            if (!errors.isEmpty()) {
                this.sendRoundMessage(round, ChatColor.RED + "Custom course could not start. " + errors.get(0));
                discardRound(round);
                return;
            }
        }
        round.setState(RoundState.START);
        this.activeRounds.add(round);
        try {
            RoundResult result = round.start();
            if (result != RoundResult.SUCCESS && result != RoundResult.ALREADY_STARTED) {
                this.activeRounds.remove(round);
                discardRound(round);
            }
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not start round " + round.getId(), exception);
            cancelRound(round);
        }
    }

    public void endRound(GameRound round) {
        if (this.preparingRounds.contains(round) || startWhenPrepared.contains(round)) {
            this.cancelPreparingRound(round, ChatColor.RED + "Round canceled while the course was preparing.");
            return;
        }

        DiscFlightManager.cleanupRound(round.getId());
        round.setState(RoundState.END);
        this.cleanupEndedRounds();
    }

    public void cancelRound(GameRound round) {
        if (this.preparingRounds.contains(round) || startWhenPrepared.contains(round)) {
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

    public GameRound createCustomRound(CustomCourse course, DefaultRoundSettings settings) {
        var round = new FFARound(course, "ndg_custom_" + UUID.randomUUID(), settings);
        round.wire(round, this.plugin);
        return round;
    }

    public GameRound createRound(
            String roundFormat,
            CourseImpl course,
            String courseName,
            DefaultRoundSettings settings,
            boolean generateObstacles) {
        GameRound round = createQueuedRound(roundFormat, course, courseName, settings);
        try { createCourseWorld(course, courseName, settings.division()); }
        catch (RuntimeException failure) { discardRound(round); throw failure; }
        if (!generateObstacles) {
            roundsRequiringPreparation.remove(round);
            preparedRounds.add(round);
        }
        return round;
    }

    public GameRound createQueuedRound(String roundFormat, CourseImpl course, String courseName,
                                       DefaultRoundSettings settings) {

        RoundFormat format = RoundFormat.parse(roundFormat);
        if (format == null) throw new IllegalArgumentException("Unknown round type: " + roundFormat);
        if (format == RoundFormat.DAILY && !(course instanceof DailyCourse))
            throw new IllegalArgumentException("Daily rounds require a date-specific course");
        if (format == RoundFormat.WEEKLY && !(course instanceof WeeklyCourse))
            throw new IllegalArgumentException("Weekly rounds require an event course");
        GameRound round = switch (format) {
            case FFA -> createFFARound(course, courseName, settings);
            case DOUBLES -> createDoublesRound(course, courseName, settings);
            case BEST_SHOT -> new BestShotRound(course, courseName, settings);
            case WORST_SHOT -> new WorstShotRound(course, courseName, settings);
            case ONE_DISC -> new OneDiscRound(course, courseName, settings);
            case DISC_ROULETTE -> new DiscRouletteRound(course, courseName, settings);
            case ELIMINATION -> new EliminationRound(course, courseName, settings);
            case ACE_RACE -> new AceRaceRound(course, courseName, settings);
            case CLOSEST_TO_PIN -> new ClosestToPinRound(course, courseName, settings);
            case ARCADE -> new ArcadeRound(course, courseName, settings, plugin.getConfig().getInt("arcade.charges-per-disc", 1));
            case DAILY -> new DailyRound((DailyCourse)course, courseName, settings);
            case WEEKLY -> new WeeklyTournamentRound((WeeklyCourse)course, courseName, settings);
            case MATCH -> new MatchPlayRound(course, courseName, settings);
        };

        return prepareOnQueue(round);
    }

    public GameRound createQueuedSetupTest(CourseImpl course, String courseName, DefaultRoundSettings settings) {
        return prepareOnQueue(new normalmanv2.normalDiscGolf.impl.round.SetupTestRound(course, courseName, settings));
    }

    private GameRound prepareOnQueue(GameRound round) {
        round.wire(round, this.plugin);
        this.roundsRequiringPreparation.add(round);
        return round;
    }

    private World createCourseWorld(CourseImpl course, String courseName, Division division) {
        Path directory = BukkitCompatibility.generatedWorldDirectory(courseName).toAbsolutePath().normalize();
        Path legacyDirectory = Bukkit.getWorldContainer().toPath().resolve(courseName);
        boolean fresh = courseName.startsWith("ndg_") && Bukkit.getWorld(courseName) == null
                && Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)
                && Files.notExists(legacyDirectory, LinkOption.NOFOLLOW_LINKS);
        World world = Bukkit.createWorld(new CourseGridWorldCreator(courseName, course, division));
        if (world == null) throw new IllegalStateException("World " + courseName + " could not be created");
        if (fresh && world.getWorldFolder().toPath().toAbsolutePath().normalize().equals(directory)) {
            ownedWorlds.put(courseName, new GeneratedWorld(world.getUID(), directory));
            try {
                if (directory.equals(directory.toRealPath())) {
                    Files.writeString(directory.resolve(".ndg-generated"), courseName, StandardOpenOption.CREATE_NEW);
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not mark generated world: " + exception.getMessage());
            }
        }
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        return world;
    }

    public boolean courseReady(GameRound round) { return !roundsRequiringPreparation.contains(round); }

    public boolean isBuildingCourse(GameRound round) { return preparingRounds.contains(round); }

    public void prepareNext(Collection<GameRound> queued) {
        if (!preparingRounds.isEmpty()) return;
        java.util.stream.Stream.concat(startWhenPrepared.stream(), queued.stream())
                .filter(this::needsPreparation).filter(round -> !round.getTeams().isEmpty())
                .findFirst().ifPresent(this::prepareRound);
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

        round.setState(RoundState.PREPARING);
        this.preparingRounds.add(round);
        this.sendRoundMessage(round, ChatColor.YELLOW + "Preparing course. The lobby stays open while the course builds.");
        long started = System.nanoTime();
        try {
            CourseImpl course = (CourseImpl) round.getCourse();
            World world = Bukkit.getWorld(round.getId());
            if (world == null) world = createCourseWorld(course, round.getId(), round.division());
            this.scheduleObstacleGeneration(course, world, round.getId(), round, started);
        } catch (RuntimeException failure) {
            this.failPreparation(round, null, failure);
        }
    }

    private void scheduleObstacleGeneration(
            CourseImpl course,
            World world,
            String courseName,
            GameRound round, long started) {
            BossBar loadingBar = Bukkit.createBossBar("Preparing course: 0%", BarColor.GREEN, BarStyle.SEGMENTED_10);
            loadingBar.setProgress(0.0);
            loadingBar.setVisible(true);
            this.preparationBars.put(round, loadingBar);
            this.addRoundPlayersToBossBar(round, loadingBar);
            this.sendRoundMessage(round, ChatColor.YELLOW + "Preparing course obstacles...");

            BukkitTask task = new ObstacleGenerator(course.grid(), round.division()).generateObstaclesIncrementally(
                    this.plugin,
                    world,
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
                        long millis = (System.nanoTime() - started) / 1_000_000;
                        this.plugin.getLogger().info("Obstacle generation completed for " + courseName + " in " + millis + " ms");
                        this.finishPreparation(round);
                    },
                    exception -> {
                        this.plugin.getLogger().warning("Course preparation failed for " + courseName + ": " + exception.getMessage());
                        this.failPreparation(round, loadingBar, exception);
                    }
            );
            this.preparationTasks.put(round, task);
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

        if (this.startWhenPrepared.remove(round)) {
            this.sendRoundMessage(round, ChatColor.GREEN + "Course ready. Starting round.");
            this.startPreparedRound(round);
        }
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
        this.startWhenPrepared.remove(round);
        this.roundsRequiringPreparation.remove(round);
        this.preparedRounds.remove(round);
        NDGManager.getInstance().getRoundQueueManager().removeRoundFromQueue(round);

        if (message != null) {
            this.sendRoundMessage(round, message);
        }

        round.setState(RoundState.CANCEL);
        NDGManager.getInstance().getPlayerSessionManager().restoreRoundSessions(round);
        round.dispose();
        releaseWorld(round.getId());
    }

    private void addRoundPlayersToBossBar(GameRound round, BossBar bossBar) {
        if (round.isOver()) {
            bossBar.removeAll();
            return;
        }

        for (Player player : bossBar.getPlayers()) {
            if (!round.containsPlayer(player.getUniqueId())) bossBar.removePlayer(player);
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
            this.roundsRequiringPreparation.remove(gameRound);
            this.preparedRounds.remove(gameRound);
            try {
                if (roundState == RoundState.CANCEL) gameRound.cancel();
                else gameRound.end();
            } finally {
                gameRound.dispose();
                releaseWorld(gameRound.getId());
            }
        }
    }

    public boolean isCourseWorld(World world) {
        return ownedWorlds.containsKey(world.getName()) || this.activeRounds.stream().anyMatch(r -> r.getId().equals(world.getName()));
    }

    public void leavePlayer(Player player) {
        plugin.cancelMeter(player.getUniqueId());
        normalmanv2.normalDiscGolf.impl.round.RoundPresentation.cancel(player);
        for (GameRound round : getActiveRounds()) {
            if (round.containsPlayer(player.getUniqueId())) {
                ((normalmanv2.normalDiscGolf.impl.round.RoundImpl)round).play().leave(player.getUniqueId());
                if (!(round instanceof MatchPlayRound match) || player.getUniqueId().equals(match.score().forfeiter()))
                    player.sendMessage("§eYou forfeited the round.");
            }
        }
        for (GameRound round : getPreparingRounds()) {
            if (NDGManager.getInstance().getRoundQueueManager().isRoundQueued(round)) continue;
            for (Team team : round.getTeams()) if (team.contains(player.getUniqueId())) {
                team.removePlayer(player.getUniqueId());
                if (team.getTeamMembers().isEmpty()) round.removeTeam(team);
            }
            if (round instanceof EliminationRound elimination && !elimination.hasOpponents())
                cancelPreparingRound(round, ChatColor.YELLOW + "Elimination needs at least two players.");
            else if (round.getTeams().isEmpty() || round instanceof MatchPlayRound match && !match.hasOpponents()) cancelRound(round);
        }
        NDGManager.getInstance().getPlayerSessionManager().restorePlayer(player);
    }

    /** Used for queues that never started, so cancellation does not require an active lifecycle. */
    public void discardRound(GameRound round) {
        if (this.preparingRounds.contains(round) || this.startWhenPrepared.contains(round)) {
            cancelPreparingRound(round, null);
            return;
        }
        roundsRequiringPreparation.remove(round);
        preparedRounds.remove(round);
        round.setState(RoundState.CANCEL);
        round.dispose();
        releaseWorld(round.getId());
    }

    private void releaseWorld(String name) {
        GeneratedWorld generated = ownedWorlds.get(name);
        if (generated == null) return;
        World world = Bukkit.getWorld(generated.id());
        if (world != null) {
            world.removePluginChunkTickets(plugin);
            World lobby = Bukkit.getWorlds().stream().filter(w -> !ownedWorlds.containsKey(w.getName())).findFirst().orElse(null);
            for (Player player : new ArrayList<>(world.getPlayers())) {
                NDGManager.getInstance().getPlayerSessionManager().restorePlayer(player);
                if (player.getWorld() == world && lobby != null) player.teleport(lobby.getSpawnLocation());
            }
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().warning("Could not unload generated course " + name + "; keeping it for shutdown cleanup.");
                return;
            }
        }
        try {
            Path directory = generated.directory();
            Path marker = directory.resolve(".ndg-generated");
            // Delete only a directory created and marked by this plugin instance.
            if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) && directory.equals(directory.toRealPath())
                    && Files.readString(marker).equals(name)) {
                try (var paths = Files.walk(directory)) {
                    for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                }
            }
            ownedWorlds.remove(name);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not remove generated course " + name + ": " + exception.getMessage());
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds);
    }

    public List<GameRound> getPreparingRounds() {
        var pending = new LinkedHashSet<>(this.preparingRounds);
        pending.addAll(this.startWhenPrepared);
        return new ArrayList<>(pending);
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
        roundsToDispose.addAll(this.roundsRequiringPreparation);
        roundsToDispose.addAll(this.preparedRounds);
        roundsToDispose.addAll(NDGManager.getInstance().getRoundQueueManager().getQueuedRounds());

        for (GameRound round : roundsToDispose) {
            DiscFlightManager.cleanupRound(round.getId());
            round.setState(RoundState.CANCEL);
            NDGManager.getInstance().getPlayerSessionManager().restoreRoundSessions(round);
            round.dispose();
            releaseWorld(round.getId());
        }

        NDGManager.getInstance().getRoundQueueManager().clear();
        this.activeRounds.clear();
        this.preparingRounds.clear();
        this.startWhenPrepared.clear();
        this.roundsRequiringPreparation.clear();
        this.preparedRounds.clear();
        for (String name : new HashSet<>(ownedWorlds.keySet())) releaseWorld(name);
    }
}
