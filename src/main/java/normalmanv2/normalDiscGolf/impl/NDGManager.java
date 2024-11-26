package normalmanv2.normalDiscGolf.impl;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.impl.course.obstacle.Bush;
import normalmanv2.normalDiscGolf.impl.manager.FileManager;
import normalmanv2.normalDiscGolf.impl.manager.ObstacleManager;
import normalmanv2.normalDiscGolf.impl.course.obstacle.Pin;
import normalmanv2.normalDiscGolf.impl.course.obstacle.Rock;
import normalmanv2.normalDiscGolf.impl.course.obstacle.Tree;
import normalmanv2.normalDiscGolf.impl.disc.Driver;
import normalmanv2.normalDiscGolf.impl.disc.MidRange;
import normalmanv2.normalDiscGolf.impl.disc.Putter;
import normalmanv2.normalDiscGolf.impl.manager.TaskManager;
import normalmanv2.normalDiscGolf.impl.registry.ObstacleRegistry;
import normalmanv2.normalDiscGolf.impl.service.InviteService;
import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.manager.RoundHandler;
import normalmanv2.normalDiscGolf.impl.registry.ThrowTechniqueRegistry;
import normalmanv2.normalDiscGolf.impl.manager.QueueManager;
import normalmanv2.normalDiscGolf.impl.service.QueueService;

public class NDGManager {
    private final PlayerDataManager playerDataManager = new PlayerDataManager();
    private final RoundHandler roundHandler;
    private final QueueService queueService = new QueueService();
    private final QueueManager queueManager;
    private final DiscRegistry discRegistry = new DiscRegistry();
    private final ThrowTechniqueRegistry throwTechniqueRegistry = new ThrowTechniqueRegistry();
    private final InviteService inviteService = new InviteService();
    private final ObstacleRegistry obstacleRegistry = new ObstacleRegistry();
    private final FileManager fileManager;
    private final ObstacleManager obstacleManager;
    private final TaskManager taskManager;
    private static NDGManager instance;

    private NDGManager() {
        NormalDiscGolf plugin = NormalDiscGolf.getPlugin(NormalDiscGolf.class);
        this.fileManager = new FileManager(plugin);
        this.taskManager = new TaskManager(plugin);
        this.queueManager = new QueueManager(this.queueService);
        this.obstacleManager = new ObstacleManager(this.fileManager);
        this.roundHandler = new RoundHandler(this.queueManager, plugin);
        this.registerDefaultDiscs();
        this.registerDefaultObstacles();
    }

    public static NDGManager getInstance() {
        if (instance == null) {
            instance = new NDGManager();
        }
        return instance;
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

    public QueueManager getQueueManager() {
        return this.queueManager;
    }

    public QueueService getQueueService() {
        return this.queueService;
    }

    public TaskManager getTaskManager() {
        return this.taskManager;
    }

    private void registerDefaultDiscs() {
        discRegistry.registerDisc("TestDriver", new Driver(9, 5, -1, 2, "&6TestDriver", this));
        discRegistry.registerDisc("TestDriver1", new Driver(9, 5, -3, 1, "&aTestDriver1", this));
        discRegistry.registerDisc("Midrange", new MidRange(5, 6, -1, 1, "&7Midrange", this));
        discRegistry.registerDisc("Midrange1", new MidRange(5, 6, -3, 1, "&cMidrange1", this));
        discRegistry.registerDisc("Putter", new Putter(2, 4, -1, 1, "&4Putter", this));
        discRegistry.registerDisc("Putter1", new Putter(2, 4, 0, 2, "&3Putter1", this));
    }

    private void registerDefaultObstacles() {
        this.obstacleRegistry.registerObstacle("tree", new Tree(null, "tree", this.obstacleManager));
        this.obstacleRegistry.registerObstacle("bush", new Bush(null, "bush", this.obstacleManager));
        this.obstacleRegistry.registerObstacle("rock", new Rock(null, "rock", this.obstacleManager));
        this.obstacleRegistry.registerObstacle("pin", new Pin(null, "pin", this.obstacleManager));
    }

}
