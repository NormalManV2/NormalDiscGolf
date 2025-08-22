package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.manager.RoundStrokeManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundStrokeManager;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class DefaultRoundStrokeManager implements DelegateRoundStrokeManager {
    private final GameRound round;
    private final PlayerDataManager playerDataManager;

    public DefaultRoundStrokeManager(GameRound round, PlayerDataManager playerDataManager) {
        this.round = round;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public RoundStrokeManager getRoundStrokeManager() {
        return this;
    }

    @Override
    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    @Override
    public boolean handleStroke(ThrowMechanic throwMechanic) {
        Player player = Bukkit.getPlayer(throwMechanic.getPlayerId());
        if (player == null) {
            return false;
        }

        PlayerData playerData = this.getPlayerDataManager().getDataByPlayer(throwMechanic.getPlayerId());
        PlayerSkills skills = playerData.getSkills();


        if (this.round instanceof FFARound) {
            ScoreCard card = this.round.getRoundScoreCardManager().getScoreCardById(player.getUniqueId());
            if (card == null)
                throw new RuntimeException("No score card found for player " + player.getDisplayName() + ". This is a major issue, please report!");
            card.trackStroke();
        }

        DiscImpl disc = (DiscImpl) throwMechanic.getDisc();

        disc.handleThrow(player, skills, throwMechanic.getThrowTechnique(), player.getFacing(), throwMechanic);
        return true;
    }
}
