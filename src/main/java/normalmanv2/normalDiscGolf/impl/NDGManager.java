package normalmanv2.normalDiscGolf.impl;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.course.CourseCreator;
import normalmanv2.normalDiscGolf.impl.course.difficulty.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Bush;
import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.manager.file.FileManager;
import normalmanv2.normalDiscGolf.impl.manager.gui.GuiManager;
import normalmanv2.normalDiscGolf.impl.manager.course.ObstacleManager;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Pin;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Rock;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Tree;
import normalmanv2.normalDiscGolf.impl.manager.party.PartyManager;
import normalmanv2.normalDiscGolf.impl.manager.task.TaskManager;
import normalmanv2.normalDiscGolf.impl.registry.CourseDivisionRegistry;
import normalmanv2.normalDiscGolf.impl.registry.ObstacleRegistry;
import normalmanv2.normalDiscGolf.impl.resourcepack.PackedIntegration;
import normalmanv2.normalDiscGolf.impl.service.InviteService;
import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import normalmanv2.normalDiscGolf.impl.registry.ThrowTechniqueRegistry;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;

import org.normal.impl.registry.RegistryImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class NDGManager {
    private final PlayerDataManager playerDataManager = new PlayerDataManager();
    private final RoundHandler roundHandler;
    private final RegistryImpl<String, DiscImpl> discRegistry = new DiscRegistry();
    private final ThrowTechniqueRegistry throwTechniqueRegistry = new ThrowTechniqueRegistry();
    private final InviteService inviteService = new InviteService();
    private final RegistryImpl<String, ObstacleImpl> obstacleRegistry = new ObstacleRegistry();
    private final FileManager fileManager;
    private final ObstacleManager obstacleManager;
    private final TaskManager taskManager;
    private final GuiManager guiManager = new GuiManager();
    private final RoundQueueManager roundQueueManager;
    private final RegistryImpl<Division, CourseDifficulty> divisionCourseRegistry = new CourseDivisionRegistry();
    private final PartyManager partyManager = new PartyManager();
    private PackedIntegration packed;
    private final NormalDiscGolfPlugin plugin;
    private static NDGManager instance;

    private NDGManager() {
        this.plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        this.fileManager = new FileManager(plugin);
        this.taskManager = new TaskManager();
        this.obstacleManager = new ObstacleManager(this.fileManager);
        this.roundHandler = new RoundHandler(plugin);
        this.roundQueueManager = new RoundQueueManager(this.roundHandler, this.playerDataManager);

        //this.registerDefaultDiscs();
        this.registerDefaultObstacles();
        this.registerDefaultCourseDifficulty();
    }

    public static NDGManager getInstance() {
        if (instance == null) {
            instance = new NDGManager();
        }
        return instance;
    }

    public PackedIntegration getPacked() throws IOException {
        if (packed == null) {
            packed = new PackedIntegration(this.plugin);
        }

        return packed;
    }

    public PartyManager getPartyManager() {
        return this.partyManager;
    }

    public CourseCreator courseCreator() {
        return new CourseCreator();
    }

    public RegistryImpl<Division, CourseDifficulty> getDivisionCourseRegistry() {
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

    public RegistryImpl<String, DiscImpl> getDiscRegistry() {
        return this.discRegistry;
    }

    public InviteService getInviteService() {
        return this.inviteService;
    }

    public RegistryImpl<String, ObstacleImpl> getObstacleRegistry() {
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

    private void registerDefaultObstacles() {
        this.obstacleRegistry.register("tree", new Tree(null, "tree"));
        this.obstacleRegistry.register("bush", new Bush(null, "bush"));
        this.obstacleRegistry.register("rock", new Rock(null, "rock"));
        this.obstacleRegistry.register("pin", new Pin(null, "pin"));
    }

    private void registerDefaultCourseDifficulty() {
        Map<Division, CourseDifficulty> defaultMapping = Arrays.stream(Division.values())
                .collect(Collectors.toMap(
                        division -> division,
                        Division::getDifficulty
                ));

        this.divisionCourseRegistry.set(defaultMapping);
    }
}
