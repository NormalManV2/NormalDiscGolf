package normalmanv2.normalDiscGolf.api.round.lifecycle;

import normalmanv2.normalDiscGolf.impl.round.RoundState;

public interface RoundLifecycle {

    RoundResult start();

    RoundResult cancel();

    RoundResult end();

    boolean isOver();

    RoundState getState();

    void setState(RoundState state);
}
