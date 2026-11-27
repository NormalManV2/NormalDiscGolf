package normalmanv2.normalDiscGolf.common.mechanic;

import com.google.common.util.concurrent.AtomicDouble;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightEngine;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
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
    private final AtomicDouble power = new AtomicDouble(.15);
    private final DiscImpl disc;
    private final ArcadeDisc arcadeDisc;
    private final GameRound round;
    private final String throwTechnique;
    private final ShotSetup setup;
    private final ShotSetup.Stance stance;
    private final PlayerSkills skills;
    private final PlayerSkills.PracticeContext practice;
    private final boolean practiceThrow;
    private BukkitTask task;
    private boolean disposed;

    private boolean increasing = true;
    private boolean timing;
    private double marker;
    private double spreadMultiplier = 1;
    private int elapsed;

    public ThrowMechanicImpl(UUID playerId, Plugin plugin, DiscImpl disc, GameRound round, String throwTechnique) {
        this(playerId, plugin, disc, round, throwTechnique, null);
    }

    public ThrowMechanicImpl(UUID playerId, Plugin plugin, DiscImpl disc, GameRound round, String throwTechnique, ArcadeDisc arcadeDisc) {
        this(playerId, plugin, disc, round, throwTechnique, arcadeDisc, false);
    }

    public static void forPractice(UUID playerId, NormalDiscGolfPlugin plugin, DiscImpl disc, String technique) {
        new ThrowMechanicImpl(playerId, plugin, disc, null, technique, null, true);
    }

    private ThrowMechanicImpl(UUID playerId, Plugin plugin, DiscImpl disc, GameRound round, String throwTechnique,
                              ArcadeDisc arcadeDisc, boolean practiceThrow) {
        this.playerId = playerId;
        this.plugin = plugin;
        this.disc = disc;
        this.arcadeDisc = arcadeDisc;
        this.round = round;
        this.throwTechnique = throwTechnique;
        this.practiceThrow = practiceThrow;
        RoundImpl liveRound = round instanceof RoundImpl impl ? impl : null;
        this.setup = practiceThrow ? new ShotSetup(ShotSetup.Angle.FLAT,
                disc.getType() == DiscType.PUTTER ? ShotSetup.Stance.PUTT : ShotSetup.Stance.NORMAL, true, true)
                : liveRound == null ? ShotSetup.DEFAULT : liveRound.play().setup(playerId);
        this.stance = setup.stance() == ShotSetup.Stance.PUTT
                && !practiceThrow && (liveRound == null || !setup.usesPutting(disc.getType() == DiscType.PUTTER, liveRound.play().distanceToBasket(playerId)))
                ? ShotSetup.Stance.NORMAL : setup.stance();
        var personal = NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(playerId).getSkills();
        this.skills = liveRound == null ? personal : liveRound.play().effectiveSkills(personal);
        this.practice = liveRound == null ? PlayerSkills.PracticeContext.OPEN_LIE : liveRound.play().practiceContext(playerId);

        this.use();
    }

    public String getThrowTechnique() {
        return this.throwTechnique;
    }

    public DiscImpl getDisc() {
        return this.disc;
    }

    public ArcadeDisc arcadeDisc() { return arcadeDisc; }

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

    public ShotSetup.Angle releaseAngle() {
        return round instanceof RoundImpl liveRound ? liveRound.play().setup(playerId).angle() : setup.angle();
    }
    public ShotSetup.Stance stance() { return stance; }
    public boolean isPutting() { return stance == ShotSetup.Stance.PUTT; }
    public boolean isPractice() { return practiceThrow; }
    public boolean isDisposed() { return disposed; }
    public double spreadMultiplier() { return spreadMultiplier; }
    public PlayerSkills effectiveSkills() { return skills; }
    public boolean recoveryLie() { return practice.recovery(); }

    /** Returns true when the player has finished the release sequence. */
    public boolean click(Player player) {
        if (timing) {
            double error = skills.timingError(marker, stance, practice.pressure());
            spreadMultiplier = .12 + error * 3.38;
            player.sendMessage(ChatColor.YELLOW + (error == 0 ? "Clean release"
                    : marker < .5 ? "Early release" : "Late release"));
            return true;
        }
        player.sendMessage(ChatColor.GRAY + "Power locked: " + Math.round(power.get() * 100) + "%");
        if (!setup.timing()) return true;
        timing = true;
        updateTimingBar();
        return false;
    }

    private void updateTimingBar() {
        bossBar.setProgress(marker);
        bossBar.setColor(skills.timingError(marker, stance, practice.pressure()) == 0
                ? BarColor.GREEN : BarColor.YELLOW);
        bossBar.setTitle("Release Timing: " + Math.round(marker * 100) + "%  |  Click in the green");
    }

    public void dispose() {
        if (this.disposed) {
            return;
        }

        this.disposed = true;
        TaskManager.unregisterTask(this.task);
        this.task = null;

        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar.setVisible(false);
            this.bossBar = null;
        }
    }

    // Intentional power algo
    protected void use() {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        boolean putting = isPutting();

        if (putting) power.set(.10);
        this.bossBar = Bukkit.createBossBar(powerTitle(), BarColor.BLUE, BarStyle.SEGMENTED_10);
        this.bossBar.setProgress(this.power.get());
        this.bossBar.setVisible(true);
        this.bossBar.addPlayer(player);

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (this.disposed || this.bossBar == null) {
                return;
            }

            if (round instanceof RoundImpl liveRound && liveRound.play().validate(player) != null) {
                dispose();
                return;
            }
            if (practiceThrow && !NDGManager.getInstance().getClubhouse().atTee(player)) {
                dispose();
                return;
            }
            if (timing) {
                marker = marker >= .99 ? 0 : marker + .1;
                updateTimingBar();
            } else if (elapsed % (putting ? 4 : 8) == 0) {
                double step = putting ? .025 : .05;
                double minimum = putting ? .10 : .15;
                double newPower = Math.clamp(power.get() + (increasing ? step : -step), minimum, 1);
                if (newPower >= 1) increasing = false;
                if (newPower <= minimum) increasing = true;
                power.set(newPower);
                bossBar.setProgress(newPower);
                bossBar.setTitle(powerTitle());
            }
            if (setup.preview() && elapsed % 6 == 0) {
                if (practiceThrow) NDGManager.getInstance().getClubhouse().preview(player, this);
                else if (round instanceof RoundImpl liveRound) DiscFlightEngine.preview(player, skills, this, liveRound);
            }
            elapsed += 2;
        }, 0, 2);

        if (setup.stance() == ShotSetup.Stance.PUTT && !putting)
            player.sendMessage(ChatColor.GRAY + "Putting stance needs a putter within 15 blocks. Using full throw.");
        String hint = switch (stance) {
            case ROLLER -> "Aim along open ground. Grass rolls farther than sand, and the release angle steers the roll.";
            case FLEX -> "Aim through open air. The disc bends out early and fades back late.";
            case TOMAHAWK -> "Aim over the obstacle. The tomahawk climbs high, pans, and drops steeply.";
            case THUMBER -> "Aim over the obstacle. The thumber climbs steeper and pans opposite the tomahawk.";
            default -> null;
        };
        if (hint != null) player.sendMessage(ChatColor.GRAY + hint);
        player.sendMessage(ChatColor.YELLOW + (setup.timing() ? "Click to lock power, then click when the timing bar is green."
                : "Aim your throw, then click once to release."));
        player.sendMessage(ChatColor.GRAY + (practiceThrow ? "Cancel with /ndg shot cancel."
                : "Use your drop key or /ndg shot cancel to stop aiming."));

        TaskManager.registerTask(this.task, playerId);

        if (practiceThrow) {
            ((NormalDiscGolfPlugin)plugin).trackPracticeMeter(this);
            return;
        }

        ThrowMechanicEvent event = new ThrowMechanicEvent(ThrowState.POWER, player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.dispose();
        }
    }

    private String powerTitle() {
        return stance.label() + " Power: " + Math.round(power.get() * 100) + "%";
    }

    public BossBar getBossBar() {
        return this.bossBar;
    }
}
