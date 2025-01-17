package normalmanv2.normalDiscGolf.impl;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.api.division.Division;
import normalmanv2.normalDiscGolf.impl.course.CourseCreator;
import normalmanv2.normalDiscGolf.impl.course.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.obstacle.Bush;
import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.manager.file.FileManager;
import normalmanv2.normalDiscGolf.impl.manager.gui.GuiManager;
import normalmanv2.normalDiscGolf.impl.manager.course.ObstacleManager;
import normalmanv2.normalDiscGolf.impl.course.obstacle.Pin;
import normalmanv2.normalDiscGolf.impl.course.obstacle.Rock;
import normalmanv2.normalDiscGolf.impl.course.obstacle.Tree;
import normalmanv2.normalDiscGolf.impl.disc.Driver;
import normalmanv2.normalDiscGolf.impl.disc.MidRange;
import normalmanv2.normalDiscGolf.impl.disc.Putter;
import normalmanv2.normalDiscGolf.impl.manager.task.TaskManager;
import normalmanv2.normalDiscGolf.impl.registry.CourseDivisionRegistry;
import normalmanv2.normalDiscGolf.impl.registry.ObstacleRegistry;
import normalmanv2.normalDiscGolf.impl.service.InviteService;
import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import normalmanv2.normalDiscGolf.impl.registry.ThrowTechniqueRegistry;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import org.normal.NormalAPI;
import org.normal.impl.RegistryImpl;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class NDGManager {
    private final PlayerDataManager playerDataManager;
    private final RoundHandler roundHandler;
    private final DiscRegistry discRegistry;
    private final ThrowTechniqueRegistry throwTechniqueRegistry;
    private final InviteService inviteService;
    private final ObstacleRegistry obstacleRegistry;
    private final FileManager fileManager;
    private final ObstacleManager obstacleManager;
    private final TaskManager taskManager;
    private final GuiManager guiManager;
    private final RoundQueueManager roundQueueManager;
    private final CourseDivisionRegistry divisionCourseRegistry;
    private static NDGManager instance;

    private NDGManager() {
        NormalDiscGolf plugin = NormalDiscGolf.getPlugin(NormalDiscGolf.class);
        this.roundHandler = new RoundHandler(plugin);
        this.fileManager = new FileManager(plugin);
        this.obstacleManager = new ObstacleManager(this.fileManager);
        this.taskManager = new TaskManager();
        this.playerDataManager = new PlayerDataManager();
        this.roundQueueManager = new RoundQueueManager(this.roundHandler, this.playerDataManager);
        this.discRegistry = new DiscRegistry();
        this.throwTechniqueRegistry = new ThrowTechniqueRegistry();
        this.inviteService = new InviteService();
        this.obstacleRegistry = new ObstacleRegistry();
        this.guiManager = new GuiManager();
        this.divisionCourseRegistry = new CourseDivisionRegistry();

        this.registerDefaults();
        this.initRoundHandlerTask(this.roundQueueManager);
    }

    private void initRoundHandlerTask(RoundQueueManager roundQueueManager) {
        this.roundHandler.startQueueTask(roundQueueManager);
    }

    private void registerDefaults() {
        this.registerDefaultDiscs();
        this.registerDefaultObstacles();
        this.registerDefaultCourseDifficulty();
    }

    public static NDGManager getInstance() {
        if (instance == null) {
            instance = new NDGManager();
        }
        return instance;
    }

    public CourseCreator courseCreator() {
        return new CourseCreator();
    }

    public CourseDivisionRegistry getDivisionCourseRegistry() {
        return this.divisionCourseRegistry;
    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    public RoundHandler getRoundHandler() {
        return this.roundHandler;
    }

    public ThrowTechniqueRegistry getThrowTechniqueRegistry() {
        return this.throwTechniqueRegistry;
    }

    public DiscRegistry getDiscRegistry() {
        return this.discRegistry;
    }

    public InviteService getInviteService() {
        return this.inviteService;
    }

    public ObstacleRegistry getObstacleRegistry() {
        return this.obstacleRegistry;
    }

    public FileManager getFileManager() {
        return this.fileManager;
    }

    public ObstacleManager getObstacleManager() {
        return this.obstacleManager;
    }

    public TaskManager getTaskManager() {
        return this.taskManager;
    }

    public GuiManager getGuiManager() {
        return this.guiManager;
    }

    public RoundQueueManager getRoundQueueManager() {
        return this.roundQueueManager;
    }

    private void registerDefaultDiscs() {
        discRegistry.register("TestDriver", new Driver(9, 5, -1, 2, "&6TestDriver", this));
        discRegistry.register("TestDriver1", new Driver(9, 5, -3, 1, "&aTestDriver1", this));
        discRegistry.register("Midrange", new MidRange(5, 6, -1, 1, "&7Midrange", this));
        discRegistry.register("Midrange1", new MidRange(5, 6, -3, 1, "&cMidrange1", this));
        discRegistry.register("Putter", new Putter(2, 4, -1, 1, "&4Putter", this));
        discRegistry.register("Putter1", new Putter(2, 4, 0, 2, "&3Putter1", this));
        discRegistry.freeze();
    }

    private void registerDefaultObstacles() {
        this.obstacleRegistry.register("tree", new Tree(null, "tree", this.obstacleManager));
        this.obstacleRegistry.register("bush", new Bush(null, "bush", this.obstacleManager));
        this.obstacleRegistry.register("rock", new Rock(null, "rock", this.obstacleManager));
        this.obstacleRegistry.register("pin", new Pin(null, "pin", this.obstacleManager));
        this.obstacleRegistry.freeze();
    }

    private void registerDefaultCourseDifficulty() {
        Map<Division, CourseDifficulty> defaultMapping = Arrays.stream(Division.values())
                .collect(Collectors.toMap(
                        division -> division,
                        Division::getDifficulty
                ));

        this.divisionCourseRegistry.set(defaultMapping);
        this.divisionCourseRegistry.freeze();
    }

}
