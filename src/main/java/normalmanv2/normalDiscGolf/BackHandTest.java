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

public class BackHandTest implements CommandExecutor {

    private final Disc testDisc;
    private final PlayerDataManager dataManager;

    public BackHandTest(Disc testDisc, PlayerDataManager dataManager) {
        this.testDisc = testDisc;
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {

        if (!(commandSender instanceof Player player)) {
            return false;
        }

        PlayerData playerData = dataManager.getDataByPlayer(player.getUniqueId());
        PlayerSkills skills = playerData.getSkills();

        testDisc.handleThrow(player, skills, ThrowTechnique.BACKHAND);

        return true;
    }
}
