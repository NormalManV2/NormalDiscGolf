package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.Wirable;
import normalmanv2.normalDiscGolf.api.round.manager.RoundStrokeManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundStrokeManager;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultRoundStrokeManager implements DelegateRoundStrokeManager, Wirable {
    private GameRound round;
    private boolean isWired = false;

    private final PlayerDataManager playerDataManager;

    public DefaultRoundStrokeManager() {
        this.playerDataManager = NDGManager.getInstance().getPlayerDataManager();
    }

    @Override
    public RoundStrokeManager getRoundStrokeManager() {
        return this;
    }

    @Override
    public void wire(@NotNull GameRound gameRound, @Nullable Plugin plugin) {
        if (this.isWired) {
            throw new IllegalStateException("This stroke manager is already wired!");
        }

        this.round = gameRound;
        this.isWired = true;
    }

    @Override
    public void unwire() {
        if (!this.isWired) {
            throw new IllegalStateException("This turn manager is not wired!");
        }

        this.round = null;
        this.isWired = false;
    }

    @Override
    public boolean isWired() {
        return this.isWired;
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
