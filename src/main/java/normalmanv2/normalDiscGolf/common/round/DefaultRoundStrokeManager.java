package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.delegate.Wirable;
import normalmanv2.normalDiscGolf.api.round.manager.RoundStrokeManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundStrokeManager;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.listener.RoundPlayerListener;
import normalmanv2.normalDiscGolf.impl.round.ArcadeRound;
import normalmanv2.normalDiscGolf.impl.round.CompetitionRound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultRoundStrokeManager implements DelegateRoundStrokeManager, Wirable {
    private GameRound round;
    private boolean isWired = false;

    public DefaultRoundStrokeManager() {}

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
        return NDGManager.getInstance().getPlayerDataManager();
    }

    @Override
    public boolean handleStroke(ThrowMechanic throwMechanic) {
        Player player = Bukkit.getPlayer(throwMechanic.getPlayerId());
        if (player == null) {
            return false;
        }

        PlayerData playerData = this.getPlayerDataManager().getDataByPlayer(throwMechanic.getPlayerId());
        if (playerData == null) {
            return false;
        }

        ScoreCard card = this.round.getRoundScoreCardManager().getScoreCardById(player.getUniqueId());
        if (card == null) {
            throw new RuntimeException("No score card found for player " + player.getDisplayName() + ". This is a major issue, please report!");
        }
        var play = ((normalmanv2.normalDiscGolf.impl.round.RoundImpl)this.round).play();
        PlayerSkills skills = throwMechanic instanceof ThrowMechanicImpl mechanic
                ? mechanic.effectiveSkills() : play.effectiveSkills(playerData.getSkills());
        DiscImpl disc = (DiscImpl) throwMechanic.getDisc();
        var special = throwMechanic instanceof ThrowMechanicImpl mechanic ? mechanic.arcadeDisc() : null;
        ArcadeRound arcade = round instanceof ArcadeRound mode ? mode : null;
        if (special != null) {
            if (arcade == null || !special.model().displayName().equals(disc.getName()) || special.model().type() != disc.getType())
                return false;
            if (arcade.remaining(player.getUniqueId(), special) == 0) {
                player.sendMessage(org.bukkit.ChatColor.RED + disc.getName() + " has no charges left this round.");
                return false;
            }
        }
        if ((play.usesOneDisc() || play.usesRoulette() || round instanceof CompetitionRound)
                && play.throwableDiscs(player.getUniqueId()).stream().noneMatch(model -> model.displayName().equalsIgnoreCase(disc.getName()))) {
            player.sendMessage(org.bukkit.ChatColor.RED + play.discRestriction(player.getUniqueId()));
            return false;
        }
        if (!play.beginShot(player)) return false;
        if (special != null && !arcade.spend(player.getUniqueId(), special)) {
            play.failedShot();
            return false;
        }
        boolean countAtRelease = !play.usesPairedAttempts();
        if (countAtRelease) card.trackStroke();

        try {
            disc.handleThrow(player, skills, throwMechanic.getThrowTechnique(), player.getFacing(), throwMechanic);
        } catch (RuntimeException exception) {
            if (countAtRelease) card.undoStroke();
            if (special != null) arcade.refund(player.getUniqueId(), special);
            play.failedShot();
            throw exception;
        }
        if (special != null) {
            RoundPlayerListener.refreshArcadeDisc(player, arcade, special);
            player.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + disc.getName() + " charges remaining: "
                    + arcade.remaining(player.getUniqueId(), special));
        }
        return true;
    }
}
