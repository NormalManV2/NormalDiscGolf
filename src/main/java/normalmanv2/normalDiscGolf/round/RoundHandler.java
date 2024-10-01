package normalmanv2.normalDiscGolf.round;

import normalmanv2.normalDiscGolf.disc.Disc;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;

import java.util.UUID;

public class RoundHandler {
//
    private GameRound round;

    public void startRound(GameRound round) {
        if (round == null){
            return;
        }
        this.round = round;
        round.startRound();
    }

    public void endRound() {
        if (this.round == null){
            return;
        }
        this.round.endRound();
        this.round = null;
    }

    public void handlePlayerThrow(UUID player, ThrowTechnique technique, Disc disc) {
        if (this.round == null){
            return;
        }
        this.round.handleThrow(player, technique, disc);
    }

    public void addPlayerToRound(UUID player) {
        if (this.round == null){
            return;
        }
        this.round.addPlayer(player);
    }

    public void removePlayerFromRound(UUID player) {
        if (this.round == null){
            return;
        }
        this.round.removePlayer(player);
    }

    public GameRound getRound() {
        return this.round;
    }

}
