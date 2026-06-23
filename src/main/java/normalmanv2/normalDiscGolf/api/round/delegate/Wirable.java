package normalmanv2.normalDiscGolf.api.round.delegate;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import org.bukkit.plugin.Plugin;

public interface Wirable {

    void wire(GameRound round, Plugin plugin);
    void unwire();
    boolean isWired();

}
