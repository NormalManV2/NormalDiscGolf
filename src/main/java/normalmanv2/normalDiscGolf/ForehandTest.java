package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.impl.disc.Disc;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ForehandTest implements CommandExecutor {

    private final DiscRegistry discRegistry;
    private final PlayerDataManager playerDataManager;

    public ForehandTest(PlayerDataManager playerDataManager, DiscRegistry registry) {
        this.playerDataManager = playerDataManager;
        this.discRegistry = registry;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {

        if (!(commandSender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            return false;
        }
        try {
            Disc disc = discRegistry.getDiscByString(args[0]);

            PlayerData playerData = playerDataManager.getDataByPlayer(player.getUniqueId());
            PlayerSkills skills = playerData.getSkills();

            disc.handleThrow(player, skills, "forehand", player.getFacing());
        } catch (CloneNotSupportedException exception) {
            exception.printStackTrace();
            return false;
        }
        return true;
    }
}
