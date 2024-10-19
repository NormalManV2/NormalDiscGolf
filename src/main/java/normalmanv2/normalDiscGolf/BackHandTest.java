package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.impl.disc.Disc;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackHandTest implements CommandExecutor {

    private final PlayerDataManager dataManager;
    private final DiscRegistry discRegistry;

    public BackHandTest(PlayerDataManager dataManager, DiscRegistry discRegistry) {
        this.dataManager = dataManager;
        this.discRegistry = discRegistry;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {

        if (!(commandSender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            return false;
        }

        Disc disc = discRegistry.getDiscByString(args[0]);

        PlayerData playerData = dataManager.getDataByPlayer(player.getUniqueId());
        PlayerSkills skills = playerData.getSkills();

        disc.handleThrow(player, skills, "backhand");

        return true;
    }
}
