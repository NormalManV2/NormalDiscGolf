package normalmanv2.normalDiscGolf.api.round.delegate.manager;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.round.manager.RoundStrokeManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;

public interface DelegateRoundStrokeManager extends RoundStrokeManager {
    RoundStrokeManager getRoundStrokeManager();

    @Override
    default PlayerDataManager getPlayerDataManager() {
        return getRoundStrokeManager().getPlayerDataManager();
    }

    @Override
    default boolean handleStroke(ThrowMechanic throwMechanic) {
        return getRoundStrokeManager().handleStroke(throwMechanic);
    }

}
