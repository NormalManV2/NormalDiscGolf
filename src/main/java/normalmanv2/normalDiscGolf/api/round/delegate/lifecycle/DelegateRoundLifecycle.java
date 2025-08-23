package normalmanv2.normalDiscGolf.api.round.delegate.lifecycle;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.Wirable;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundResult;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundLifecycle;
import normalmanv2.normalDiscGolf.impl.round.RoundState;

public interface DelegateRoundLifecycle extends RoundLifecycle, Wirable {
    RoundLifecycle getRoundLifecycle();

    @Override
    default RoundResult start() {
        return this.getRoundLifecycle().start();
    }

    @Override
    default RoundResult end() {
        return this.getRoundLifecycle().end();
    }

    @Override
    default RoundResult cancel() {
        return this.getRoundLifecycle().cancel();
    }

    @Override
    default boolean isOver() {
        return this.getRoundLifecycle().isOver();
    }


    @Override
    default RoundState getState() {
        return this.getRoundLifecycle().getState();
    }

    @Override
    default void setState(RoundState state) {
        this.getRoundLifecycle().setState(state);
    }

    static RoundLifecycle createDefault() {
        return new DefaultRoundLifecycle();
    }
}
