package normalmanv2.normalDiscGolf.api.round.manager;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;

public interface RoundStrokeManager {

    PlayerDataManager getPlayerDataManager();
    boolean handleStroke(ThrowMechanic throwMechanic);

}
