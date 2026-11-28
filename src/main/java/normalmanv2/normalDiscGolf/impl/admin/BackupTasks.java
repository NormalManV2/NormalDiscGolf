package normalmanv2.normalDiscGolf.impl.admin;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/** Serializes backup operations and runs ZIP work off the server thread. */
public final class BackupTasks {
    public static final int DEFAULT_INTERVAL = 86_400;
    public static final int DEFAULT_KEEP = 7;
    private final NormalDiscGolfPlugin plugin;
    private final PlayerDataManager players;
    private Policy policy;
    private OffsiteBackups offsite;
    private Instant next;
    private Instant lastSuccess;
    private boolean running;

    public record Policy(boolean enabled, int intervalSeconds, int keep) {
        static Policy read(ConfigurationSection config) {
            var interval = config.get("backups.interval-seconds");
            var keep = config.get("backups.keep");
            if (!(interval instanceof Integer seconds) || seconds < 1 || !(keep instanceof Integer count) || count < 1)
                return new Policy(false, DEFAULT_INTERVAL, DEFAULT_KEEP);
            return new Policy(config.getBoolean("backups.enabled", false), seconds, count);
        }
    }

    @FunctionalInterface
    public interface Work<T> { T run() throws IOException; }

    public BackupTasks(NormalDiscGolfPlugin plugin, PlayerDataManager players) {
        this.plugin = plugin;
        this.players = players;
        try {
            lastSuccess = plugin.dataBackups().lastAutomatic();
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not read the scheduled backup history", failure);
        }
        configure();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean running() { return running; }
    public Policy policy() { return policy; }
    public OffsiteBackups offsite() { return offsite; }
    public Instant next() { return next; }

    public void configure() {
        try {
            offsite = new OffsiteBackups(plugin.dataBackups(), plugin.getConfig(), plugin.getDataFolder().toPath());
        } catch (IllegalArgumentException invalid) {
            offsite = null;
            plugin.getLogger().warning("Off-server backups are disabled. " + invalid.getMessage());
        }
        var updated = Policy.read(plugin.getConfig());
        if (!updated.enabled()) next = null;
        else if (next == null || updated.intervalSeconds() != policy.intervalSeconds()) {
            Instant now = Instant.now();
            Instant due = (lastSuccess == null ? now : lastSuccess).plusSeconds(updated.intervalSeconds());
            next = due.isBefore(now) ? now : due;
        }
        policy = updated;
    }

    private void tick() {
        if (running || next == null || Instant.now().isBefore(next)) return;
        int keep = policy.keep();
        var destination = offsite;
        submit(true, () -> {
            var backups = plugin.dataBackups();
            var created = backups.createAutomatic();
            plugin.getLogger().info("Automatic backup created: " + created.filename());
            if (created.flaggedCourses() > 0) plugin.getLogger().warning("Automatic backup saved "
                    + created.flaggedCourses() + " course recipes for review. Use /ndg admin upgrade check.");
            if (destination != null && destination.enabled()) {
                try {
                    destination.upload(created.filename());
                    plugin.getLogger().info("Off-server backup verified: " + created.filename());
                } catch (IOException failure) {
                    plugin.getLogger().warning("Off-server upload failed for " + created.filename()
                            + ". The local ZIP was kept and retention skipped. " + failure.getMessage());
                    return created;
                }
            }
            try {
                int removed = backups.pruneAutomatic(created.filename(), keep, plugin.dataRestore().pendingArchive());
                if (removed > 0) plugin.getLogger().info("Removed " + removed + " older automatic backup(s).");
            } catch (IOException failure) {
                plugin.getLogger().log(Level.WARNING, "Automatic backup retained. Could not finish retention cleanup.", failure);
            }
            return created;
        }, (created, failure) -> {
            Instant now = Instant.now();
            if (failure == null) {
                lastSuccess = now;
                next = now.plusSeconds(policy.intervalSeconds());
            } else {
                plugin.getLogger().log(Level.WARNING, "Automatic backup failed. NDG will retry.", failure);
                next = now.plusSeconds(Math.min(60, Math.max(5, policy.intervalSeconds())));
            }
        });
    }

    public <T> void submit(boolean flushProfiles, Work<T> task, BiConsumer<T, Exception> done) {
        if (running) throw new IllegalStateException("A backup operation is already running.");
        if (flushProfiles && !players.saveAll()) {
            done.accept(null, new IOException("A player profile could not be saved. Check the server log before backing up."));
            return;
        }
        running = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T value;
            Exception failure;
            try {
                value = task.run();
                failure = null;
            } catch (IOException | RuntimeException error) {
                value = null;
                failure = error;
            }
            T result = value;
            Exception error = failure;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                running = false;
                done.accept(result, error);
            });
        });
    }
}
