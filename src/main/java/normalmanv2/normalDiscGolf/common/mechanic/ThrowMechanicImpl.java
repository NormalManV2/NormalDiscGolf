package normalmanv2.normalDiscGolf.common.mechanic;

import com.google.common.util.concurrent.AtomicDouble;
import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.event.ThrowMechanicEvent;
import normalmanv2.normalDiscGolf.impl.manager.task.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class ThrowMechanicImpl implements ThrowMechanic {

    private final UUID playerId;
    private BossBar bossBar;
    private final Plugin plugin;
    private AtomicDouble power;
    private final DiscImpl disc;
    private final GameRound round;
    private final String throwTechnique;

    private boolean increasing = true;
    private final double step = 0.05;

    public ThrowMechanicImpl(UUID playerId, Plugin plugin, DiscImpl disc, GameRound round, String throwTechnique) {
        this.playerId = playerId;
        this.plugin = plugin;
        this.disc = disc;
        this.round = round;
        this.throwTechnique = throwTechnique;

        this.use();
    }

    public String getThrowTechnique() {
        return this.throwTechnique;
    }

    public DiscImpl getDisc() {
        return this.disc;
    }

    public GameRound getRound() {
        return this.round;
    }

    @Override
    public BlockFace getDirection() {
        Player player = Bukkit.getPlayer(playerId);

        if (player == null) return null;

        return player.getFacing();
    }

    @Override
    public Location getThrowLocation() {
        Player player = Bukkit.getPlayer(playerId);

        if (player == null) return null;

        return player.getLocation();
    }

    @Override
    public UUID getPlayerId() {
        return this.playerId;
    }

    public AtomicDouble getPower() {
        return this.power;
    }

    // Intentional power algo
    protected void use() {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        this.bossBar = Bukkit.createBossBar("Throw Power", BarColor.BLUE, BarStyle.SOLID);
        this.bossBar.setProgress(0.0);
        this.bossBar.setVisible(true);
        this.bossBar.addPlayer(player);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double currentProgress = this.bossBar.getProgress();
            double newProgress;

            if (this.increasing) {
                newProgress = currentProgress + this.step;
                if (newProgress >= 1.0) {
                    newProgress = 1.0;
                    this.increasing = false;
                }
            } else {
                newProgress = currentProgress - this.step;
                if (newProgress <= 0.0) {
                    newProgress = 0.0;
                    this.increasing = true;
                }
            }

            this.bossBar.setProgress(newProgress);
            this.power = new AtomicDouble(newProgress);

        }, 0, 8);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&Click to lock in your power!"));

        TaskManager.registerTask(task, playerId);
        Bukkit.getPluginManager().callEvent(new ThrowMechanicEvent(ThrowState.POWER, player, this));
    }

    public BossBar getBossBar() {
        return this.bossBar;
    }
}
