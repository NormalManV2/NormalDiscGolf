package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.disc.Driver;
import org.bukkit.plugin.java.JavaPlugin;

public final class NormalDiscGolf extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic

        Driver testDriver = new Driver(9, 5, -1, 2);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
