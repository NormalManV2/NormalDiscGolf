package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class WorldTPCommand extends AbstractCommand {

    public WorldTPCommand(String command, String[] aliases, String description, String permission) {
        super(command, aliases, description, permission);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }

        World nextWorld = getNextWorld(player.getWorld());

        player.teleport(new Location(nextWorld, 0.5, 64, 0.5));
    }

    private World getNextWorld(World currentWorld) {
        List<World> worlds = Bukkit.getWorlds();

        int currentIndex = worlds.indexOf(currentWorld);

        int nextIndex = (currentIndex + 1) % worlds.size();

        return worlds.get(nextIndex);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
