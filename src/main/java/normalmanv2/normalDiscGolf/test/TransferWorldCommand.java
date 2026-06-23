package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TransferWorldCommand extends AbstractCommand {

    public TransferWorldCommand(String command, String[] aliases, String description, String permission) {
        super(command, aliases, description, permission);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }

        if (args.length != 1) {
            return;
        }

        String worldName = player.getDisplayName() + "." + player.getUniqueId();

        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(ChatColor.RED + "Invalid world name!");
        }

        player.teleport(new Location(world, 0, 64, 0));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
