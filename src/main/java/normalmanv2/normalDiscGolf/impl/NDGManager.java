package normalmanv2.normalDiscGolf.impl;

import normalmanv2.normalDiscGolf.impl.disc.Driver;
import normalmanv2.normalDiscGolf.impl.disc.MidRange;
import normalmanv2.normalDiscGolf.impl.disc.Putter;
import normalmanv2.normalDiscGolf.impl.invite.InviteService;
import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.round.RoundHandler;
import normalmanv2.normalDiscGolf.impl.registry.ThrowTechniqueRegistry;

public class NDGManager {
    private final PlayerDataManager playerDataManager = new PlayerDataManager();
    private final RoundHandler roundHandler = new RoundHandler();
    private final DiscRegistry discRegistry = new DiscRegistry();
    private final ThrowTechniqueRegistry throwTechniqueRegistry = new ThrowTechniqueRegistry();
    private final InviteService inviteService = new InviteService();
    private static NDGManager instance;

    private NDGManager() {
        registerDefaultDiscs();
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

    private void registerDefaultDiscs() {
        discRegistry.registerDisc("TestDriver", new Driver(9, 5, -1, 2, "&6TestDriver", this));
        discRegistry.registerDisc("TestDriver1", new Driver(9, 5, -3, 1, "&aTestDriver1", this));
        discRegistry.registerDisc("Midrange", new MidRange(5, 6, -1, 1, "&7Midrange", this));
        discRegistry.registerDisc("Midrange1", new MidRange(5, 6, -3, 1, "&cMidrange1", this));
        discRegistry.registerDisc("Putter", new Putter(2, 4, -1, 1, "&4Putter", this));
        discRegistry.registerDisc("Putter1", new Putter(2, 4, 0, 2, "&3Putter1", this));
    }
}
