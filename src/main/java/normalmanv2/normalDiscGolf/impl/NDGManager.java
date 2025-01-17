package normalmanv2.normalDiscGolf.impl;

import net.mcbrawls.inject.spigot.InjectSpigot;
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
import normalmanv2.normalDiscGolf.impl.service.InviteService;
import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import normalmanv2.normalDiscGolf.impl.registry.ThrowTechniqueRegistry;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import normalmanv2.normalDiscGolf.impl.resourcepack.PackedIntegration;
import normalmanv2.normalDiscGolf.impl.resourcepack.ResourcePackInjector;
import org.normal.NormalAPI;
import org.normal.impl.RegistryImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class NDGManager {
    private final PlayerDataManager playerDataManager = new PlayerDataManager();
    private final RoundHandler roundHandler;
    private final DiscRegistry discRegistry = new DiscRegistry();
    private final ThrowTechniqueRegistry throwTechniqueRegistry = new ThrowTechniqueRegistry();
    private final InviteService inviteService = new InviteService();
    private final RegistryImpl<String, ObstacleImpl> obstacleRegistry = NormalAPI.getInstance().createRegistry();
    private final FileManager fileManager;
    private final ObstacleManager obstacleManager;
    private final TaskManager taskManager;
    private final GuiManager guiManager = new GuiManager();
    private final RoundQueueManager roundQueueManager;
    private final CourseDivisionRegistry divisionCourseRegistry = new CourseDivisionRegistry();
    private final PackedIntegration packedIntegration;
    private static NDGManager instance;

    private NDGManager() {
        NormalDiscGolf plugin = NormalDiscGolf.getPlugin(NormalDiscGolf.class);
        this.fileManager = new FileManager(plugin);
        this.taskManager = new TaskManager();
        this.obstacleManager = new ObstacleManager(this.fileManager);
        this.roundQueueManager = new RoundQueueManager();
        this.roundHandler = new RoundHandler(plugin);
        try {
            this.packedIntegration = new PackedIntegration(plugin);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.registerDefaultDiscs();
        this.registerDefaultObstacles();
        this.registerDefaultCourseDifficulty();
        this.registerInjectors();
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

    public PackedIntegration getPackedIntegration() {
        return this.packedIntegration;
    }

    private void registerDefaultDiscs() {
        discRegistry.register("TestDriver", new Driver(9, 5, -1, 2, "&6TestDriver", this));
        discRegistry.register("TestDriver1", new Driver(9, 5, -3, 1, "&aTestDriver1", this));
        discRegistry.register("Midrange", new MidRange(5, 6, -1, 1, "&7Midrange", this));
        discRegistry.register("Midrange1", new MidRange(5, 6, -3, 1, "&cMidrange1", this));
        discRegistry.register("Putter", new Putter(2, 4, -1, 1, "&4Putter", this));
        discRegistry.register("Putter1", new Putter(2, 4, 0, 2, "&3Putter1", this));
    }

    private void registerDefaultObstacles() {
        this.obstacleRegistry.register("tree", new Tree(null, "tree", this.obstacleManager));
        this.obstacleRegistry.register("bush", new Bush(null, "bush", this.obstacleManager));
        this.obstacleRegistry.register("rock", new Rock(null, "rock", this.obstacleManager));
        this.obstacleRegistry.register("pin", new Pin(null, "pin", this.obstacleManager));
    }

    private void registerDefaultCourseDifficulty() {
        Map<Division, CourseDifficulty> defaultMapping = Arrays.stream(Division.values())
                .collect(Collectors.toMap(
                        division -> division,
                        Division::getDifficulty
                ));

        this.divisionCourseRegistry.set(defaultMapping);
    }

    private void registerInjectors() {
        InjectSpigot.INSTANCE.registerInjector(new ResourcePackInjector(this.packedIntegration));
    }

}
