package normalmanv2.normalDiscGolf.api;

import normalmanv2.normalDiscGolf.impl.disc.Driver;
import normalmanv2.normalDiscGolf.impl.disc.MidRange;
import normalmanv2.normalDiscGolf.impl.disc.Putter;
import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.round.RoundHandler;
import normalmanv2.normalDiscGolf.impl.registry.ThrowTechniqueRegistry;

public class NDGApi {
    private final PlayerDataManager playerDataManager = new PlayerDataManager();
    private final RoundHandler roundHandler = new RoundHandler();
    private static final DiscRegistry discRegistry = new DiscRegistry();
    private final ThrowTechniqueRegistry throwTechniqueRegistry = new ThrowTechniqueRegistry();
    private static NDGApi instance;

    private NDGApi() {
    }

    public static NDGApi getInstance() {
        if (instance == null) {
            instance = new NDGApi();
            registerDefaultDiscs();
        }
        return instance;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public RoundHandler getRoundHandler() {
        return roundHandler;
    }

    public ThrowTechniqueRegistry getThrowTechniqueRegistry() {
        return throwTechniqueRegistry;
    }

    public DiscRegistry getDiscRegistry() {
        return discRegistry;
    }

    private static void registerDefaultDiscs() {
        discRegistry.registerDisc("TestDriver", new Driver(9, 5, -1, 2, "&6TestDriver"));
        discRegistry.registerDisc("TestDriver1", new Driver(9, 5, -3, 1, "&aTestDriver1"));
        discRegistry.registerDisc("Midrange", new MidRange(5, 6, -1, 1, "&7Midrange"));
        discRegistry.registerDisc("Midrange1", new MidRange(5, 6, -3, 1, "&cMidrange1"));
        discRegistry.registerDisc("Putter", new Putter(2, 4, -1, 1, "&4Putter"));
        discRegistry.registerDisc("Putter1", new Putter(2, 4, 0, 2, "&3Putter1"));
    }
}
