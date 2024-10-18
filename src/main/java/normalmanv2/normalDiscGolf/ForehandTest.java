package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.disc.Disc;
import normalmanv2.normalDiscGolf.player.PlayerData;
import normalmanv2.normalDiscGolf.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.player.PlayerSkills;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ForehandTest implements CommandExecutor {

    private final Disc disc;
    private final PlayerDataManager playerDataManager;

    public ForehandTest(Disc disc, PlayerDataManager playerDataManager) {
        this.disc = disc;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {

        if (!(commandSender instanceof Player player)) {
            return false;
        }

        PlayerData playerData = playerDataManager.getDataByPlayer(player.getUniqueId());
        PlayerSkills skills = playerData.getSkills();

        disc.handleThrow(player, skills, ThrowTechnique.FOREHAND);

        return true;
    }
}
