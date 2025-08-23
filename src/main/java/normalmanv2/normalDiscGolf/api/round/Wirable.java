package normalmanv2.normalDiscGolf.api.round;

import org.bukkit.plugin.Plugin;

public interface Wirable {

    void wire(GameRound round, Plugin plugin);
    void unwire();
    boolean isWired();

}
