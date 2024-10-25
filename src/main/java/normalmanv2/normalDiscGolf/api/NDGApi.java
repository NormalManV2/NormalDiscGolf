package normalmanv2.normalDiscGolf.api;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.round.RoundHandler;
import normalmanv2.normalDiscGolf.impl.technique.ThrowTechniqueRegistry;

public class NDGApi {
    private final PlayerDataManager playerDataManager;
    private final RoundHandler roundHandler;
    private final ThrowTechniqueRegistry throwTechniqueRegistry;
    private static NDGApi instance;

    private NDGApi(PlayerDataManager playerDataManager, RoundHandler roundHandler, ThrowTechniqueRegistry throwTechniqueRegistry) {
        this.playerDataManager = playerDataManager;
        this.roundHandler = roundHandler;
        this.throwTechniqueRegistry = throwTechniqueRegistry;
    }

    public static NDGApi getInstance() {
        if (instance == null) {
            instance = new NDGApi(NormalDiscGolf.getPlayerDataManager(), NormalDiscGolf.getRoundHandler(), NormalDiscGolf.getThrowTechniqueRegistry());
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
}
