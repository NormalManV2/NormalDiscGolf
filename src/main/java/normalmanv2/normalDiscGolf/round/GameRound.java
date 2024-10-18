package normalmanv2.normalDiscGolf.round;

import normalmanv2.normalDiscGolf.disc.Disc;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;

import java.util.List;
import java.util.UUID;

public interface GameRound {

    void startRound();
    void endRound();
    void cancelRound();
    void addPlayer(UUID player);
    void removePlayer(UUID player);
    void handleStroke(UUID player, ThrowTechnique technique, Disc disc);
    boolean isRoundOver();
    List<UUID> getPlayers();
}
