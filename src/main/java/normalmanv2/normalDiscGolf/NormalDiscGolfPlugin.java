package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.command.DiscGolfCommand;
import normalmanv2.normalDiscGolf.impl.command.ThrowDiscCommand;
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

    private ThrowMechanicListener throwMechanicListener;

    @Override
    public void onEnable() {
        NDGManager.getInstance().initializeOnlinePlayers();
        this.registerCommands();
        this.registerListeners();
    /*
        try {
            PackedIntegration integration = new PackedIntegration(this);
            integration.savePack();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        */
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
        this.throwMechanicListener = new ThrowMechanicListener();
        Bukkit.getPluginManager().registerEvents(this.throwMechanicListener, this);
    }

    @Override
    public void onDisable() {
        if (this.throwMechanicListener != null) {
            this.throwMechanicListener.cancelAll();
        }

        NDGManager manager = NDGManager.getInstance();
        manager.getPlayerSessionManager().restoreAllOnlinePlayers();
        manager.getRoundHandler().shutdown();
        manager.getPlayerSessionManager().clear();
        manager.getPlayerDataManager().saveAll();
        manager.getPlayerDataManager().clear();
        DiscFlightManager.cleanupAll();
        TaskManager.unregisterAll();
    }
}
