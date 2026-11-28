package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.command.DiscGolfCommand;
import normalmanv2.normalDiscGolf.impl.command.ThrowDiscCommand;
import normalmanv2.normalDiscGolf.impl.admin.AdminSettings;
import normalmanv2.normalDiscGolf.impl.admin.BackupTasks;
import normalmanv2.normalDiscGolf.impl.admin.DataBackups;
import normalmanv2.normalDiscGolf.impl.admin.DataRestore;
import normalmanv2.normalDiscGolf.impl.admin.SetupWizard;
import normalmanv2.normalDiscGolf.impl.listener.DiscThrowListener;
import normalmanv2.normalDiscGolf.impl.listener.GoalScoreListener;
import normalmanv2.normalDiscGolf.impl.listener.GuiListener;
import normalmanv2.normalDiscGolf.impl.listener.PlayerJoinListener;
import normalmanv2.normalDiscGolf.impl.listener.PlayerRoundQueueListener;
import normalmanv2.normalDiscGolf.impl.listener.ThrowMechanicListener;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import normalmanv2.normalDiscGolf.impl.manager.task.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NormalDiscGolfPlugin extends JavaPlugin {

    private NDGManager manager;
    private AdminSettings adminSettings;
    private SetupWizard setupWizard;
    private DataBackups dataBackups;
    private DataRestore dataRestore;
    private BackupTasks backupTasks;
    public AdminSettings adminSettings() { return adminSettings; }
    public SetupWizard setupWizard() { return setupWizard; }
    public DataBackups dataBackups() { return dataBackups; }
    public DataRestore dataRestore() { return dataRestore; }
    public BackupTasks backupTasks() { return backupTasks; }
    private ThrowMechanicListener throwMechanicListener;
    public boolean released(org.bukkit.event.player.PlayerInteractEvent event) { return throwMechanicListener != null && throwMechanicListener.released(event); }
    public boolean cancelMeter(java.util.UUID id) {
        return throwMechanicListener != null && throwMechanicListener.cancelMechanic(id);
    }
    public void trackPracticeMeter(normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl mechanic) {
        throwMechanicListener.track(mechanic);
    }

    @Override
    public void onEnable() {
        if (Bukkit.getBukkitVersion().matches("1\\.19(?:\\.[1-3])?-.*")) {
            getLogger().severe("NormalDiscGolf requires Minecraft 1.19.4 or newer for display entities.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        var data = getDataFolder().toPath();
        dataBackups = new DataBackups(data, getDescription().getVersion());
        dataRestore = new DataRestore(data, dataBackups);
        try {
            dataRestore.applyOnStartup(getLogger());
        } catch (java.io.IOException failure) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "NDG could not resolve the restore state. Data stores will stay closed. See restore metadata and backups in the plugin directory.", failure);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        boolean firstStart = !new java.io.File(getDataFolder(), "config.yml").exists();
        saveDefaultConfig();
        reloadConfig();
        adminSettings = new AdminSettings(this);
        setupWizard = new SetupWizard(this, adminSettings);
        if (firstStart) getLogger().info("Operators can open /ndg admin wizard to set up the first round.");
        try {
            var check = adminSettings.schema().check(adminSettings.read());
            for (String issue : check.errors()) getLogger().warning(issue);
            for (String warning : check.warnings()) getLogger().info(warning);
        } catch (java.io.IOException failure) {
            getLogger().warning("Cannot check the settings file. " + failure.getMessage());
        }
        manager = NDGManager.getInstance();
        manager.initializeOnlinePlayers();
        backupTasks = new BackupTasks(this, manager.getPlayerDataManager());
        this.registerCommands();
        this.registerListeners();
        Bukkit.getScheduler().runTask(this, NDGManager.getInstance().getClubhouse()::start);
        Bukkit.getScheduler().runTaskTimer(this, NDGManager.getInstance().getWeeklyTournaments()::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, NDGManager.getInstance().getRankedSeasons()::tick, 20L, 20L);
        try {
            NDGManager.getInstance().getPacked();
            for (var player : Bukkit.getOnlinePlayers()) NDGManager.getInstance().getPacked().send(player);
        } catch (java.io.IOException | RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Resource pack initialization failed", exception);
        }
    }

    private void registerCommands() {
        new DiscGolfCommand("ndg", new String[]{"discGolf", "normalDiscGolf", "dg"}, "NormalDiscGolf gameplay commands", null);
        new ThrowDiscCommand("throwdisc", new String[]{"tdisc"}, "Throw a disc in the active round", null, this);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new GoalScoreListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerRoundQueueListener(), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(NDGManager.getInstance().getGuiManager()), this);
        Bukkit.getPluginManager().registerEvents(new DiscThrowListener(), this);
        Bukkit.getPluginManager().registerEvents(new normalmanv2.normalDiscGolf.impl.listener.RoundPlayerListener(), this);
        Bukkit.getPluginManager().registerEvents(NDGManager.getInstance().getClubhouse(), this);
        this.throwMechanicListener = new ThrowMechanicListener();
        Bukkit.getPluginManager().registerEvents(this.throwMechanicListener, this);
    }

    @Override
    public void onDisable() {
        if (this.throwMechanicListener != null) {
            this.throwMechanicListener.cancelAll();
        }

        if (manager == null) return;
        normalmanv2.normalDiscGolf.impl.round.RoundPresentation.cancelAll();
        manager.getPlayerSessionManager().restoreAllOnlinePlayers();
        manager.getRoundHandler().shutdown();
        manager.getRankedSeasons().shutdown();
        manager.getPlayerSessionManager().clear();
        manager.getPlayerDataManager().saveAll();
        manager.getPlayerDataManager().clear();
        DiscFlightManager.cleanupAll();
        TaskManager.unregisterAll();
        manager.closeResourcePack();
    }
}
