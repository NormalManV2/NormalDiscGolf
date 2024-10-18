package normalmanv2.normalDiscGolf.api;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.round.RoundHandler;

public class API {

    private static final PlayerDataManager playerDataManager = NormalDiscGolf.getPlayerDataManager();
    private static final RoundHandler roundHandler = NormalDiscGolf.getRoundHandler();
    private static API instance;

    private API() {
    }

    public static API getInstance() {
        if (instance == null) {
            instance = new API();
        }
        return instance;
    }

    public static PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public static RoundHandler getRoundHandler() {
        return roundHandler;
    }

}
